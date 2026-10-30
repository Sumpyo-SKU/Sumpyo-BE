package com.app.sumpyo_be.domain.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.domain.action.service.ActionMeasureService;
import com.app.sumpyo_be.domain.complaint.recommend.LlmPlanner;
import com.app.sumpyo_be.global.ai.LlmClient;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 민원 접수함 (목록 · 반복 민원 · 상세 · AI 추천 · 체크리스트 · 결과 측정). LLM 은 mock.
 * 시각은 2026-10-21 (수) 14:00 KST 고정 (이번 주 = 10/19 월 ~ 10/25 일). 스케줄러는 끄고 직접 호출한다.
 * worker01(W1): 북악관 6층 담당, 기준 CO2 1000 · 온도 20~23 · 습도 40~60 (희망 온도 중간값 21).
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.expire-enabled=false", "sumpyo.action.auto-complete-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.action.measure-enabled=false",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.complaint.salt=test-salt",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",   // 진짜 LlmClient 대신 mock
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@Import(ComplaintInboxIntegrationTest.FixedClock.class)   // @Nested 테스트에도 고정 시각이 적용되도록 명시
class ComplaintInboxIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  /** 2026-10-21 (수) 14:00 KST */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);
  private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 10, 19, 10, 0);
  private static final String W1 = "worker01";
  private static final String LLM_OK = """
      {"steps":[{"type":"WINDOW_OPEN","windows":2},{"type":"AC_ON","targetTemp":23}],
       "summary":"창문 2곳을 열고 에어컨을 틀어주세요","reason":"실내 CO2가 높고 바깥이 선선해서 창문 환기가 효과적이에요"}""";

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
  @Autowired ActionMeasureService measureService;
  @Autowired LlmPlanner llmPlanner;

  long bukak;
  long hyein;
  long r604;   // 기타, 센서 없음
  long r616;
  long r617;   // FULL
  long r618;   // SIM
  long hyeinRoom;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.update("delete from notification");
    jdbc.update("delete from action");
    jdbc.update("delete from complaint");
    jdbc.update("delete from sensor_reading");
    jdbc.update("delete from prediction");
    jdbc.update("delete from outdoor_observation");
    jdbc.update("delete from class_cancellation");
    jdbc.update("delete from class_schedule");
    bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    hyein = jdbc.queryForObject("select id from building where name = '혜인관'", Long.class);
    r604 = roomId("bukak-604");
    r616 = roomId("bukak-616");
    r617 = roomId("bukak-617");
    r618 = roomId("bukak-618");
    jdbc.update("""
        insert into room (building_id, code, number, floor, type, sensor_level)
        values (?, 'hyein-301', '301', 3, 'LECTURE', 'SIM') on conflict (code) do nothing""", hyein);
    hyeinRoom = roomId("hyein-301");
    as(W1, put("/api/me/zones"), "[{\"buildingId\":" + bukak + ",\"floors\":[6]}]").andExpect(status().isOk());
    as(W1, put("/api/me/settings"), """
        {"co2Threshold":1000,"tempMin":20,"tempMax":23,"humidityMin":40,"humidityMax":60}""").andExpect(status().isOk());
  }

  private long roomId(String code) {
    return jdbc.queryForObject("select id from room where code = ?", Long.class, code);
  }

  private ResultActions as(String loginId, MockHttpServletRequestBuilder req, String body) throws Exception {
    req.header("X-Demo-Login", loginId);
    if (body != null) {
      req.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(req);
  }

  private ResultActions as(String loginId, MockHttpServletRequestBuilder req) throws Exception {
    return as(loginId, req, null);
  }

  private long complaint(long roomId, String categories, String content, String status, LocalDateTime createdAt) {
    return complaint(roomId, categories, content, status, createdAt, null, null, null);
  }

  private long complaint(long roomId, String categories, String content, String status, LocalDateTime createdAt,
      Integer co2, BigDecimal temp, Integer humidity) {
    return jdbc.queryForObject("""
        insert into complaint (room_id, categories, content, status, co2_snapshot, temp_snapshot, humidity_snapshot,
                               client_hash, created_at)
        values (?, ?, ?, ?, ?, ?, ?, 'test', ?) returning id""", Long.class,
        roomId, categories, content, status, co2, temp, humidity, createdAt);
  }

  private void reading(long roomId, int co2, int temp, int humidity, LocalDateTime at) {
    jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, ?, ?, ?, ?)",
        roomId, co2, temp, humidity, at);
  }

  private void prediction(long roomId, LocalDateTime targetAt, int co2, LocalDateTime createdAt) {
    jdbc.update("""
        insert into prediction (room_id, created_at, target_at, co2, temp, humidity, model_version)
        values (?, ?, ?, ?, 23, 50, 'inbox-test')""", roomId, createdAt, targetAt, co2);
  }

  private ResultActions recommend(long complaintId, boolean retry) throws Exception {
    return as(W1, post("/api/complaints/{id}/recommendation", complaintId), retry ? "{\"retry\":true}" : "{}");
  }

  private long actionId(ResultActions res) throws Exception {
    return ((Number) JsonPath.read(res.andReturn().getResponse().getContentAsString(), "$.data.actionId")).longValue();
  }

  private ResultActions step(long actionId, String body) throws Exception {
    return as(W1, patch("/api/actions/{id}/steps", actionId), body);
  }

  @Nested
  class ListAndSearch {

    @Test
    void 필터별_목록과_counts_는_status_를_뺀_같은_필터() throws Exception {
      long c1 = complaint(r618, "STUFFY", "5교시만 되면 너무 답답하고 졸려요", "RECEIVED", NOW.minusMinutes(6));
      long c2 = complaint(r617, "HOT", "더워요", "IN_PROGRESS", NOW.minusMinutes(30));
      long c3 = complaint(hyeinRoom, "HUMID,HOT", "습해요", "DONE", NOW.minusHours(2));
      long c4 = complaint(r618, "SMELL", null, "RECEIVED", NOW.minusHours(3));

      as(W1, get("/api/complaints"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.counts.all").value(4))
          .andExpect(jsonPath("$.data.counts.received").value(2))
          .andExpect(jsonPath("$.data.counts.inProgress").value(1))
          .andExpect(jsonPath("$.data.counts.done").value(1))
          .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c1, (int) c2, (int) c3, (int) c4)))
          .andExpect(jsonPath("$.data.content[0].room").value("북악관 618호"))
          .andExpect(jsonPath("$.data.content[0].categoryLabels", contains("답답해요")))
          .andExpect(jsonPath("$.data.content[0].preview").value("5교시만 되면 너무 답답하고 졸려요"))
          .andExpect(jsonPath("$.data.content[0].status").value("RECEIVED"))
          .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-21T13:54:00"))
          .andExpect(jsonPath("$.data.content[2].categoryLabels", contains("습해요", "더워요")))
          .andExpect(jsonPath("$.data.content[3].preview").value(nullValue()))
          .andExpect(jsonPath("$.data.hasNext").value(false))
          .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

      as(W1, get("/api/complaints").param("status", "RECEIVED"))
          .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c1, (int) c4)))
          .andExpect(jsonPath("$.data.counts.all").value(4));
      as(W1, get("/api/complaints").param("buildingId", String.valueOf(hyein)))
          .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c3)))
          .andExpect(jsonPath("$.data.counts.all").value(1));
      as(W1, get("/api/complaints").param("roomId", String.valueOf(r618)))
          .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c1, (int) c4)));
    }

    @Test
    void 검색은_건물명_호실_내용() throws Exception {
      long c1 = complaint(r618, "STUFFY", "5교시만 되면 너무 답답하고 졸려요", "RECEIVED", NOW.minusMinutes(6));
      complaint(r617, "HOT", "더워요", "RECEIVED", NOW.minusMinutes(30));
      long c3 = complaint(hyeinRoom, "HUMID", "습해요", "RECEIVED", NOW.minusHours(2));

      as(W1, get("/api/complaints").param("q", "혜인")).andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c3)));
      as(W1, get("/api/complaints").param("q", "618")).andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c1)));
      as(W1, get("/api/complaints").param("q", "졸려")).andExpect(jsonPath("$.data.content[*].complaintId", contains((int) c1)));
      as(W1, get("/api/complaints").param("q", "%")).andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void 호와_건물명을_떼고_번호로_520_520호_520_호_북악관_520호는_같은_결과() throws Exception {
      long r520 = roomId("bukak-520");
      long a = complaint(r520, "STUFFY", "환기 좀 해주세요", "RECEIVED", NOW.minusMinutes(5));
      long b = complaint(r520, "HOT", null, "DONE", NOW.minusMinutes(50));
      complaint(r617, "STUFFY", "520호 옆방인데요", "RECEIVED", NOW.minusHours(3));   // 내용에 520 → "520" 단어로는 걸림

      for (String q : List.of("520", "520호", "520 호", "북악관 520호", "  북악관520호  ")) {
        as(W1, get("/api/complaints").param("q", q))
            .andExpect(jsonPath("$.data.content[*].complaintId", hasSize(3)))
            .andExpect(jsonPath("$.data.content[0].complaintId").value(a))
            .andExpect(jsonPath("$.data.content[1].complaintId").value(b));
      }
    }

    @Test
    void 민원_종류_한글로_검색_내용에_없어도_종류가_맞으면() throws Exception {
      long stuffy1 = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      long stuffy2 = complaint(r617, "HOT,STUFFY", "공기가 안 좋아요", "RECEIVED", NOW.minusMinutes(10));
      complaint(r617, "HOT", "더워요", "RECEIVED", NOW.minusMinutes(20));
      complaint(r618, "HUMID", null, "RECEIVED", NOW.minusMinutes(30));

      for (String q : List.of("답답해요", "답답", "stuffy")) {
        as(W1, get("/api/complaints").param("q", q))
            .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) stuffy1, (int) stuffy2)));
      }
      as(W1, get("/api/complaints").param("q", "덥")).andExpect(jsonPath("$.data.content", hasSize(2)));   // HOT 2건
      as(W1, get("/api/complaints").param("q", "습")).andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void 여러_단어는_모두_맞아야_617_답답은_617호_STUFFY_만() throws Exception {
      long hit = complaint(r617, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      complaint(r617, "HOT", null, "RECEIVED", NOW.minusMinutes(10));      // 617 이지만 답답 아님
      complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(20));   // 답답이지만 618

      as(W1, get("/api/complaints").param("q", "617 답답"))
          .andExpect(jsonPath("$.data.content[*].complaintId", contains((int) hit)))
          .andExpect(jsonPath("$.data.counts.all").value(1));
    }

    @Test
    void 미리보기는_50자_넘으면_말줄임() throws Exception {
      complaint(r618, "STUFFY", "가".repeat(60), "RECEIVED", NOW);
      as(W1, get("/api/complaints")).andExpect(jsonPath("$.data.content[0].preview").value("가".repeat(50) + "…"));
    }

    @Test
    void 무한_스크롤_45건을_20개씩_같은_시각이어도_중복_누락_없음() throws Exception {
      List<Long> expected = new ArrayList<>();
      for (int i = 0; i < 45; i++) {
        // 15건씩 같은 시각 → (created_at, id) 복합 커서가 필요
        expected.add(complaint(r618, "STUFFY", "민원 " + i, "RECEIVED", NOW.minusMinutes(i / 15)));
      }
      List<Integer> seen = new ArrayList<>();
      String cursor = null;
      int[] sizes = {20, 20, 5};
      for (int size : sizes) {
        MockHttpServletRequestBuilder req = get("/api/complaints").param("size", "20");
        if (cursor != null) {
          req.param("cursor", cursor);
        }
        String res = as(W1, req).andExpect(jsonPath("$.data.content", hasSize(size)))
            .andReturn().getResponse().getContentAsString();
        seen.addAll(JsonPath.read(res, "$.data.content[*].complaintId"));
        cursor = JsonPath.read(res, "$.data.nextCursor");
        assertThat((Boolean) JsonPath.read(res, "$.data.hasNext")).isEqualTo(size == 20);
      }
      assertThat(cursor).isNull();
      assertThat(seen).hasSize(45).doesNotHaveDuplicates();
      assertThat(new HashSet<>(seen)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Long::intValue).toList());
      // 최신순: 시각 내림차순, 같은 시각이면 id 내림차순
      List<Integer> sorted = new ArrayList<>(seen);
      sorted.sort((a, b) -> {
        int ia = expected.indexOf((long) a);
        int ib = expected.indexOf((long) b);
        int byTime = Integer.compare(ia / 15, ib / 15);
        return byTime != 0 ? byTime : Integer.compare(b, a);
      });
      assertThat(seen).isEqualTo(sorted);
    }

    @Test
    void 잘못된_커서는_400() throws Exception {
      as(W1, get("/api/complaints").param("cursor", "not-a-cursor")).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class Repeated {

    @Test
    void 이번_주_5건_이상만_많은_순_센서_매일_일부_없음() throws Exception {
      for (int i = 0; i < 7; i++) {
        complaint(r618, "STUFFY", null, "RECEIVED", MONDAY.plusHours(i * 7));
      }
      complaint(r618, "STUFFY", null, "RECEIVED", MONDAY.minusDays(3));   // 지난 주는 안 셈
      for (int i = 0; i < 5; i++) {
        complaint(r617, "HOT", null, "RECEIVED", MONDAY.plusHours(i));
      }
      for (int i = 0; i < 6; i++) {
        complaint(r604, "STUFFY", null, "RECEIVED", MONDAY.plusHours(i));
      }
      for (int i = 0; i < 4; i++) {
        complaint(r616, "STUFFY", null, "RECEIVED", MONDAY.plusHours(i));   // 4건은 제외
      }
      // 618: 월·화·수 모두 1000 이상 → 지난 평일(월~수) 전부 → 매일
      reading(r618, 1100, 24, 50, MONDAY.plusHours(2));
      reading(r618, 900, 24, 50, MONDAY.plusHours(3));
      reading(r618, 1050, 24, 50, MONDAY.plusDays(1));
      reading(r618, 1200, 24, 50, NOW.minusHours(1));
      // 617: 월만 초과
      reading(r617, 1100, 24, 50, MONDAY.plusHours(1));
      reading(r617, 900, 24, 50, MONDAY.plusDays(1));

      as(W1, get("/api/complaints/repeated"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[*].room", contains("북악관 618호", "북악관 604호", "북악관 617호")))
          .andExpect(jsonPath("$.data[0].roomId").value(r618))
          .andExpect(jsonPath("$.data[0].count").value(7))
          .andExpect(jsonPath("$.data[0].overThresholdDays").value(3))
          .andExpect(jsonPath("$.data[0].message").value("이번 주 7건 · 센서도 매일 1,000ppm 초과"))
          .andExpect(jsonPath("$.data[1].overThresholdDays").value(nullValue()))
          .andExpect(jsonPath("$.data[1].message").value("이번 주 6건"))
          .andExpect(jsonPath("$.data[2].overThresholdDays").value(1))
          .andExpect(jsonPath("$.data[2].message").value("이번 주 5건 · 센서도 1일 1,000ppm 초과"));

      as(W1, get("/api/complaints/repeated").param("buildingId", String.valueOf(hyein)))
          .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 지난_주와_잘못된_week() throws Exception {
      for (int i = 0; i < 5; i++) {
        complaint(r616, "STUFFY", null, "RECEIVED", MONDAY.minusDays(5).plusHours(i));
      }
      as(W1, get("/api/complaints/repeated")).andExpect(jsonPath("$.data", hasSize(0)));
      as(W1, get("/api/complaints/repeated").param("week", "2026-W42"))
          .andExpect(jsonPath("$.data[*].room", contains("북악관 616호")));
      as(W1, get("/api/complaints/repeated").param("week", "2026-43")).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class Detail {

    @Test
    void 스냅샷은_조회자_기준으로_판정() throws Exception {
      long id = complaint(r618, "STUFFY", "답답해요", "RECEIVED", NOW.minusMinutes(5), 1250, new BigDecimal("24.1"), 61);

      as(W1, get("/api/complaints/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.room").value("북악관 618호"))
          .andExpect(jsonPath("$.data.roomId").value(r618))
          .andExpect(jsonPath("$.data.categoryLabels", contains("답답해요")))
          .andExpect(jsonPath("$.data.snapshot.co2").value(1250))
          .andExpect(jsonPath("$.data.snapshot.co2Level").value("CRITICAL"))
          .andExpect(jsonPath("$.data.snapshot.co2OverBy").value(250))
          .andExpect(jsonPath("$.data.snapshot.comfortScore").value(58))
          .andExpect(jsonPath("$.data.snapshot.comfortLevel").value("NORMAL"))
          .andExpect(jsonPath("$.data.snapshot.temp").value(24.1))
          .andExpect(jsonPath("$.data.snapshot.tempLabel").value("살짝 더워요"))
          .andExpect(jsonPath("$.data.snapshot.humidity").value(61))
          .andExpect(jsonPath("$.data.snapshot.humidityLabel").value("조금 습해요"))
          .andExpect(jsonPath("$.data.action").value(nullValue()));
    }

    @Test
    void 스냅샷이_없으면_null_추천하면_조치가_연결된다_없는_민원은_404() throws Exception {
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      as(W1, get("/api/complaints/{id}", id)).andExpect(jsonPath("$.data.snapshot").value(nullValue()));

      long actionId = actionId(recommend(id, false));
      as(W1, get("/api/complaints/{id}", id))
          .andExpect(jsonPath("$.data.action.actionId").value(actionId))
          .andExpect(jsonPath("$.data.action.status").value("PENDING"))
          .andExpect(jsonPath("$.data.action.source").value("RULE"));
      as(W1, get("/api/complaints/{id}", 999_999)).andExpect(status().isNotFound());
    }
  }

  @Nested
  class Recommendation {

    @Test
    void LLM_이_고르고_숫자는_서버가_계산() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn(LLM_OK);
      long id = complaint(r618, "STUFFY", "답답해요", "RECEIVED", NOW.minusMinutes(5));

      recommend(id, false)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.summary").value("창문 2곳을 30분 열고 에어컨을 틀어주세요"))
          .andExpect(jsonPath("$.data.reason").value("실내 CO2가 높고 바깥이 선선해서 창문 환기가 효과적이에요"))
          .andExpect(jsonPath("$.data.co2Before").value(1250))
          .andExpect(jsonPath("$.data.expectedCo2After").value(757))
          .andExpect(jsonPath("$.data.source").value("LLM"))
          .andExpect(jsonPath("$.data.status").value("PENDING"))
          .andExpect(jsonPath("$.data.steps[0].type").value("WINDOW_OPEN"))
          .andExpect(jsonPath("$.data.steps[0].label").value("창문 2곳 열기"))
          .andExpect(jsonPath("$.data.steps[0].minutes").value(30))
          .andExpect(jsonPath("$.data.steps[1].type").value("AC_ON"))
          .andExpect(jsonPath("$.data.steps[1].label").value("에어컨 켜기"))
          .andExpect(jsonPath("$.data.steps[1].targetTemp").value(23));

      Map<String, Object> row = jdbc.queryForMap("select * from action where complaint_id = ?", id);
      assertThat(row).containsEntry("source", "COMPLAINT").containsEntry("status", "PENDING")
          .containsEntry("recommend", "OPEN").containsEntry("metric", "CO2").containsEntry("plan_source", "LLM")
          .containsEntry("co2_before", 1250).containsEntry("expected_co2_after", 757).containsEntry("ac_target_temp", 23);
    }

    @Test
    void LLM_이_넣은_숫자는_서버_숫자로_교체() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"WINDOW_OPEN","windows":2}],"summary":"창문을 10분 열어주세요",
           "reason":"CO2가 높아 20분 정도 환기가 필요해요"}""");
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));

      recommend(id, false)
          .andExpect(jsonPath("$.data.summary").value("창문 2곳을 30분 열어주세요"))
          .andExpect(jsonPath("$.data.reason").value("CO2가 높아 환기가 필요해요"))
          .andExpect(jsonPath("$.data.source").value("LLM"));
    }

    @Test
    void 잘못된_type_깨진_JSON_타임아웃이면_규칙_기반() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      for (String bad : List.of("{\"steps\":[{\"type\":\"FAN_ON\"}],\"summary\":\"s\",\"reason\":\"r\"}", "{ 깨진")) {
        when(llmClient.complete(anyString())).thenReturn(bad);
        long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
        recommend(id, false)
            .andExpect(jsonPath("$.data.source").value("RULE"))
            .andExpect(jsonPath("$.data.summary").value("창문 2곳을 30분 열어 환기해주세요"));
      }

      when(llmClient.complete(anyString())).thenAnswer(inv -> {
        Thread.sleep(2_000);
        return LLM_OK;
      });
      ReflectionTestUtils.setField(llmPlanner, "timeout", Duration.ofMillis(300));
      try {
        long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
        recommend(id, false).andExpect(jsonPath("$.data.source").value("RULE"));
      } finally {
        ReflectionTestUtils.setField(llmPlanner, "timeout", Duration.ofSeconds(10));
      }
    }

    @Test
    void 다른_방법이면_이전_추천을_LLM_에_넘기고_같은_조치의_추천을_교체() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn(LLM_OK);
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      long actionId = actionId(recommend(id, false));

      // 다시 눌러도(retry 아님) 그대로
      recommend(id, false).andExpect(jsonPath("$.data.actionId").value(actionId));
      verify(llmClient, times(1)).complete(anyString());

      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"WINDOW_OPEN","windows":4}],"summary":"창문을 활짝 열어주세요","reason":"더 빨리 환기돼요"}""");
      recommend(id, true)
          .andExpect(jsonPath("$.data.actionId").value(actionId))
          .andExpect(jsonPath("$.data.summary").value("창문 4곳을 15분 활짝 열어주세요"));
      ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
      verify(llmClient, times(2)).complete(prompt.capture());
      assertThat(prompt.getValue()).contains("이전 추천과 다른 방법", "이전 추천: 창문 2곳을 30분 열고 에어컨을 틀어주세요",
          "WINDOW_OPEN, AC_ON");
      assertThat(jdbc.queryForObject("select count(*) from action where complaint_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void 규칙_기반_다른_방법은_다음_대안() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(id, false).andExpect(jsonPath("$.data.steps[0].windows").value(2));
      recommend(id, true)
          .andExpect(jsonPath("$.data.steps[0].windows").value(4))
          .andExpect(jsonPath("$.data.source").value("RULE"));
    }

    @Test
    void ML_예측을_프롬프트에_넘기고_오르는_중이면_예측값부터_계산() throws Exception {
      reading(r618, 1100, 22, 50, NOW.minusMinutes(1));
      jdbc.update("""
          insert into class_schedule (room_id, course_name, day_of_week, start_time, end_time, semester)
          values (?, '데이터크롤링', 3, '13:30', '14:45', 'inbox-test')""", r618);
      prediction(r618, NOW.plusMinutes(30), 1150, NOW.minusMinutes(20));   // 예전 예측 (같은 시각)
      prediction(r618, NOW.plusMinutes(30), 1180, NOW.minusMinutes(5));    // 최신 예측
      prediction(r618, NOW.plusMinutes(45), 1280, NOW.minusMinutes(5));    // 수업 종료 14:45
      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"WINDOW_OPEN","windows":2}],"summary":"창문을 열어주세요","reason":"CO2가 오르는 중이에요"}""");
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));

      // 시작값 1180 (현재 1100 보다 큼) → 25분, 420 + 760·e^(−0.75) = 779
      recommend(id, false)
          .andExpect(jsonPath("$.data.co2Before").value(1100))
          .andExpect(jsonPath("$.data.forecastCo2").value(1280))
          .andExpect(jsonPath("$.data.steps[0].minutes").value(25))
          .andExpect(jsonPath("$.data.expectedCo2After").value(779))
          .andExpect(jsonPath("$.data.summary").value("창문 2곳을 25분 열어주세요"));
      ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
      verify(llmClient).complete(prompt.capture());
      assertThat(prompt.getValue()).contains("그대로 두면 30분 뒤 1,180ppm, 수업 종료 시 1,280ppm 예상");
      as(W1, get("/api/complaints/{id}", id)).andExpect(jsonPath("$.data.action.forecastCo2").value(1280));
    }

    @Test
    void 예측이_내려가는_중이거나_없으면_현재_CO2_로_계산_forecastCo2_는_null() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      prediction(r618, NOW.plusMinutes(30), 1100, NOW.minusMinutes(5));
      long falling = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(falling, false)
          .andExpect(jsonPath("$.data.expectedCo2After").value(757))   // 1250 에서 시작
          .andExpect(jsonPath("$.data.forecastCo2").value(1100));        // 수업이 없으면 30분 뒤

      jdbc.update("delete from prediction");
      long none = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(4));
      recommend(none, false)
          .andExpect(jsonPath("$.data.expectedCo2After").value(757))
          .andExpect(jsonPath("$.data.forecastCo2").value(nullValue()));
    }

    @Test
    void 삼십분_상한이면_예상_CO2_를_부풀리지_않는다() throws Exception {
      reading(r618, 2000, 22, 50, NOW.minusMinutes(1));
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(id, false)
          .andExpect(jsonPath("$.data.steps[0].minutes").value(30))
          .andExpect(jsonPath("$.data.expectedCo2After").value(1062));
    }

    @Test
    void 현재_측정이_없으면_접수_스냅샷_CO2() throws Exception {
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5), 1250, null, null);
      recommend(id, false).andExpect(jsonPath("$.data.co2Before").value(1250));
    }

    @Test
    void 접수_상태가_아니면_409() throws Exception {
      long id = complaint(r618, "STUFFY", null, "IN_PROGRESS", NOW.minusMinutes(5));
      recommend(id, false).andExpect(status().isConflict());
    }

    @Test
    void 에어컨만_있는_추천의_예상_효과는_온도_25에서_23() throws Exception {
      reading(r618, 436, 25, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"AC_ON","targetTemp":23}],"summary":"에어컨을 23°C로 켜주세요","reason":"실내가 적정보다 더워요"}""");
      long id = complaint(r618, "HOT", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(id, false)
          .andExpect(jsonPath("$.data.summary").value("에어컨을 23°C로 켜주세요"))
          .andExpect(jsonPath("$.data.co2Before").value(436))
          .andExpect(jsonPath("$.data.expectedCo2After").value(nullValue()))
          .andExpect(jsonPath("$.data.effectMetric").value("TEMP"))
          .andExpect(jsonPath("$.data.effectBefore").value(25))
          .andExpect(jsonPath("$.data.effectAfter").value(23));
    }

    @Test
    void 창문과_에어컨이면_예상_효과는_CO2() throws Exception {
      reading(r618, 1250, 25, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn(LLM_OK);
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(id, false)
          .andExpect(jsonPath("$.data.effectMetric").value("CO2"))
          .andExpect(jsonPath("$.data.effectBefore").value(1250))
          .andExpect(jsonPath("$.data.effectAfter").value(757));
    }

    @Test
    void 계산할_수_없으면_예상_효과는_전부_null() throws Exception {
      // 창문 닫기만 → CO2·온도 효과 없음
      reading(r618, 436, 19, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"WINDOW_CLOSE"}],"summary":"창문을 닫아주세요","reason":"찬 바람이 들어와요"}""");
      long cold = complaint(r618, "COLD", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(cold, false)
          .andExpect(jsonPath("$.data.effectMetric").value(nullValue()))
          .andExpect(jsonPath("$.data.effectBefore").value(nullValue()))
          .andExpect(jsonPath("$.data.effectAfter").value(nullValue()));

      // 에어컨만인데 현재 온도를 모름 (측정 없음, 접수 때 온도도 없음)
      when(llmClient.complete(anyString())).thenReturn("""
          {"steps":[{"type":"AC_ON","targetTemp":23}],"summary":"에어컨을 23°C로 켜주세요","reason":"더워요"}""");
      long hot = complaint(r617, "HOT", null, "RECEIVED", NOW.minusMinutes(5));
      recommend(hot, false)
          .andExpect(jsonPath("$.data.effectMetric").value(nullValue()))
          .andExpect(jsonPath("$.data.effectBefore").value(nullValue()))
          .andExpect(jsonPath("$.data.effectAfter").value(nullValue()));
    }
  }

  @Nested
  class Checklist {

    private long startedAction() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn(LLM_OK);
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      long actionId = actionId(recommend(id, false));
      as(W1, post("/api/actions/{id}/start", actionId)).andExpect(status().isOk());
      return actionId;
    }

    @Test
    void 처리_시작하면_민원도_처리_중_창문_시각은_찍지_않는다() throws Exception {
      long actionId = startedAction();
      Map<String, Object> row = jdbc.queryForMap("select * from action where id = ?", actionId);
      assertThat(row).containsEntry("status", "IN_PROGRESS").containsEntry("window_opened_at", null);
      assertThat(jdbc.queryForObject("select c.status from complaint c join action a on a.complaint_id = c.id where a.id = ?",
          String.class, actionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void plan_에_없는_단계_400_중복_체크_200_시작_전_409() throws Exception {
      reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
      when(llmClient.complete(anyString())).thenReturn(LLM_OK);
      long id = complaint(r618, "STUFFY", null, "RECEIVED", NOW.minusMinutes(5));
      long pending = actionId(recommend(id, false));
      step(pending, "{\"type\":\"WINDOW_OPEN\"}").andExpect(status().isConflict());   // 시작 전

      as(W1, post("/api/actions/{id}/start", pending)).andExpect(status().isOk());
      step(pending, "{\"type\":\"WINDOW_CLOSE\"}").andExpect(status().isBadRequest());
      step(pending, "{}").andExpect(status().isBadRequest());
      step(pending, "{\"type\":\"WINDOW_OPEN\"}").andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
          .andExpect(jsonPath("$.data.steps[0].doneAt").value("2026-10-21T14:00:00"))
          .andExpect(jsonPath("$.data.steps[1].doneAt").value(nullValue()));
      step(pending, "{\"type\":\"WINDOW_OPEN\"}").andExpect(status().isOk())
          .andExpect(jsonPath("$.data.steps[0].doneAt").value("2026-10-21T14:00:00"));
    }

    @Test
    void 전부_체크하면_자동_완료_결과는_measure_at_이_지나야_기록() throws Exception {
      long actionId = startedAction();
      step(actionId, "{\"type\":\"WINDOW_OPEN\"}").andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
      step(actionId, "{\"type\":\"AC_ON\",\"targetTemp\":24}")
          .andExpect(jsonPath("$.data.status").value("DONE"))
          .andExpect(jsonPath("$.data.steps[1].targetTemp").value(24))
          .andExpect(jsonPath("$.data.completedAt").value("2026-10-21T14:00:00"))
          .andExpect(jsonPath("$.data.measureAt").value("2026-10-21T14:30:00"))
          .andExpect(jsonPath("$.data.actualCo2After").value(nullValue()))
          .andExpect(jsonPath("$.data.goalReached").value(nullValue()));
      assertThat(jdbc.queryForObject("select c.status from complaint c join action a on a.complaint_id = c.id where a.id = ?",
          String.class, actionId)).isEqualTo("DONE");
      step(actionId, "{\"type\":\"AC_ON\"}").andExpect(status().isConflict());   // 완료 후

      reading(r618, 750, 22, 50, NOW.plusMinutes(29));
      assertThat(measureService.measureDue(NOW.plusMinutes(29))).isZero();   // measure_at 전
      assertThat(measureService.measureDue(NOW.plusMinutes(31))).isEqualTo(1);
      assertThat(measureService.measureDue(NOW.plusMinutes(32))).isZero();   // 한 번만

      long complaintId = jdbc.queryForObject("select complaint_id from action where id = ?", Long.class, actionId);
      as(W1, get("/api/complaints/{id}", complaintId))
          .andExpect(jsonPath("$.data.status").value("DONE"))
          .andExpect(jsonPath("$.data.action.co2Before").value(1250))
          .andExpect(jsonPath("$.data.action.actualCo2After").value(750))
          .andExpect(jsonPath("$.data.action.goalReached").value(true));   // 750 ≤ 예상 757
    }

    @Test
    void 측정이_계속_없으면_30분_뒤_포기() throws Exception {
      long actionId = startedAction();
      step(actionId, "{\"type\":\"WINDOW_OPEN\"}");
      step(actionId, "{\"type\":\"AC_ON\"}");
      assertThat(measureService.measureDue(NOW.plusMinutes(31))).isZero();
      assertThat(measureService.measureDue(NOW.plusMinutes(61))).isZero();
      assertThat(jdbc.queryForObject("select actual_co2_after from action where id = ?", Integer.class, actionId)).isNull();
    }

    @Test
    void 창문_단계가_없으면_완료_시점_값으로_바로_기록() throws Exception {
      reading(r618, 900, 27, 50, NOW.minusMinutes(1));   // 더운데 바깥 정보 없음 → 규칙: 에어컨
      long id = complaint(r618, "HOT", null, "RECEIVED", NOW.minusMinutes(5));
      long actionId = actionId(recommend(id, false)
          .andExpect(jsonPath("$.data.steps[0].type").value("AC_ON"))
          .andExpect(jsonPath("$.data.steps[0].targetTemp").value(21))
          .andExpect(jsonPath("$.data.expectedCo2After").value(nullValue())));
      as(W1, post("/api/actions/{id}/start", actionId)).andExpect(status().isOk());
      step(actionId, "{\"type\":\"AC_ON\"}")
          .andExpect(jsonPath("$.data.status").value("DONE"))
          .andExpect(jsonPath("$.data.measureAt").value("2026-10-21T14:00:00"))
          .andExpect(jsonPath("$.data.actualCo2After").value(900))
          .andExpect(jsonPath("$.data.goalReached").value(true));   // 예상이 없으면 900 < 기준 1000
    }

    @Test
    void 고장은_단계가_없어_처리_시작과_함께_완료() throws Exception {
      long id = complaint(r618, "BROKEN", null, "RECEIVED", NOW.minusMinutes(5));
      long actionId = actionId(recommend(id, false)
          .andExpect(jsonPath("$.data.summary").value("시설팀에 고장 신고해주세요"))
          .andExpect(jsonPath("$.data.steps", hasSize(0))));
      as(W1, post("/api/actions/{id}/start", actionId)).andExpect(jsonPath("$.data.status").value("DONE"));
      assertThat(jdbc.queryForObject("select status from complaint where id = ?", String.class, id)).isEqualTo("DONE");
    }
  }

  @Test
  void 전체_흐름_제출_추천_시작_체크_2개_완료_측정() throws Exception {
    reading(r618, 1250, 22, 50, NOW.minusMinutes(1));
    when(llmClient.complete(anyString())).thenReturn(LLM_OK);

    String created = mvc.perform(post("/api/complaints").contentType(MediaType.APPLICATION_JSON)
            .content("{\"roomId\":" + r618 + ",\"categories\":[\"STUFFY\"],\"content\":\"답답해요\",\"clientId\":\"flow\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    long complaintId = ((Number) JsonPath.read(created, "$.data.complaintId")).longValue();

    as(W1, get("/api/complaints/{id}", complaintId))
        .andExpect(jsonPath("$.data.status").value("RECEIVED"))
        .andExpect(jsonPath("$.data.snapshot.co2").value(1250));
    long actionId = actionId(recommend(complaintId, false));
    as(W1, post("/api/actions/{id}/start", actionId)).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    as(W1, get("/api/complaints/{id}", complaintId)).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    step(actionId, "{\"type\":\"WINDOW_OPEN\"}");
    step(actionId, "{\"type\":\"AC_ON\"}").andExpect(jsonPath("$.data.status").value("DONE"));

    reading(r618, 800, 22, 50, NOW.plusMinutes(30));
    measureService.measureDue(NOW.plusMinutes(31));
    as(W1, get("/api/complaints/{id}", complaintId))
        .andExpect(jsonPath("$.data.status").value("DONE"))
        .andExpect(jsonPath("$.data.action.actualCo2After").value(800))
        .andExpect(jsonPath("$.data.action.goalReached").value(false));   // 800 > 예상 757
  }
}
