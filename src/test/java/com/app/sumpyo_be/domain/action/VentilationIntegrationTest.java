package com.app.sumpyo_be.domain.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.domain.action.service.ActionAutoCompleteService;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 강의실 환기 상태 (co2Status · overThreshold · minutesToExceed · ventilateInMinutes · ventilatingAction),
 * [환기 하기] → [환기 완료], 자동 완료.
 * 시각은 2026-10-21 (수) 14:00 KST 고정, 스케줄러는 끄고 직접 호출한다.
 * worker01(W1): 기준 CO2 1000, 미리 알림 10분. 강의실은 북악관 617 (FULL).
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.expire-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.action.measure-enabled=false", "sumpyo.action.auto-complete-enabled=false",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
@Import(VentilationIntegrationTest.FixedClock.class)   // @Nested 테스트에도 고정 시각이 적용되도록 명시
class VentilationIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  /** 2026-10-21 (수) 14:00 KST */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);
  private static final String W1 = "worker01";

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

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ActionAutoCompleteService autoCompleteService;

  long r617;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.update("delete from notification");
    jdbc.update("delete from action");
    jdbc.update("delete from complaint");
    jdbc.update("delete from sensor_reading");
    jdbc.update("delete from prediction");
    jdbc.update("delete from class_cancellation");
    jdbc.update("delete from class_schedule");
    r617 = jdbc.queryForObject("select id from room where code = 'bukak-617'", Long.class);
    as(W1, put("/api/me/settings"), """
        {"co2Threshold":1000,"tempMin":20,"tempMax":26,"humidityMin":40,"humidityMax":60,"alertLeadMinutes":10}""")
        .andExpect(status().isOk());
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

  private ResultActions room() throws Exception {
    return as(W1, get("/api/rooms/{id}", r617)).andExpect(status().isOk());
  }

  private ResultActions ventilate() throws Exception {
    return as(W1, post("/api/actions/ventilate"), "{\"roomId\":" + r617 + "}");
  }

  private void reading(int co2, LocalDateTime at) {
    jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, ?, 24, 45, ?)",
        r617, co2, at);
  }

  private void prediction(LocalDateTime targetAt, int co2) {
    jdbc.update("""
        insert into prediction (room_id, created_at, target_at, co2, temp, humidity, model_version)
        values (?, ?, ?, ?, 24, 45, 'ventilation-test')""", r617, NOW.minusMinutes(5), targetAt, co2);
  }

  private long pendingPredictionAction() {
    return jdbc.queryForObject("""
        insert into action (room_id, source, recommend, metric, status, expected_at, created_at)
        values (?, 'PREDICTION', 'OPEN', 'CO2', 'PENDING', ?, ?) returning id""", Long.class,
        r617, NOW.plusMinutes(20), NOW.minusMinutes(10));
  }

  private static long actionId(ResultActions res) throws Exception {
    return ((Number) JsonPath.read(res.andReturn().getResponse().getContentAsString(), "$.data.actionId")).longValue();
  }

  private long actionCount() {
    return jdbc.queryForObject("select count(*) from action where room_id = ?", Long.class, r617);
  }

  @Nested
  class Co2Status {

    @Test
    void CRITICAL_은_OVER_와_현재_CO2_빼기_기준() throws Exception {
      reading(1150, NOW.minusMinutes(1));
      room()
          .andExpect(jsonPath("$.data.co2Status").value("OVER"))
          .andExpect(jsonPath("$.data.overThreshold").value(150))
          .andExpect(jsonPath("$.data.minutesToExceed").value(nullValue()))
          .andExpect(jsonPath("$.data.ventilateInMinutes").value(nullValue()))
          .andExpect(jsonPath("$.data.ventilatingAction").value(nullValue()));
    }

    @Test
    void WARNING_미리_알림_10분_초과까지_20분이면_10분_안에_환기() throws Exception {
      reading(920, NOW.minusMinutes(1));
      prediction(NOW.plusMinutes(10), 980);
      prediction(NOW.plusMinutes(20), 1010);
      prediction(NOW.plusMinutes(25), 1100);
      room()
          .andExpect(jsonPath("$.data.co2Status").value("PREDICTED"))
          .andExpect(jsonPath("$.data.overThreshold").value(nullValue()))
          .andExpect(jsonPath("$.data.minutesToExceed").value(20))
          .andExpect(jsonPath("$.data.ventilateInMinutes").value(10));
    }

    @Test
    void NORMAL_은_셋_다_null() throws Exception {
      reading(650, NOW.minusMinutes(1));
      prediction(NOW.plusMinutes(20), 700);
      room()
          .andExpect(jsonPath("$.data.co2Status").value("NORMAL"))
          .andExpect(jsonPath("$.data.overThreshold").value(nullValue()))
          .andExpect(jsonPath("$.data.minutesToExceed").value(nullValue()))
          .andExpect(jsonPath("$.data.ventilateInMinutes").value(nullValue()))
          .andExpect(jsonPath("$.data.ventilatingAction").value(nullValue()));
    }

    @Test
    void 환기_중이면_ventilatingAction_에_조치와_시작_시각() throws Exception {
      reading(1150, NOW.minusMinutes(1));
      long actionId = actionId(ventilate().andExpect(status().isOk()));
      room()
          .andExpect(jsonPath("$.data.co2Status").value("OVER"))
          .andExpect(jsonPath("$.data.ventilating").value(true))
          .andExpect(jsonPath("$.data.ventilatingAction.actionId").value(actionId))
          .andExpect(jsonPath("$.data.ventilatingAction.startedAt").value("2026-10-21T14:00:00"));
    }
  }

  @Nested
  class Ventilate {

    @Test
    void 정상_상태에서_환기_하기_후_환기_완료() throws Exception {
      reading(650, NOW.minusMinutes(1));
      long actionId = actionId(ventilate()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.source").value("MANUAL"))
          .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
          .andExpect(jsonPath("$.data.co2Before").value(650))
          .andExpect(jsonPath("$.data.startedAt").value("2026-10-21T14:00:00")));
      room().andExpect(jsonPath("$.data.ventilatingAction.actionId").value(actionId));

      reading(600, NOW);
      as(W1, post("/api/actions/{id}/complete", actionId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("DONE"))
          .andExpect(jsonPath("$.data.actualCo2After").value(600));

      Map<String, Object> row = jdbc.queryForMap(
          "select status, actual_co2_after, completed_at, auto_completed, effective from action where id = ?", actionId);
      assertThat(row.get("status")).isEqualTo("DONE");
      assertThat(row.get("actual_co2_after")).isEqualTo(600);
      assertThat(row.get("completed_at")).isNotNull();
      assertThat(row.get("auto_completed")).isEqualTo(false);
      assertThat(row.get("effective")).isEqualTo(true);
      room().andExpect(jsonPath("$.data.ventilatingAction").value(nullValue()));
    }

    @Test
    void 이미_환기_중이면_다시_눌러도_새로_만들지_않고_기존_조치() throws Exception {
      reading(1150, NOW.minusMinutes(1));
      long first = actionId(ventilate().andExpect(status().isOk()));
      long second = actionId(ventilate()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("IN_PROGRESS")));
      assertThat(second).isEqualTo(first);
      assertThat(actionCount()).isEqualTo(1);
    }

    @Test
    void 대기_중인_추천_조치가_있으면_그_조치를_시작() throws Exception {
      reading(920, NOW.minusMinutes(1));
      long pending = pendingPredictionAction();
      ventilate()
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.actionId").value(pending))
          .andExpect(jsonPath("$.data.source").value("PREDICTION"))
          .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
      assertThat(actionCount()).isEqualTo(1);
    }

    @Test
    void 없는_강의실은_404() throws Exception {
      as(W1, post("/api/actions/ventilate"), "{\"roomId\":999999}").andExpect(status().isNotFound());
    }

    @Test
    void roomId_가_없으면_400() throws Exception {
      as(W1, post("/api/actions/ventilate"), "{}").andExpect(status().isBadRequest());
    }
  }

  @Nested
  class AutoComplete {

    @Test
    void CO2_가_800_이하로_떨어지면_자동_완료() throws Exception {
      reading(1150, NOW.minusMinutes(1));
      long actionId = actionId(ventilate().andExpect(status().isOk()));
      reading(780, NOW);

      assertThat(autoCompleteService.completeDue(NOW)).isEqualTo(1);
      Map<String, Object> row = jdbc.queryForMap(
          "select status, actual_co2_after, auto_completed, effective from action where id = ?", actionId);
      assertThat(row.get("status")).isEqualTo("DONE");
      assertThat(row.get("actual_co2_after")).isEqualTo(780);
      assertThat(row.get("auto_completed")).isEqualTo(true);
      assertThat(row.get("effective")).isEqualTo(true);
    }

    @Test
    void 시작_후_60분이_지나면_자동_완료() throws Exception {
      reading(1150, NOW.minusMinutes(1));
      long actionId = actionId(ventilate().andExpect(status().isOk()));
      reading(1100, NOW.plusMinutes(59));

      assertThat(autoCompleteService.completeDue(NOW.plusMinutes(59))).isZero();
      assertThat(autoCompleteService.completeDue(NOW.plusMinutes(60))).isEqualTo(1);
      Map<String, Object> row = jdbc.queryForMap(
          "select status, actual_co2_after, auto_completed from action where id = ?", actionId);
      assertThat(row.get("status")).isEqualTo("DONE");
      assertThat(row.get("actual_co2_after")).isEqualTo(1100);
      assertThat(row.get("auto_completed")).isEqualTo(true);
    }

    @Test
    void 정상_상태에서_시작한_환기는_CO2_로는_바로_끝내지_않는다() throws Exception {
      reading(650, NOW.minusMinutes(1));
      ventilate().andExpect(status().isOk());
      reading(640, NOW);
      assertThat(autoCompleteService.completeDue(NOW)).isZero();
    }
  }
}
