package com.app.sumpyo_be.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.RoomType;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 PostgreSQL(일회용 컨테이너)에 Flyway V1~V5 를 적용하고 /api/me, /api/buildings, /api/actions 를 호출한다.
 * Docker 가 켜져 있어야 한다.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.demo.password=test-demo-password",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)   // main 엔 시큐리티가 없음. security-test 의존성이 테스트에서만 필터를 켜서 끈다
@Testcontainers
class MeApiIntegrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RoomRepository roomRepository;

  long bukak;    // 북악관 (max 7)
  long hyein;    // 혜인관 (max 5)

  @BeforeEach
  void setUp() throws Exception {
    bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    hyein = jdbc.queryForObject("select id from building where name = '혜인관'", Long.class);
    // 테스트 간 영향 없도록 초기화
    putJson("/api/me/zones", "[]").andExpect(status().isOk());
    putJson("/api/me/settings", """
        {"co2Threshold":1000,"tempMin":20,"tempMax":26,"humidityMin":40,"humidityMax":60,
         "alertEnabled":true,"alertLeadMinutes":15,
         "notifyVentilation":true,"notifyComplaint":true,"notifySensorError":true}""").andExpect(status().isOk());
    jdbc.update("delete from action");
    jdbc.update("delete from sensor_reading");
  }

  private ResultActions putJson(String url, String body) throws Exception {
    return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void 스웨거_문서에_한글_설명이_들어간다() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/actions'].get.summary").value("조치 목록"))
        .andExpect(jsonPath("$.components.schemas.ActionListItem.properties.room.description")
            .value("강의실 이름 (건물 + 호수)"));
  }

  @Test
  void 건물_목록은_화면_순서대로_12개() throws Exception {
    mvc.perform(get("/api/buildings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", hasSize(12)))
        .andExpect(jsonPath("$.data[*].name", contains(
            "대일관", "문예관", "본관", "북악관", "상승관", "유담관",
            "은주1관", "은주2관", "청운관", "한림관", "혜인관", "수인관")))
        .andExpect(jsonPath("$.data[3].maxFloor").value(7))
        .andExpect(jsonPath("$.data[10].maxFloor").value(5));
  }

  @Test
  void 북악관_5층_6층_평면도는_배치_순서대로_17칸() throws Exception {
    mvc.perform(get("/api/buildings/{id}/floors/5", bukak))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rooms", hasSize(17)))
        .andExpect(jsonPath("$.data.rooms[*].number", contains(
            "521", "520", "519", "화장실1", "화장실2", "518", "517", "516",
            "504", "505", "506", "507", "508", "512", "513", "514", "515")))
        .andExpect(jsonPath("$.data.rooms[*].type", contains(
            "LECTURE", "LECTURE", "LECTURE", "RESTROOM", "RESTROOM", "LECTURE", "LECTURE", "LECTURE",
            "ETC", "ETC", "ETC", "ETC", "LECTURE", "ETC", "LECTURE", "LECTURE", "LECTURE")))
        .andExpect(jsonPath("$.data.rooms[*].side", contains(
            "TOP", "TOP", "TOP", "TOP", "TOP", "TOP", "TOP", "TOP",
            "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM", "BOTTOM")))
        .andExpect(jsonPath("$.data.rooms[8].status").value("NO_SENSOR"));

    mvc.perform(get("/api/buildings/{id}/floors/6", bukak))
        .andExpect(jsonPath("$.data.rooms", hasSize(17)))
        .andExpect(jsonPath("$.data.rooms[*].number", contains(
            "621", "620", "619", "화장실1", "화장실2", "618", "617", "616",
            "604", "605", "606", "607", "608", "612", "613", "614", "615")))
        .andExpect(jsonPath("$.data.rooms[8].type").value("ETC"))
        .andExpect(jsonPath("$.data.rooms[13].type").value("ETC"))
        .andExpect(jsonPath("$.data.rooms[13].status").value("NO_SENSOR"));
  }

  @Test
  void 시뮬레이터_대상은_SIM_강의실만() {
    jdbc.update("update room set sensor_level = 'SIM' where code = 'bukak-5F-WC1'");
    try {
      assertThat(roomRepository.findBySensorLevelAndType(SensorLevel.SIM, RoomType.LECTURE))
          .extracting(Room::getCode)
          .contains("bukak-519", "bukak-508", "bukak-618")
          .doesNotContain("bukak-5F-WC1", "bukak-504", "bukak-512", "bukak-604", "bukak-612", "bukak-617");
    } finally {
      jdbc.update("update room set sensor_level = 'NONE' where code = 'bukak-5F-WC1'");
    }
  }

  @Nested
  class Zones {

    @Test
    void 처음엔_빈_배열() throws Exception {
      mvc.perform(get("/api/me/zones")).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void PUT_후_GET_결과가_같다() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[6,5]}]")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].buildingName").value("북악관"))
          .andExpect(jsonPath("$.data[0].floors", contains(5, 6)));

      mvc.perform(get("/api/me/zones"))
          .andExpect(jsonPath("$.data", hasSize(1)))
          .andExpect(jsonPath("$.data[0].buildingId").value(bukak))
          .andExpect(jsonPath("$.data[0].buildingName").value("북악관"))
          .andExpect(jsonPath("$.data[0].floors", contains(5, 6)));
    }

    @Test
    void 두_번_PUT하면_이전_값이_남지_않는다() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
      putJson("/api/me/zones", "[{\"buildingId\":" + hyein + ",\"floors\":[3]}]").andExpect(status().isOk());

      mvc.perform(get("/api/me/zones"))
          .andExpect(jsonPath("$.data", hasSize(1)))
          .andExpect(jsonPath("$.data[0].buildingName").value("혜인관"))
          .andExpect(jsonPath("$.data[0].floors", contains(3)));
    }

    @Test
    void 같은_구역을_다시_보내도_UNIQUE_충돌_없이_교체된다() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
      mvc.perform(get("/api/me/zones")).andExpect(jsonPath("$.data[0].floors", contains(5)));
    }

    @Test
    void 없는_buildingId는_404() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":99999,\"floors\":[1]}]").andExpect(status().isNotFound());
    }

    @Test
    void 범위_밖_층이나_빈_층_목록은_400() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[0]}]").andExpect(status().isBadRequest());
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[8]}]").andExpect(status().isBadRequest());
      putJson("/api/me/zones", "[{\"buildingId\":" + hyein + ",\"floors\":[6]}]").andExpect(status().isBadRequest());
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[]}]").andExpect(status().isBadRequest());
    }

    @Test
    void 같은_건물_중복은_400_그리고_실패하면_기존_값_유지() throws Exception {
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
      putJson("/api/me/zones", "[{\"buildingId\":" + hyein + ",\"floors\":[1]},{\"buildingId\":" + hyein
          + ",\"floors\":[2]}]").andExpect(status().isBadRequest());

      mvc.perform(get("/api/me/zones")).andExpect(jsonPath("$.data[0].buildingName").value("북악관"));
    }
  }

  @Nested
  class Settings {

    @Test
    void 기본값과_cautionFrom_criticalFrom_계산값() throws Exception {
      mvc.perform(get("/api/me/settings"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.co2Threshold").value(1000))
          .andExpect(jsonPath("$.data.cautionFrom").value(900))
          .andExpect(jsonPath("$.data.criticalFrom").value(1200))
          .andExpect(jsonPath("$.data.alertEnabled").value(true))
          .andExpect(jsonPath("$.data.alertLeadMinutes").value(15))
          .andExpect(jsonPath("$.data.notifyVentilation").value(true))
          .andExpect(jsonPath("$.data.notifyComplaint").value(true))
          .andExpect(jsonPath("$.data.notifySensorError").value(true));
    }

    @Test
    void 알림_종류별_on_off는_보낸_것만_바뀐다() throws Exception {
      putJson("/api/me/settings", "{\"notifySensorError\":false}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.notifySensorError").value(false))
          .andExpect(jsonPath("$.data.notifyVentilation").value(true))
          .andExpect(jsonPath("$.data.notifyComplaint").value(true))
          .andExpect(jsonPath("$.data.alertEnabled").value(true));
      putJson("/api/me/settings", "{\"notifyVentilation\":false,\"notifyComplaint\":false}").andExpect(status().isOk());

      mvc.perform(get("/api/me/settings"))
          .andExpect(jsonPath("$.data.notifyVentilation").value(false))
          .andExpect(jsonPath("$.data.notifyComplaint").value(false))
          .andExpect(jsonPath("$.data.notifySensorError").value(false))
          .andExpect(jsonPath("$.data.co2Threshold").value(1000));
    }

    @Test
    void 기준을_바꾸면_계산값도_바뀌고_보낸_계산값은_무시된다() throws Exception {
      putJson("/api/me/settings", "{\"co2Threshold\":1100,\"cautionFrom\":1,\"criticalFrom\":2}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.co2Threshold").value(1100))
          .andExpect(jsonPath("$.data.cautionFrom").value(990))
          .andExpect(jsonPath("$.data.criticalFrom").value(1320));
    }

    @Test
    void 보낸_값만_바뀐다_온보딩의_alertEnabled만_보내기() throws Exception {
      putJson("/api/me/settings", "{\"alertEnabled\":false,\"tempMax\":25}").andExpect(status().isOk());
      putJson("/api/me/settings", "{\"alertEnabled\":true}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.alertEnabled").value(true))
          .andExpect(jsonPath("$.data.tempMax").value(25))
          .andExpect(jsonPath("$.data.co2Threshold").value(1000));
    }

    @Test
    void 검증_실패는_400이고_값은_바뀌지_않는다() throws Exception {
      putJson("/api/me/settings", "{\"co2Threshold\":599}").andExpect(status().isBadRequest());
      putJson("/api/me/settings", "{\"co2Threshold\":1501}").andExpect(status().isBadRequest());
      putJson("/api/me/settings", "{\"alertLeadMinutes\":12}").andExpect(status().isBadRequest());
      putJson("/api/me/settings", "{\"tempMin\":26}").andExpect(status().isBadRequest());          // 26 < 26 아님
      putJson("/api/me/settings", "{\"humidityMin\":70,\"humidityMax\":60}").andExpect(status().isBadRequest());

      mvc.perform(get("/api/me/settings"))
          .andExpect(jsonPath("$.data.co2Threshold").value(1000))
          .andExpect(jsonPath("$.data.tempMin").value(20))
          .andExpect(jsonPath("$.data.alertLeadMinutes").value(15));
    }

    @Test
    void 경계값_600_1500과_알림_10_20분은_허용() throws Exception {
      putJson("/api/me/settings", "{\"co2Threshold\":600,\"alertLeadMinutes\":10}").andExpect(status().isOk());
      putJson("/api/me/settings", "{\"co2Threshold\":1500,\"alertLeadMinutes\":20}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.criticalFrom").value(1800));
    }
  }

  @Nested
  class ActionsInMyZone {

    /** expected_at = 지금 + minutesLater (null 이면 없음). 앱 Clock 과 같은 KST 로 넣는다 */
    private void insertAction(String roomCode, String source, String recommend, Integer minutesLater) {
      LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
      jdbc.update("""
          insert into action (room_id, source, recommend, metric, predicted_value, threshold, status,
                              expected_at, created_at)
          values ((select id from room where code = ?), ?, ?, 'CO2', 1250, 1000, 'PENDING', ?, ?)""",
          roomCode, source, recommend, minutesLater == null ? null : now.plusMinutes(minutesLater), now);
    }

    private void insertAction(String roomCode) {
      insertAction(roomCode, "PREDICTION", "OPEN", 20);
    }

    @Test
    void zone_mine은_담당_건물_층의_방만() throws Exception {
      insertAction("bukak-617");
      insertAction("bukak-508");
      putJson("/api/me/zones", "[{\"buildingId\":" + bukak + ",\"floors\":[6]}]").andExpect(status().isOk());

      mvc.perform(get("/api/actions").param("zone", "mine"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data", hasSize(1)))
          .andExpect(jsonPath("$.data[0].room").value("북악관 617호"));

      mvc.perform(get("/api/actions")).andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void 명세_형식과_expectedAt_오름차순() throws Exception {
      insertAction("bukak-617", "PREDICTION", "OPEN", 45);
      insertAction("bukak-618", "PREDICTION", "CLOSE", -5);
      insertAction("bukak-608", "PREDICTION", "OPEN", 20);

      mvc.perform(get("/api/actions"))
          .andExpect(jsonPath("$.data[*].room", contains("북악관 618호", "북악관 608호", "북악관 617호")))
          .andExpect(jsonPath("$.data[0].timing").value("NOW"))
          .andExpect(jsonPath("$.data[0].title").value("창문 닫아주세요"))
          .andExpect(jsonPath("$.data[0].reason").value("기준 초과"))
          .andExpect(jsonPath("$.data[1].timing").value("SOON"))
          .andExpect(jsonPath("$.data[1].title").value("창문 열어주세요"))
          .andExpect(jsonPath("$.data[1].reason").value(matchesPattern("(19|20)분 뒤 기준 초과 예정")))
          .andExpect(jsonPath("$.data[2].timing").value("LATER"))
          .andExpect(jsonPath("$.data[2].source").value("PREDICTION"))
          .andExpect(jsonPath("$.data[2].recommend").value("OPEN"))
          .andExpect(jsonPath("$.data[2].metric").value("CO2"))
          .andExpect(jsonPath("$.data[2].predictedValue").value(1250))
          .andExpect(jsonPath("$.data[2].threshold").value(1000))
          .andExpect(jsonPath("$.data[2].status").value("PENDING"))
          .andExpect(jsonPath("$.data[2].actionId").isNumber())
          .andExpect(jsonPath("$.data[2].roomId").isNumber())
          .andExpect(jsonPath("$.data[2].expectedAt").isString());
    }

    @Test
    void 민원_조치는_학생_민원_접수() throws Exception {
      insertAction("bukak-617", "COMPLAINT", "OPEN", null);
      mvc.perform(get("/api/actions"))
          .andExpect(jsonPath("$.data[0].reason").value("학생 민원 접수"))
          .andExpect(jsonPath("$.data[0].timing").value("NOW"));
    }

    @Test
    void 담당_구역이_없으면_빈_목록() throws Exception {
      insertAction("bukak-617");
      mvc.perform(get("/api/actions").param("zone", "mine")).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 상태_필터와_잘못된_zone() throws Exception {
      insertAction("bukak-617");
      mvc.perform(get("/api/actions").param("status", "DONE")).andExpect(jsonPath("$.data", hasSize(0)));
      mvc.perform(get("/api/actions").param("status", "PENDING,CHECKED")).andExpect(jsonPath("$.data", hasSize(1)));
      mvc.perform(get("/api/actions").param("zone", "all")).andExpect(status().isBadRequest());
    }
  }

  /** POST /api/actions/{id}/complete 의 goalReached 기준 */
  @Nested
  class CompleteGoalReached {

    /** worker01 이 환기를 시작해 둔(IN_PROGRESS) 조치. expectedCo2After 가 null 이면 예측 조치 */
    private long inProgressAction(String roomCode, String source, Integer expectedCo2After) {
      LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
      return jdbc.queryForObject("""
          insert into action (room_id, source, recommend, status, co2_before, expected_co2_after,
                              member_id, window_opened_at, created_at)
          values ((select id from room where code = ?), ?, 'OPEN', 'IN_PROGRESS', 1250, ?,
                  (select id from member where login_id = 'worker01'), ?, ?)
          returning id""", Long.class, roomCode, source, expectedCo2After, now.minusMinutes(10), now);
    }

    /** 최근 10분 안의 측정값 = 완료 시점 CO2 */
    private void reading(String roomCode, int co2) {
      jdbc.update("""
          insert into sensor_reading (room_id, co2, temp, humidity, measured_at)
          values ((select id from room where code = ?), ?, 24, 45, ?)""",
          roomCode, co2, LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1));
    }

    private ResultActions complete(long actionId) throws Exception {
      return mvc.perform(post("/api/actions/{id}/complete", actionId)).andExpect(status().isOk());
    }

    @Test
    void 민원_AI_추천_조치는_실제_CO2가_예상_CO2_이하면_달성() throws Exception {
      // 기준값(1000) 미만이어도 예상값보다 높으면 미달성 → 예상값 기준임을 확인
      long equal = inProgressAction("bukak-617", "COMPLAINT", 780);
      long above = inProgressAction("bukak-618", "COMPLAINT", 700);
      reading("bukak-617", 780);
      reading("bukak-618", 780);

      complete(equal)
          .andExpect(jsonPath("$.data.co2Before").value(1250))
          .andExpect(jsonPath("$.data.actualCo2After").value(780))
          .andExpect(jsonPath("$.data.goalReached").value(true))
          .andExpect(jsonPath("$.data.status").value("DONE"));
      complete(above).andExpect(jsonPath("$.data.goalReached").value(false));
    }

    @Test
    void 예측_조치는_실제_CO2가_처리자_기준값_미만이면_달성() throws Exception {
      // 처리자(worker01) 기준값을 800 으로 → 기본 1000 이 아니라 처리자 기준값을 쓰는지 확인
      putJson("/api/me/settings", "{\"co2Threshold\":800}").andExpect(status().isOk());
      long below = inProgressAction("bukak-617", "PREDICTION", null);
      long over = inProgressAction("bukak-618", "PREDICTION", null);
      reading("bukak-617", 799);
      reading("bukak-618", 800);

      complete(below).andExpect(jsonPath("$.data.goalReached").value(true));
      complete(over).andExpect(jsonPath("$.data.goalReached").value(false));
    }

    @Test
    void 센서가_끊겨_실제_CO2가_없으면_goalReached는_null() throws Exception {
      long id = inProgressAction("bukak-617", "PREDICTION", null);

      complete(id)
          .andExpect(jsonPath("$.data.actualCo2After").value(nullValue()))
          .andExpect(jsonPath("$.data.goalReached").value(nullValue()))
          .andExpect(jsonPath("$.data.status").value("DONE"));
    }
  }
}
