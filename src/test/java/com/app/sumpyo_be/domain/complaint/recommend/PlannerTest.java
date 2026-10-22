package com.app.sumpyo_be.domain.complaint.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

/** 서버 숫자 계산 · LLM 응답 검증 · 규칙 기반 대안 */
class PlannerTest {

  private static final Thresholds T = Thresholds.DEFAULT;   // CO2 1000, 20~26°C, 40~60%

  private static PlanStep open(int windows) {
    return new PlanStep(StepType.WINDOW_OPEN, null, windows, null, null);
  }

  private static PlanStep ac(int temp) {
    return new PlanStep(StepType.AC_ON, null, null, temp, null);
  }

  private static PlanContext ctx(ComplaintCategory category, Integer co2, Integer temp, Integer humidity,
      Double outTemp, Integer outHumidity, boolean rain, boolean windowOpen, PlanContext.Previous previous) {
    return new PlanContext(List.of(category), null, co2, temp, humidity, T, outTemp, outHumidity, null, rain,
        windowOpen, null, null, previous);
  }

  @Nested
  class Calculator {

    @Test
    void 필요_분은_5분_단위_올림_최대_30분_창문_수에_비례() {
      assertThat(PlanCalculator.windowMinutes(1250, 2)).isEqualTo(30);   // 26.0분
      assertThat(PlanCalculator.windowMinutes(1000, 2)).isEqualTo(15);   // 14.1분
      assertThat(PlanCalculator.windowMinutes(1250, 4)).isEqualTo(15);   // 13.0분
      assertThat(PlanCalculator.windowMinutes(2000, 2)).isEqualTo(30);   // 47.5분 → 상한
      assertThat(PlanCalculator.windowMinutes(700, 2)).isEqualTo(5);
      assertThat(PlanCalculator.windowMinutes(null, 2)).isEqualTo(15);
    }

    @Test
    void 예상_CO2_는_지수_감소_30분으로_목표에_못_미치면_부풀리지_않는다() {
      assertThat(PlanCalculator.co2After(1250, 2, 30)).isEqualTo(757);
      assertThat(PlanCalculator.co2After(1000, 2, 15)).isEqualTo(790);
      assertThat(PlanCalculator.co2After(2000, 2, 30)).isEqualTo(1062);   // 800 이 아니라 30분 뒤 값
      assertThat(PlanCalculator.co2After(null, 2, 30)).isNull();
    }

    @Test
    void 라벨과_분을_채우고_창문이_없으면_예상_CO2_없음() {
      List<PlanStep> steps = PlanCalculator.complete(List.of(open(2), ac(23)), 1250);
      assertThat(steps).extracting(PlanStep::label).containsExactly("창문 2곳 열기", "에어컨 켜기");
      assertThat(steps.getFirst().minutes()).isEqualTo(30);
      assertThat(PlanCalculator.expectedCo2After(steps, 1250)).isEqualTo(757);
      assertThat(PlanCalculator.expectedCo2After(PlanCalculator.complete(List.of(ac(23)), 1250), 1250)).isNull();
    }

    @Test
    void 요약에_서버_숫자를_넣고_LLM_숫자는_지운다() {
      List<PlanStep> steps = PlanCalculator.complete(List.of(open(2), ac(23)), 1250);
      assertThat(PlanCalculator.summary("창문 2곳을 열고 에어컨을 틀어주세요", steps))
          .isEqualTo("창문 2곳을 30분 열고 에어컨을 틀어주세요");
      assertThat(PlanCalculator.summary("창문 3곳을 10분 동안 열고 에어컨을 틀어주세요", steps))
          .isEqualTo("창문 2곳을 30분 열고 에어컨을 틀어주세요");
      assertThat(PlanCalculator.summary("환기해주세요", steps)).isEqualTo("창문 2곳을 30분 열고 에어컨을 틀어주세요");
      assertThat(PlanCalculator.summary(null, PlanCalculator.complete(List.of(open(2)), 1250)))
          .isEqualTo("창문 2곳을 30분 열어 환기해주세요");
      assertThat(PlanCalculator.stripNumbers("CO2를 약 800ppm까지 낮추려면 20분 정도 열어야 해요"))
          .isEqualTo("CO2를 낮추려면 열어야 해요");
    }
  }

  @Nested
  class LlmParse {

    @SuppressWarnings("unchecked")
    private final LlmPlanner planner = new LlmPlanner(Mockito.mock(ObjectProvider.class), JsonMapper.builder().build());

    @Test
    void 코드블록_JSON_정상_기본값_채움() {
      assertThat(planner.parse("```json\n{\"steps\":[{\"type\":\"WINDOW_OPEN\"},{\"type\":\"AC_ON\"}],"
          + "\"summary\":\"창문을 열고 에어컨을 틀어주세요\",\"reason\":\"더워요\"}\n```", 23))
          .hasValueSatisfying(p -> assertThat(p.steps()).containsExactly(open(2), ac(23)));
      assertThat(planner.parse("{\"steps\":[],\"summary\":\"시설팀에 고장 신고해주세요\",\"reason\":\"고장\"}", 23))
          .hasValueSatisfying(p -> assertThat(p.steps()).isEmpty());
    }

