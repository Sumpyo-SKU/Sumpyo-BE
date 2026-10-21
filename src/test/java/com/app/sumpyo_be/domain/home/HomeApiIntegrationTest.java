package com.app.sumpyo_be.domain.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.domain.action.service.RainCloseActionService;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.ForecastSlot;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.Nowcast;
import com.app.sumpyo_be.domain.outdoor.service.OutdoorCollector;
import com.app.sumpyo_be.domain.outdoor.service.RainForecast;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 홈 화면 (내 강의실, 알림 배지, 바깥 공기, 수업 중 예보, 창문 환기 예측 카드, 비 예보 창문 닫기).
 * 시각은 2026-10-21 (수) 10:00 KST 고정, 시드 시간표는 지우고 테스트가 넣은 수업만 쓴다.
 * 바깥 공기 외부 API 는 mock, 스케줄러는 끄고 직접 호출한다.
 * 계정: worker01(W1) 북악관 6층 담당 (기준 1000, 습도 40~60), worker02(W2) 담당 구역 없음.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.outdoor.collect-enabled=false",
    "sumpyo.action.rain-close-enabled=false",
    "sumpyo.outdoor.api-key=test-key", "sumpyo.outdoor.nx=60", "sumpyo.outdoor.ny=127",
    "sumpyo.outdoor.station=정릉로",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@Import(HomeApiIntegrationTest.FixedClock.class)   // @Nested 테스트에도 고정 시각이 적용되도록 명시
class HomeApiIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  /** 2026-10-21 (수) 10:00 KST */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0);
  private static final int WEDNESDAY = 3;
  private static final String W1 = "worker01";
  private static final String W2 = "worker02";

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

  @MockitoBean OutdoorApiClient outdoorApi;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OutdoorCollector outdoorCollector;
  @Autowired RainForecast rainForecast;
  @Autowired RainCloseActionService rainCloseActionService;

  long bukak;
  long hyein;
  long r608;
  long r616;
  long r617;   // FULL
  long r618;   // SIM

  @BeforeEach
  void setUp() throws Exception {
    jdbc.update("delete from notification");
    jdbc.update("delete from action");
    jdbc.update("delete from prediction");
    jdbc.update("delete from sensor_reading");
    jdbc.update("delete from class_cancellation");
    jdbc.update("delete from class_schedule");
    jdbc.update("delete from outdoor_observation");
    bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    hyein = jdbc.queryForObject("select id from building where name = '혜인관'", Long.class);
    r608 = roomId("bukak-608");
    r616 = roomId("bukak-616");
    r617 = roomId("bukak-617");
    r618 = roomId("bukak-618");
    as(W1, put("/api/me/zones"), "[{\"buildingId\":" + bukak + ",\"floors\":[6]}]").andExpect(status().isOk());
    as(W2, put("/api/me/zones"), "[]").andExpect(status().isOk());
    rainForecast.update(List.of(), NOW.minusDays(1));   // 메모리 예보 초기화 (오래된 것 = 없음)
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

  private long schedule(long roomId, String start, String end) {
    return jdbc.queryForObject("""
        insert into class_schedule (room_id, course_name, day_of_week, start_time, end_time, semester)
        values (?, '데이터크롤링', ?, ?::time, ?::time, 'home-test') returning id""",
        Long.class, roomId, WEDNESDAY, start, end);
  }

  private void prediction(long roomId, LocalDateTime targetAt, Integer co2, Integer humidity, LocalDateTime createdAt) {
    jdbc.update("""
        insert into prediction (room_id, created_at, target_at, co2, temp, humidity, model_version)
        values (?, ?, ?, ?, 23, ?, 'home-test')""", roomId, createdAt, targetAt, co2, humidity);
  }

  private void prediction(long roomId, LocalDateTime targetAt, int co2) {
    prediction(roomId, targetAt, co2, 50, NOW.minusMinutes(5));
  }

  private void reading(long roomId, int co2, int humidity) {
    jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, ?, 24, ?, ?)",
        roomId, co2, humidity, NOW.minusMinutes(1));
  }

  private long action(long roomId, String recommend, String metric, Integer predictedValue, LocalDateTime expectedAt,
      String status, LocalDateTime windowClosedAt) {
    return jdbc.queryForObject("""
        insert into action (room_id, source, recommend, metric, predicted_value, status, expected_at,
                            window_opened_at, window_closed_at, created_at)
        values (?, 'PREDICTION', ?, ?, ?, ?, ?, ?, ?, ?) returning id""", Long.class,
        roomId, recommend, metric, predictedValue, status, expectedAt,
        "IN_PROGRESS".equals(status) ? NOW.minusMinutes(20) : null, windowClosedAt, NOW.minusMinutes(30));
  }

  @Nested
  class MyRooms {

    @Test
    void 담당_구역의_센서_있는_강의실만_건물_층_호수_순() throws Exception {
      jdbc.update("""
          insert into room (building_id, code, number, floor, type, sensor_level)
          values (?, 'hyein-301', '301', 3, 'LECTURE', 'SIM'), (?, 'hyein-302', '302', 3, 'LECTURE', 'NONE')
          on conflict (code) do nothing""", hyein, hyein);
      jdbc.update("update room set sensor_level = 'NONE' where code = 'bukak-621'");
      try {
        as(W1, put("/api/me/zones"), "[{\"buildingId\":" + hyein + ",\"floors\":[3]},"
            + "{\"buildingId\":" + bukak + ",\"floors\":[6]}]").andExpect(status().isOk());

        // 6층 ETC(604~607·612)·화장실·NONE(621) 제외, 북악관(표시 순서 앞) → 혜인관
        as(W1, get("/api/me/rooms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].label", contains(
                "북악관 · 608호", "북악관 · 613호", "북악관 · 614호", "북악관 · 615호", "북악관 · 616호",
                "북악관 · 617호", "북악관 · 618호", "북악관 · 619호", "북악관 · 620호", "혜인관 · 301호")))
            .andExpect(jsonPath("$.data[5].roomId").value(r617))
            .andExpect(jsonPath("$.data[5].buildingId").value(bukak))
            .andExpect(jsonPath("$.data[5].simulated").value(false))
            .andExpect(jsonPath("$.data[6].simulated").value(true));
      } finally {
        jdbc.update("update room set sensor_level = 'SIM' where code = 'bukak-621'");
      }
    }

    @Test
    void 담당_구역이_없으면_빈_배열() throws Exception {
      as(W2, get("/api/me/rooms")).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }
  }

  @Test
  void 안_읽은_알림_수() throws Exception {
    for (String sql : List.of(
        "insert into notification (member_id, type, title, dedup_key, created_at) values (?, 'SENSOR_ERROR', 't', 'a', ?)",
        "insert into notification (member_id, type, title, dedup_key, created_at) values (?, 'SENSOR_ERROR', 't', 'b', ?)")) {
      jdbc.update(sql, jdbc.queryForObject("select id from member where login_id = 'worker01'", Long.class), NOW);
    }
    jdbc.update("""
        insert into notification (member_id, type, title, dedup_key, read_at, created_at)
        values ((select id from member where login_id = 'worker01'), 'SENSOR_ERROR', 't', 'c', ?, ?)""", NOW, NOW);
    jdbc.update("""
        insert into notification (member_id, type, title, dedup_key, created_at)
        values ((select id from member where login_id = 'worker01'), 'SENSOR_ERROR', 't', 'd', ?)""", NOW.minusDays(8));

    as(W1, get("/api/notifications/unread-count")).andExpect(jsonPath("$.data.unreadCount").value(2));
  }

  @Nested
  class Outdoor {

    @Test
    void 저장하고_같은_관측_시각이면_갱신_못_받은_값은_유지() throws Exception {
      when(outdoorApi.nowcast(NOW)).thenReturn(Optional.of(new Nowcast(new BigDecimal("18.0"), 62)));
      when(outdoorApi.pm10()).thenReturn(Optional.of(28));
      when(outdoorApi.forecast(any())).thenReturn(List.of());

      assertThat(outdoorCollector.collect(NOW.plusMinutes(10))).isTrue();
      as(W1, get("/api/outdoor/latest"))
          .andExpect(jsonPath("$.data.temp").value(18.0))
          .andExpect(jsonPath("$.data.humidity").value(62))
          .andExpect(jsonPath("$.data.pm10").value(28))
          .andExpect(jsonPath("$.data.observedAt").value("2026-10-21T10:00:00"));

      when(outdoorApi.nowcast(NOW)).thenReturn(Optional.of(new Nowcast(new BigDecimal("19.5"), 60)));
      when(outdoorApi.pm10()).thenThrow(new IllegalStateException("점검"));
      assertThat(outdoorCollector.collect(NOW.plusMinutes(10))).isTrue();

      assertThat(jdbc.queryForObject("select count(*) from outdoor_observation", Integer.class)).isEqualTo(1);
      as(W1, get("/api/outdoor/latest"))
          .andExpect(jsonPath("$.data.temp").value(19.5))
          .andExpect(jsonPath("$.data.humidity").value(60))
          .andExpect(jsonPath("$.data.pm10").value(28));
    }

    @Test
    void 이번_정시_자료가_아직_없으면_한_시간_전_자료() {
      when(outdoorApi.nowcast(NOW)).thenReturn(Optional.empty());
      when(outdoorApi.nowcast(NOW.minusHours(1))).thenReturn(Optional.of(new Nowcast(new BigDecimal("17.0"), 70)));
      when(outdoorApi.pm10()).thenReturn(Optional.empty());

      assertThat(outdoorCollector.collect(NOW.plusMinutes(10))).isTrue();
      assertThat(jdbc.queryForObject("select observed_at from outdoor_observation", LocalDateTime.class))
          .isEqualTo(NOW.minusHours(1));
    }

    @Test
    void 호출이_모두_실패하면_저장_건너뛰고_null() throws Exception {
      when(outdoorApi.nowcast(any())).thenThrow(new IllegalStateException("timeout"));
      when(outdoorApi.pm10()).thenThrow(new IllegalStateException("timeout"));
      when(outdoorApi.forecast(any())).thenThrow(new IllegalStateException("timeout"));

      assertThat(outdoorCollector.collect(NOW.plusMinutes(10))).isFalse();
      as(W1, get("/api/outdoor/latest")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void 초단기예보는_09시30분_발표분으로_받아_비_예보를_보관() {
      when(outdoorApi.nowcast(any())).thenReturn(Optional.empty());
      when(outdoorApi.pm10()).thenReturn(Optional.empty());
      when(outdoorApi.forecast(NOW.minusMinutes(30))).thenReturn(List.of(
          new ForecastSlot(NOW, 0), new ForecastSlot(NOW.plusHours(1), 1)));

      outdoorCollector.collect(NOW.plusMinutes(10));
      assertThat(rainForecast.firstRainWithin(NOW.plusMinutes(10), java.time.Duration.ofHours(1)))
          .contains(NOW.plusHours(1));
    }
  }

  @Nested
  class NextClass {

    private ResultActions nextClass(long roomId) throws Exception {
      return as(W1, get("/api/rooms/{id}/forecast/next-class", roomId)).andExpect(status().isOk());
    }

    @Test
    void 진행_중_수업_종료_예측만_기준_이상이면_후반에_환기() throws Exception {
      schedule(r617, "09:30", "10:45");
      reading(r617, 800, 50);   // 이미 시작했으니 시작 값은 지금 값
      prediction(r617, NOW.plusMinutes(45), 1280);

      nextClass(r617)
          .andExpect(jsonPath("$.data.courseName").value("데이터크롤링"))
          .andExpect(jsonPath("$.data.start").value("09:30"))
          .andExpect(jsonPath("$.data.end").value("10:45"))
          .andExpect(jsonPath("$.data.co2AtEnd").value(1280))
          .andExpect(jsonPath("$.data.threshold").value(1000))
          .andExpect(jsonPath("$.data.needsVentilation").value(true))
          .andExpect(jsonPath("$.data.headline").value("수업 후반엔 환기가 필요해요"))
          .andExpect(jsonPath("$.data.condition").value("창문을 닫아두면"));
    }

    @Test
    void 진행_중_수업인데_지금_이미_기준_이상이면_수업_전에_환기() throws Exception {
      schedule(r617, "09:30", "10:45");
      reading(r617, 1050, 50);
      prediction(r617, NOW.plusMinutes(45), 1280);
      nextClass(r617).andExpect(jsonPath("$.data.headline").value("수업 전에 환기가 필요해요"));
    }

    @Test
    void 다음_수업_가장_가까운_예측_같은_시각이면_최신_예측_둘_다_기준_미만이면_쾌적() throws Exception {
      schedule(r617, "11:00", "12:15");
      prediction(r617, NOW.plusMinutes(60), 900);
      prediction(r617, NOW.plusMinutes(130), 1500);                                           // 12:10 (5분 차)
      prediction(r617, NOW.plusMinutes(135), 1400, 50, NOW.minusMinutes(20));                 // 12:15 예전 예측
      prediction(r617, NOW.plusMinutes(135), 950, 50, NOW.minusMinutes(5));                   // 12:15 최신 예측

      nextClass(r617)
          .andExpect(jsonPath("$.data.start").value("11:00"))
          .andExpect(jsonPath("$.data.co2AtEnd").value(950))
          .andExpect(jsonPath("$.data.needsVentilation").value(false))
          .andExpect(jsonPath("$.data.headline").value("수업 내내 쾌적할 거예요"));
    }

    @Test
    void 다음_수업_시작_예측이_기준_이상이면_수업_전에_환기() throws Exception {
      schedule(r617, "11:00", "12:15");
      prediction(r617, NOW.plusMinutes(60), 1100);
      prediction(r617, NOW.plusMinutes(135), 1300);
      nextClass(r617).andExpect(jsonPath("$.data.headline").value("수업 전에 환기가 필요해요"));
    }

    @Test
    void 휴강이면_다음_수업() throws Exception {
      long cancelled = schedule(r617, "09:30", "10:45");
      jdbc.update("insert into class_cancellation (class_schedule_id, cancel_date, memo) values (?, ?, 'home-test')",
          cancelled, NOW.toLocalDate());
      schedule(r617, "11:00", "12:15");
      nextClass(r617).andExpect(jsonPath("$.data.start").value("11:00"));
    }

    @Test
    void 예측이_없으면_모으는_중_needsVentilation_null() throws Exception {
      schedule(r617, "11:00", "12:15");
      nextClass(r617)
          .andExpect(jsonPath("$.data.co2AtEnd").value(nullValue()))
          .andExpect(jsonPath("$.data.needsVentilation").value(nullValue()))
          .andExpect(jsonPath("$.data.headline").value("예측 데이터를 모으는 중이에요"));
    }

    @Test
    void 오늘_남은_수업이_없으면_null_없는_방은_404() throws Exception {
      schedule(r617, "08:00", "09:15");   // 이미 끝남
      nextClass(r617).andExpect(jsonPath("$.data").value(nullValue()));
      as(W1, get("/api/rooms/{id}/forecast/next-class", 999_999)).andExpect(status().isNotFound());
    }
  }

  @Nested
  class WindowCards {

    @Test
    void 카드_필드_규칙_expectedAt_오름차순() throws Exception {
      schedule(r608, "10:20", "11:35");   // 608 은 예상 시각(10:30) 전에 수업이 시작
      action(r618, "OPEN", "CO2", 1250, NOW.plusMinutes(5), "PENDING", null);
      action(r608, "OPEN", "CO2", 1100, NOW.plusMinutes(30), "PENDING", null);
      action(r617, "OPEN", "CO2", 1150, NOW.plusMinutes(40), "PENDING", null);
      action(r616, "CLOSE", "HUMIDITY", 64, NOW.plusHours(1), "PENDING", null);

      as(W1, get("/api/actions").param("buildingId", String.valueOf(bukak)))
          .andExpect(jsonPath("$.data[*].number", contains("618", "608", "617", "616")))
          // 10분 이내 → 지금
          .andExpect(jsonPath("$.data[0].timeLabel").value("지금"))
          .andExpect(jsonPath("$.data[0].relative").value(nullValue()))
          .andExpect(jsonPath("$.data[0].title").value("창문 열어주세요"))
          .andExpect(jsonPath("$.data[0].reason").value("5분 뒤 기준 초과 예정"))
          .andExpect(jsonPath("$.data[0].metricLabel").value("CO2"))
          .andExpect(jsonPath("$.data[0].unit").value("ppm"))
          .andExpect(jsonPath("$.data[0].predictedValue").value(1250))
          .andExpect(jsonPath("$.data[0].threshold").value(1000))
          .andExpect(jsonPath("$.data[0].rangeMin").value(nullValue()))
          // 미래 + 그 전에 수업 시작
          .andExpect(jsonPath("$.data[1].timeLabel").value("10:30"))
          .andExpect(jsonPath("$.data[1].relative").value("30분 뒤"))
          .andExpect(jsonPath("$.data[1].title").value("수업 전에 열어두세요"))
          .andExpect(jsonPath("$.data[1].reason").value("30분 뒤 기준 초과 예정"))
          .andExpect(jsonPath("$.data[1].metricLabel").value("CO2 예상"))
          // 미래, 수업 없음
          .andExpect(jsonPath("$.data[2].title").value("창문 열어주세요"))
          // 비 예보 닫기
          .andExpect(jsonPath("$.data[3].timeLabel").value("11:00"))
          .andExpect(jsonPath("$.data[3].relative").value("1시간 뒤"))
          .andExpect(jsonPath("$.data[3].title").value("창문을 닫아주세요"))
          .andExpect(jsonPath("$.data[3].metricLabel").value("습도 예상"))
          .andExpect(jsonPath("$.data[3].unit").value("%"))
          .andExpect(jsonPath("$.data[3].predictedValue").value(64))
          .andExpect(jsonPath("$.data[3].threshold").value(nullValue()))
          .andExpect(jsonPath("$.data[3].rangeMin").value(40))
          .andExpect(jsonPath("$.data[3].rangeMax").value(60));
    }

    @Test
    void 비_예보_닫기_reason() throws Exception {
      jdbc.update("""
          insert into action (room_id, source, recommend, metric, predicted_value, status, expected_at, summary, created_at)
          values (?, 'PREDICTION', 'CLOSE', 'HUMIDITY', 64, 'PENDING', ?, '비 예상 · 창문 닫기', ?)""",
          r616, NOW.plusHours(1), NOW);
      as(W1, get("/api/actions")).andExpect(jsonPath("$.data[0].reason").value("1시간 뒤 비 예상"));
    }

    @Test
    void buildingId_필터는_zone_mine_과_함께_쓸_수_있다() throws Exception {
      long hyeinRoom = jdbc.queryForObject("""
          insert into room (building_id, code, number, floor, type, sensor_level)
          values (?, 'hyein-401', '401', 4, 'LECTURE', 'SIM')
          on conflict (code) do update set floor = 4 returning id""", Long.class, hyein);
      action(r618, "OPEN", "CO2", 1250, NOW.plusMinutes(5), "PENDING", null);
      action(r508(), "OPEN", "CO2", 1250, NOW.plusMinutes(5), "PENDING", null);
      action(hyeinRoom, "OPEN", "CO2", 1250, NOW.plusMinutes(5), "PENDING", null);

      as(W1, get("/api/actions").param("buildingId", String.valueOf(bukak)))
          .andExpect(jsonPath("$.data[*].number", contains("618", "508")));
      as(W1, get("/api/actions").param("buildingId", String.valueOf(hyein)))
          .andExpect(jsonPath("$.data[*].number", contains("401")));
      as(W1, get("/api/actions").param("zone", "mine").param("buildingId", String.valueOf(bukak)))
          .andExpect(jsonPath("$.data[*].number", contains("618")));   // 6층 담당
    }

    private long r508() {
      return roomId("bukak-508");
    }
  }

  @Nested
  class RainClose {

    private List<Map<String, Object>> closeActions() {
      return jdbc.queryForList("""
          select room_id, source, recommend, metric, predicted_value, expected_at, status
          from action where recommend = 'CLOSE' order by id""");
    }

    @Test
    void 창문이_열린_방만_다음_1시간_비_예보면_CLOSE_조치_중복_없음() {
      action(r617, "OPEN", "CO2", 1200, NOW.minusMinutes(20), "IN_PROGRESS", null);               // 창문 열림
      action(r618, "OPEN", "CO2", 1200, NOW.minusMinutes(20), "IN_PROGRESS", NOW.minusMinutes(5)); // 이미 닫음
      action(r608, "OPEN", "CO2", 1200, NOW.plusMinutes(20), "PENDING", null);                     // 아직 안 엶
      prediction(r617, NOW.plusHours(1), 900, 64, NOW.minusMinutes(5));
      rainForecast.update(List.of(new ForecastSlot(NOW, 0), new ForecastSlot(NOW.plusHours(1), 1)), NOW);

      assertThat(rainCloseActionService.createIfRain(NOW)).isEqualTo(1);
      assertThat(rainCloseActionService.createIfRain(NOW.plusMinutes(10))).isZero();   // PENDING CLOSE 가 있으면 안 만듦

      assertThat(closeActions()).singleElement().satisfies(a -> assertThat(a)
          .containsEntry("room_id", r617).containsEntry("source", "PREDICTION").containsEntry("metric", "HUMIDITY")
          .containsEntry("predicted_value", 64).containsEntry("status", "PENDING")
          .containsEntry("expected_at", java.sql.Timestamp.valueOf(NOW.plusHours(1))));
    }

    @Test
    void 습도_예측이_없으면_현재_습도() {
      action(r617, "OPEN", "CO2", 1200, NOW.minusMinutes(20), "IN_PROGRESS", null);
      reading(r617, 900, 58);
      rainForecast.update(List.of(new ForecastSlot(NOW.plusHours(1), 1)), NOW);

      rainCloseActionService.createIfRain(NOW);
      assertThat(closeActions().getFirst()).containsEntry("predicted_value", 58);
    }

    @Test
    void 비_예보가_없거나_1시간_밖이면_안_만듦() {
      action(r617, "OPEN", "CO2", 1200, NOW.minusMinutes(20), "IN_PROGRESS", null);
      rainForecast.update(List.of(new ForecastSlot(NOW, 0), new ForecastSlot(NOW.plusHours(1), 0),
          new ForecastSlot(NOW.plusHours(2), 1)), NOW);
      assertThat(rainCloseActionService.createIfRain(NOW)).isZero();
    }
  }
}
