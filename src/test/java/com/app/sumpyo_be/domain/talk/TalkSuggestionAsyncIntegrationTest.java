package com.app.sumpyo_be.domain.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.global.ai.LlmClient;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 추천 답변 비동기 생성 (실제 @Async 실행기). LLM 은 mock 으로 지연·실패를 흉내 낸다.
 * 시각은 2026-10-25 (일) 03:00 KST 고정. worker01 이 북악관 6층 글을 쓰고 worker02(6층 담당)가 연다.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    // 로컬 .env 에 공공데이터 키가 있어도 외부 API 를 부르지 않도록
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.auto-complete-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",   // 진짜 LlmClient 대신 mock
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@Import(TalkSuggestionAsyncIntegrationTest.FixedClock.class)
class TalkSuggestionAsyncIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 3, 0);
  private static final String RULE_LOCATION = "지금 북악관 6층에 있어요";

  @TestConfiguration
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
    }
  }

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @MockitoBean LlmClient llmClient;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    long bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    for (String loginId : new String[] {"worker01", "worker02"}) {
      mvc.perform(put("/api/me/zones").header("X-Demo-Login", loginId).contentType(MediaType.APPLICATION_JSON)
          .content("[{\"buildingId\":" + bukak + ",\"floors\":[6]}]")).andExpect(status().isOk());
    }
  }

  private long send(String content) throws Exception {
    String res = mvc.perform(post("/api/talks").header("X-Demo-Login", "worker01")
            .contentType(MediaType.APPLICATION_JSON).content("{\"floor\":6,\"content\":\"" + content + "\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(res, "$.data.talkId")).longValue();
  }

  private String stored(long talkId) {
    return jdbc.queryForObject("select suggestions from talk_post where id = ?", String.class, talkId);
  }

  @Test
  void POST_는_LLM_을_기다리지_않고_생성_전_조회는_규칙_기반_완료_후엔_LLM_답변() throws Exception {
    when(llmClient.complete(anyString())).thenAnswer(inv -> {
      Thread.sleep(2_000);
      return "[\"네 6층이에요\", \"지금 갈게요\"]";
    });

    long start = System.nanoTime();
    long talkId = send("6층에 계신 분 있나요?");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));

    // 아직 생성 중 → 규칙 기반으로 즉시 응답하고 저장하지 않는다
    mvc.perform(get("/api/talks/{id}", talkId).header("X-Demo-Login", "worker02"))
        .andExpect(jsonPath("$.data.suggestions", contains(RULE_LOCATION)));
    assertThat(stored(talkId)).isNull();

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        assertThat(stored(talkId)).isEqualTo("[\"네 6층이에요\",\"지금 갈게요\"]"));
    mvc.perform(get("/api/talks/{id}", talkId).header("X-Demo-Login", "worker02"))
        .andExpect(jsonPath("$.data.suggestions", contains("네 6층이에요", "지금 갈게요")));
  }

  @Test
  void LLM_이_실패하면_규칙_기반으로_저장() throws Exception {
    when(llmClient.complete(anyString())).thenThrow(new IllegalStateException("429"));

    long talkId = send("6층에 계신 분 있나요?");

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        assertThat(stored(talkId)).isEqualTo("[\"" + RULE_LOCATION + "\"]"));
    assertThat(jdbc.queryForObject("select suggestions_generated_at from talk_post where id = ?",
        LocalDateTime.class, talkId)).isEqualTo(NOW);
  }
}