    @Test
    void 검증_실패는_empty() {
      for (String bad : List.of(
          "그냥 문장",
          "{\"steps\":[{\"type\":\"FAN_ON\"}],\"summary\":\"s\",\"reason\":\"r\"}",                  // 없는 type
          "{\"steps\":[{\"type\":\"WINDOW_OPEN\",\"windows\":5}],\"summary\":\"s\",\"reason\":\"r\"}",  // 창문 1~4
          "{\"steps\":[{\"type\":\"AC_ON\",\"targetTemp\":30}],\"summary\":\"s\",\"reason\":\"r\"}",    // 18~28
          "{\"steps\":[{\"type\":\"AC_ON\"},{\"type\":\"AC_ON\"}],\"summary\":\"s\",\"reason\":\"r\"}", // 중복
          "{\"steps\":[{\"type\":\"WINDOW_OPEN\"},{\"type\":\"WINDOW_CLOSE\"}],\"summary\":\"s\",\"reason\":\"r\"}",
          "{\"steps\":[{\"type\":\"AC_ON\"},{\"type\":\"WINDOW_OPEN\"},{\"type\":\"WINDOW_CLOSE\"},{\"type\":\"AC_ON\"}],"
              + "\"summary\":\"s\",\"reason\":\"r\"}",                                                    // 4개
          "{\"steps\":[],\"summary\":\"" + "가".repeat(101) + "\",\"reason\":\"r\"}",                     // 100자
          "{\"steps\":[],\"reason\":\"r\"}",                                                               // summary 없음
          "{\"steps\": [ 깨진")) {
        assertThat(planner.parse(bad, 23)).as(bad).isEmpty();
      }
    }
  }

  @Nested
  class Rule {

    private final RulePlanner rule = new RulePlanner();

    @Test
    void 답답함은_창문_2곳_더우면_에어컨_추가_다른_방법은_창문_4곳() {
      assertThat(rule.plan(ctx(ComplaintCategory.STUFFY, 1250, 24, 50, null, null, false, false, null)).steps())
          .containsExactly(open(2));
      RawPlan hot = rule.plan(ctx(ComplaintCategory.STUFFY, 1250, 28, 50, null, null, false, false, null));
      assertThat(hot.steps()).containsExactly(open(2), ac(23));
      assertThat(rule.plan(ctx(ComplaintCategory.STUFFY, 1250, 28, 50, null, null, false, false,
          new PlanContext.Previous("이전", hot.steps()))).steps()).containsExactly(open(4), ac(23));
    }

    @Test
    void 더움은_바깥이_3도_이상_시원하면_창문_아니면_에어컨_비_오면_에어컨() {
      assertThat(rule.plan(ctx(ComplaintCategory.HOT, 900, 27, 50, 24.0, 50, false, false, null)).steps())
          .containsExactly(open(2));
      assertThat(rule.plan(ctx(ComplaintCategory.HOT, 900, 27, 50, 25.0, 50, false, false, null)).steps())
          .containsExactly(ac(23));
      assertThat(rule.plan(ctx(ComplaintCategory.HOT, 900, 27, 50, 20.0, 50, true, false, null)).steps())
          .containsExactly(ac(23));
      // 다른 방법: 에어컨 → 창문
      assertThat(rule.plan(ctx(ComplaintCategory.HOT, 900, 27, 50, 25.0, 50, false, false,
          new PlanContext.Previous("이전", List.of(ac(23))))).steps()).containsExactly(open(2));
    }

    @Test
    void 추움은_열린_창문이_있으면_닫기_없으면_짧게_환기_안내() {
      assertThat(rule.plan(ctx(ComplaintCategory.COLD, 900, 18, 50, null, null, false, true, null)).steps())
          .extracting(PlanStep::type).containsExactly(StepType.WINDOW_CLOSE);
      RawPlan brief = rule.plan(ctx(ComplaintCategory.COLD, 900, 18, 50, null, null, false, false, null));
      assertThat(brief.steps()).isEmpty();
      assertThat(brief.summary()).isEqualTo("환기는 쉬는 시간에만 짧게 해주세요");
    }

    @Test
    void 습함은_바깥이_10퍼센트포인트_이상_건조하면_창문_아니면_제습() {
      assertThat(rule.plan(ctx(ComplaintCategory.HUMID, 900, 24, 70, 20.0, 60, false, false, null)).steps())
          .containsExactly(open(2));
      RawPlan dehumidify = rule.plan(ctx(ComplaintCategory.HUMID, 900, 24, 70, 20.0, 65, false, false, null));
      assertThat(dehumidify.steps()).containsExactly(ac(23));
      assertThat(dehumidify.summary()).isEqualTo("에어컨을 켜서 제습해주세요");
    }

    @Test
    void 고장은_단계_없이_시설팀_신고() {
      RawPlan broken = rule.plan(ctx(ComplaintCategory.BROKEN, 900, 24, 50, null, null, false, false, null));
      assertThat(broken.steps()).isEmpty();
      assertThat(broken.summary()).isEqualTo("시설팀에 고장 신고해주세요");
    }
  }
}
