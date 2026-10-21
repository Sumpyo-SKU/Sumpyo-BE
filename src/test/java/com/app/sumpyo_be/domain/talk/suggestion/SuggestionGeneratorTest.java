package com.app.sumpyo_be.domain.talk.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.global.ai.LlmClient;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SuggestionGeneratorTest {

  private static final RuleBasedSuggestionGenerator RULE = new RuleBasedSuggestionGenerator();

  private static SuggestionContext ctx(String content, String room, boolean inClass, Integer co2) {
    return new SuggestionContext("북악관", 6, content, room, inClass, inClass ? "데이터크롤링" : null,
        inClass ? LocalTime.of(14, 45) : null, co2);
  }

  @Nested
  class RuleBased {

    @Test
    void 최대_3개이고_위치는_항상_들어간다() {
      List<String> s = RULE.generate(ctx("617호 창문 에어컨", "617", true, 1050));
      assertThat(s).containsExactly(
          "617호 지금 수업 중이에요 (데이터크롤링, 14:45까지)", "창문 확인 완료 했어요", "지금 북악관 6층에 있어요");
    }

    @Test
    void 강의실이_없으면_CO2_도_쓰지_않는다() {
      assertThat(RULE.generate(ctx("확인 부탁", null, false, 1050))).containsExactly("지금 북악관 6층에 있어요");
    }

    @Test
    void CO2_는_천_단위_쉼표() {
      assertThat(RULE.generate(ctx("확인", "617", false, 1050))).contains("617호 지금 CO2 1,050ppm이에요");
    }
  }

  @Nested
  class Llm {

    private final LlmClient client = mock(LlmClient.class);
    private final LlmSuggestionGenerator llm =
        new LlmSuggestionGenerator(client, RULE, JsonMapper.builder().build(), Duration.ofMillis(300));
    private final SuggestionContext ctx = ctx("지금 수업 중인가요?", "617", true, 1050);

    @Test
    void JSON_문자열_배열이면_그대로_최대_3개_코드블록도_허용() {
      when(client.complete(anyString())).thenReturn("```json\n[\"네 수업 중이에요\", \"14:45에 끝나요\", \" \", \"창문 열었어요\", \"하나 더\"]\n```");
      assertThat(llm.generate(ctx)).containsExactly("네 수업 중이에요", "14:45에 끝나요", "창문 열었어요");
    }

    @Test
    void 호출이_실패하면_규칙_기반() {
      when(client.complete(anyString())).thenThrow(new IllegalStateException("401"));
      assertThat(llm.generate(ctx)).isEqualTo(RULE.generate(ctx));
    }

    @Test
    void JSON_문자열_배열이_아니면_규칙_기반() {
      for (String bad : List.of("그냥 문장", "[1, 2, 3]", "{\"a\": \"b\"}", "[\"ok\", 3]", "[]", "[ 깨진 json")) {
        when(client.complete(anyString())).thenReturn(bad);
        assertThat(llm.generate(ctx)).as(bad).isEqualTo(RULE.generate(ctx));
      }
    }

    @Test
    void 타임아웃이면_규칙_기반() {
      when(client.complete(anyString())).thenAnswer(inv -> {
        Thread.sleep(2_000);
        return "[\"늦은 답\"]";
      });
      long start = System.nanoTime();
      assertThat(llm.generate(ctx)).isEqualTo(RULE.generate(ctx));
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void 프롬프트에_본문_시간표_센서값이_들어간다() {
      String prompt = LlmSuggestionGenerator.prompt(ctx);
      assertThat(prompt).contains("지금 수업 중인가요?", "617호", "데이터크롤링 (14:45까지)", "1050ppm", "JSON");
    }
  }
}
