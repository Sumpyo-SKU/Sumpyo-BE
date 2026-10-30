package com.app.sumpyo_be.domain.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 학생 민원(QR) 공개 API. 실제 PostgreSQL(일회용 컨테이너)에 Flyway 를 적용하고 호출한다.
 * Docker 가 켜져 있어야 한다.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    // 로컬 .env 에 공공데이터 키가 있어도 외부 API 를 부르지 않도록
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.expire-enabled=false", "sumpyo.action.auto-complete-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.complaint.salt=test-salt",
    "cors.allowed-origins=https://sumpyo.example",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)   // main 엔 시큐리티가 없음. security-test 의존성이 테스트에서만 필터를 켜서 끈다
@Testcontainers
class ComplaintApiIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String CLIENT_ID = "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b";

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  long bukak;
  long room618;
  long room617;

  @BeforeEach
  void setUp() {
    jdbc.update("update action set complaint_id = null");
    jdbc.update("delete from notification");   // 민원 접수 때 생긴 알림 (complaint FK)
    jdbc.update("delete from complaint");
    jdbc.update("delete from sensor_reading");
    bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    room618 = roomId("bukak-618");
    room617 = roomId("bukak-617");
  }

  private long roomId(String code) {
    return jdbc.queryForObject("select id from room where code = ?", Long.class, code);
  }

  private ResultActions submit(String body) throws Exception {
    return mvc.perform(post("/api/complaints").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String body(long roomId, String categories, String content, String clientId) {
    return "{\"roomId\":" + roomId + ",\"categories\":" + categories
        + (content == null ? "" : ",\"content\":" + content)
        + (clientId == null ? "" : ",\"clientId\":\"" + clientId + "\"") + "}";
  }

  private void reading(long roomId, int co2, int temp, int humidity, int minutesAgo) {
    jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, ?, ?, ?, ?)",
        roomId, co2, temp, humidity, LocalDateTime.now(KST).minusMinutes(minutesAgo));
  }

  @Nested
  class Submit {

    @Test
    void 정상_제출하면_200이고_최근_측정값이_스냅샷으로_저장된다() throws Exception {
      reading(room618, 1180, 27, 72, 2);

      submit(body(room618, "[\"HUMID\",\"HOT\",\"HUMID\"]", "\"  5교시만 되면 너무 습해요  \"", CLIENT_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.complaintId").isNumber())
          .andExpect(jsonPath("$.data.room").value("북악관 618호"))
          .andExpect(jsonPath("$.data.categories", contains("HUMID", "HOT")))
          .andExpect(jsonPath("$.data.categoryLabels", contains("습해요", "더워요")));

      Map<String, Object> row = jdbc.queryForMap("select * from complaint");
      assertThat(row.get("status")).isEqualTo("RECEIVED");
      assertThat(row.get("categories")).isEqualTo("HUMID,HOT");
      assertThat(row.get("content")).isEqualTo("5교시만 되면 너무 습해요");
      assertThat(row.get("co2_snapshot")).isEqualTo(1180);
      assertThat((BigDecimal) row.get("temp_snapshot")).isEqualByComparingTo("27");
      assertThat(row.get("humidity_snapshot")).isEqualTo(72);
    }

    @Test
    void 최근_10분_측정이_없으면_스냅샷은_null_공백뿐인_content도_null() throws Exception {
      reading(room618, 1180, 27, 72, 15);

      submit(body(room618, "[\"STUFFY\"]", "\"   \"", CLIENT_ID)).andExpect(status().isOk());

      Map<String, Object> row = jdbc.queryForMap("select * from complaint");
      assertThat(row.get("co2_snapshot")).isNull();
      assertThat(row.get("temp_snapshot")).isNull();
      assertThat(row.get("humidity_snapshot")).isNull();
      assertThat(row.get("content")).isNull();
    }

    @Test
    void 원래_clientId는_DB에_저장되지_않고_SHA256_해시만_남는다() throws Exception {
      submit(body(room618, "[\"SMELL\"]", null, CLIENT_ID)).andExpect(status().isOk());

      String hash = jdbc.queryForObject("select client_hash from complaint", String.class);
      assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(CLIENT_ID);
      List<String> everyColumn = jdbc.queryForList("select row_to_json(c)::text from complaint c", String.class);
      assertThat(everyColumn).singleElement().asString().doesNotContain(CLIENT_ID);
    }
  }

  @Nested
  class Validation {

    @Test
    void 카테고리가_0개면_400() throws Exception {
      submit(body(room618, "[]", null, CLIENT_ID)).andExpect(status().isBadRequest());
      submit("{\"roomId\":" + room618 + ",\"clientId\":\"" + CLIENT_ID + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 없는_카테고리_값이면_400() throws Exception {
      submit(body(room618, "[\"WINDY\"]", null, CLIENT_ID)).andExpect(status().isBadRequest());
      submit(body(room618, "[null]", null, CLIENT_ID)).andExpect(status().isBadRequest());
    }

    @Test
    void content가_500자를_넘으면_400_공백_제거_후_500자는_허용() throws Exception {
      submit(body(room618, "[\"HOT\"]", "\"" + "가".repeat(501) + "\"", CLIENT_ID))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("민원 상세는 500자까지 쓸 수 있어요"));
      submit(body(room618, "[\"HOT\"]", "\"  " + "가".repeat(500) + "  \"", CLIENT_ID))
          .andExpect(status().isOk());
    }

    @Test
    void clientId가_없거나_비어_있으면_400() throws Exception {
      submit(body(room618, "[\"HOT\"]", null, null)).andExpect(status().isBadRequest());
      submit(body(room618, "[\"HOT\"]", null, "  ")).andExpect(status().isBadRequest());
      assertThat(jdbc.queryForObject("select count(*) from complaint", Integer.class)).isZero();
    }

    @Test
    void 없는_roomId면_404() throws Exception {
      submit(body(999_999, "[\"HOT\"]", null, CLIENT_ID))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("호실을 찾을 수 없어요"));
    }
  }

  @Nested
  class Resubmit {

    @Test
    void 같은_clientId_같은_방_10분_안에_다시_내면_429_다른_방은_허용() throws Exception {
      submit(body(room618, "[\"HOT\"]", null, CLIENT_ID)).andExpect(status().isOk());

      submit(body(room618, "[\"COLD\"]", null, CLIENT_ID))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.message").value("잠시 후 다시 알려주세요"));
      submit(body(room617, "[\"COLD\"]", null, CLIENT_ID)).andExpect(status().isOk());
      submit(body(room618, "[\"COLD\"]", null, "other-client")).andExpect(status().isOk());
    }

    @Test
    void 십분이_지나면_같은_방에_다시_낼_수_있다() throws Exception {
      submit(body(room618, "[\"HOT\"]", null, CLIENT_ID)).andExpect(status().isOk());
      jdbc.update("update complaint set created_at = created_at - interval '11 minutes'");

      submit(body(room618, "[\"HOT\"]", null, CLIENT_ID)).andExpect(status().isOk());
    }
  }

  @Nested
  class Lookup {

    @Test
    void code로_찾기() throws Exception {
      mvc.perform(get("/api/rooms/lookup").param("code", "bukak-618"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.roomId").value(room618))
          .andExpect(jsonPath("$.data.building").value("북악관"))
          .andExpect(jsonPath("$.data.number").value("618"));
    }

    @Test
    void buildingId와_number로_찾기_공백과_호는_무시() throws Exception {
      for (String number : List.of("618", " 618 ", "618호", "618 호")) {
        mvc.perform(get("/api/rooms/lookup").param("buildingId", String.valueOf(bukak)).param("number", number))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId").value(room618));
      }
    }

    @Test
    void 없는_방이나_강의실_데이터가_없는_건물은_404() throws Exception {
      long hyein = jdbc.queryForObject("select id from building where name = '혜인관'", Long.class);
      mvc.perform(get("/api/rooms/lookup").param("buildingId", String.valueOf(bukak)).param("number", "999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("호실을 찾을 수 없어요"));
      mvc.perform(get("/api/rooms/lookup").param("buildingId", String.valueOf(hyein)).param("number", "301"))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/rooms/lookup").param("code", "nope-1")).andExpect(status().isNotFound());
    }

    @Test
    void 조건이_없으면_400() throws Exception {
      mvc.perform(get("/api/rooms/lookup")).andExpect(status().isBadRequest());
      mvc.perform(get("/api/rooms/lookup").param("buildingId", String.valueOf(bukak)).param("number", " 호 "))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class Public {

    @Test
    void 건물_목록은_인증_없이_조회된다() throws Exception {
      mvc.perform(get("/api/buildings")).andExpect(status().isOk());
    }

    @Test
    void 설정된_프론트_도메인에서_CORS_사전요청이_허용된다() throws Exception {
      mvc.perform(options("/api/complaints")
              .header(HttpHeaders.ORIGIN, "https://sumpyo.example")
              .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
              .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://sumpyo.example"));
    }

    @Test
    void 설정에_없는_도메인은_CORS_거부() throws Exception {
      mvc.perform(options("/api/complaints")
              .header(HttpHeaders.ORIGIN, "https://evil.example")
              .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
          .andExpect(status().isForbidden());
    }
  }
}
