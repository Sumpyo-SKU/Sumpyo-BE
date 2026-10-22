package com.app.sumpyo_be.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.domain.notification.service.NotificationScanService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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
 * 환기 알림함 + 민원 QR. 실제 PostgreSQL(일회용 컨테이너)에 Flyway 를 적용하고 호출한다. Docker 가 켜져 있어야 한다.
 * 시각은 2026-10-21(수) 14:00 KST 고정, 스캔은 스케줄러를 끄고 scan(NOW) 를 직접 부른다.
 * 북악관 6층: 617호 FULL(실측), 나머지 강의실 SIM, 화장실 NONE.
 * 계정: worker01(W1) 북악관 6층 담당, worker02(W2) 북악관 5층 담당 (6층 알림을 받으면 안 됨).
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    // 로컬 .env 에 공공데이터 키가 있어도 외부 API 를 부르지 않도록
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.complaint.salt=test-salt",
    "sumpyo.complaint.front-base-url=https://front.test/",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)   // main 엔 시큐리티가 없음. security-test 의존성이 테스트에서만 필터를 켜서 끈다
@Testcontainers
@Import(NotificationApiIntegrationTest.FixedClock.class)   // @Nested 테스트에도 고정 시각이 적용되도록 명시
class NotificationApiIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  /** 2026-10-21 (수) 14:00 KST, ISO 주 2026-W43 */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);
  private static final String W1 = "worker01";
  private static final String W2 = "worker02";
  private static final String ALL_ON = """
      {"co2Threshold":1000,"alertEnabled":true,"alertLeadMinutes":15,
       "notifyVentilation":true,"notifyComplaint":true,"notifySensorError":true}""";

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
  @Autowired NotificationScanService scanService;

  long bukak;
  long r617;   // FULL
  long r618;   // SIM
  long wc6;    // 화장실

  @BeforeEach
  void setUp() throws Exception {
    jdbc.update("delete from notification");
    jdbc.update("delete from action");
    jdbc.update("delete from complaint");
    jdbc.update("delete from sensor_reading");
    bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
    r617 = roomId("bukak-617");
    r618 = roomId("bukak-618");
    wc6 = roomId("bukak-6F-WC1");
    as(W1, put("/api/me/zones"), "[{\"buildingId\":" + bukak + ",\"floors\":[6]}]").andExpect(status().isOk());
    as(W2, put("/api/me/zones"), "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
    settings(W1, ALL_ON);
    settings(W2, ALL_ON);
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

  private void settings(String loginId, String json) throws Exception {
    as(loginId, put("/api/me/settings"), json).andExpect(status().isOk());
  }

  private void reading(long roomId, int co2, LocalDateTime at) {
    jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, ?, 24, 45, ?)",
        roomId, co2, at);
  }

  /** 예측 조치 (PENDING) */
  private long predictionAction(long roomId, int predictedValue, LocalDateTime expectedAt) {
    return jdbc.queryForObject("""
        insert into action (room_id, source, recommend, metric, predicted_value, threshold, status, expected_at, created_at)
        values (?, 'PREDICTION', 'OPEN', 'CO2', ?, 1000, 'PENDING', ?, ?) returning id""",
        Long.class, roomId, predictedValue, expectedAt, NOW.minusMinutes(1));
  }

  private List<Map<String, Object>> notifications(String loginId) {
    return jdbc.queryForList("""
        select n.type, n.title, n.body, n.room_id, n.action_id, n.complaint_id
        from notification n join member m on m.id = n.member_id
        where m.login_id = ? order by n.id""", loginId);
  }

  private void complain(long roomId, String clientId, String categories) throws Exception {
    mvc.perform(post("/api/complaints").contentType(MediaType.APPLICATION_JSON).content(
            "{\"roomId\":" + roomId + ",\"categories\":" + categories + ",\"clientId\":\"" + clientId + "\"}"))
        .andExpect(status().isOk());
  }

  @Nested
  class VentilationNow {

    @Test
    void 기준_이상이면_담당_근로생만_받고_문구는_천_단위_쉼표() {
      reading(r618, 1180, NOW.minusMinutes(1));
      long actionId = predictionAction(r618, 1250, NOW.minusMinutes(5));   // 이미 지난 예측 = 대기 중 조치

      assertThat(scanService.scan(NOW)).isEqualTo(1);

      List<Map<String, Object>> w1 = notifications(W1);
      assertThat(w1).hasSize(1);
      assertThat(w1.getFirst()).containsEntry("type", "VENTILATION_NOW").containsEntry("title", "618호 환기 필요")
          .containsEntry("body", "지금 1,180ppm").containsEntry("room_id", r618).containsEntry("action_id", actionId);
      assertThat(notifications(W2)).isEmpty();   // 5층 담당
    }

    @Test
    void 같은_스캔을_두_번_돌려도_중복이_없다() {
      reading(r618, 1180, NOW.minusMinutes(1));
      scanService.scan(NOW);

      assertThat(scanService.scan(NOW)).isZero();
      assertThat(scanService.scan(NOW.plusMinutes(1))).isZero();
      assertThat(notifications(W1)).hasSize(1);
    }

    @Test
    void 기준_미만이거나_내_기준보다_낮으면_없다() throws Exception {
      reading(r618, 990, NOW.minusMinutes(1));
      assertThat(scanService.scan(NOW)).isZero();

      settings(W1, "{\"co2Threshold\":1200}");
      reading(r618, 1180, NOW.minusSeconds(30));
      assertThat(scanService.scan(NOW)).isZero();
    }

    @Test
    void notifyVentilation_이_꺼져_있으면_alertEnabled_와_상관없이_환기_알림이_없다() throws Exception {
      settings(W1, "{\"notifyVentilation\":false,\"alertEnabled\":true}");
      reading(r618, 1180, NOW.minusMinutes(1));
      predictionAction(r617, 1250, NOW.plusMinutes(10));

      assertThat(scanService.scan(NOW)).isZero();
      assertThat(notifications(W1)).isEmpty();
    }
  }

  @Nested
  class VentilationSoon {

    @Test
    void 남은_분이_alertLeadMinutes_이내면_N분_뒤_문구() throws Exception {
      long actionId = predictionAction(r618, 1250, NOW.plusMinutes(20));
      assertThat(scanService.scan(NOW)).isZero();   // 기본 15분 → 20분 뒤는 아직

      settings(W1, "{\"alertLeadMinutes\":20}");
      assertThat(scanService.scan(NOW)).isEqualTo(1);
      assertThat(notifications(W1).getFirst()).containsEntry("type", "VENTILATION_SOON")
          .containsEntry("title", "618호 20분 뒤 환기 필요").containsEntry("body", "20분 뒤 1,250ppm 예상")
          .containsEntry("action_id", actionId);
    }

    @Test
    void 남은_분은_올림() {
      predictionAction(r618, 1250, NOW.plusMinutes(14).plusSeconds(10));
      scanService.scan(NOW);
      assertThat(notifications(W1).getFirst()).containsEntry("title", "618호 15분 뒤 환기 필요");
    }

    @Test
    void alertEnabled_가_꺼져_있으면_곧_환기만_안_생긴다() throws Exception {
      settings(W1, "{\"alertEnabled\":false}");
      predictionAction(r617, 1250, NOW.plusMinutes(10));
      reading(r618, 1180, NOW.minusMinutes(1));

      scanService.scan(NOW);
      assertThat(notifications(W1)).extracting(n -> n.get("type")).containsExactly("VENTILATION_NOW");
    }
  }

  @Nested
  class SensorError {

    @Test
    void FULL_만_SIM_은_제외() {
      reading(r617, 500, NOW.minusMinutes(11));
      reading(r618, 500, NOW.minusMinutes(30));   // SIM 은 끊겨도 알림 없음

      assertThat(scanService.scan(NOW)).isEqualTo(1);
      assertThat(notifications(W1).getFirst()).containsEntry("type", "SENSOR_ERROR")
          .containsEntry("title", "617호 센서 연결 끊김").containsEntry("body", "10분째 데이터가 없어요")
          .containsEntry("room_id", r617);
    }

    @Test
    void 끊김_복구_다시_끊김이면_2건() {
      reading(r617, 500, NOW.minusMinutes(11));
      scanService.scan(NOW);
      scanService.scan(NOW.plusMinutes(1));   // 여전히 같은 끊김

      reading(r617, 500, NOW.plusMinutes(2));   // 복구
      assertThat(scanService.scan(NOW.plusMinutes(3))).isZero();

      assertThat(scanService.scan(NOW.plusMinutes(13))).isEqualTo(1);   // 다시 10분 넘게 없음
      assertThat(notifications(W1)).hasSize(2).allSatisfy(n -> assertThat(n).containsEntry("type", "SENSOR_ERROR"));
    }

    @Test
    void notifySensorError_가_꺼져_있으면_없다() throws Exception {
      settings(W1, "{\"notifySensorError\":false}");
      reading(r617, 500, NOW.minusMinutes(11));
      assertThat(scanService.scan(NOW)).isZero();
    }
  }

  @Nested
  class Complaints {

    @Test
    void 민원_접수_즉시_새_민원_알림_분류는_보낸_순서() throws Exception {
      complain(r618, "client-a", "[\"HUMID\",\"HOT\"]");
      long complaintId = jdbc.queryForObject("select id from complaint", Long.class);

      assertThat(notifications(W1)).singleElement().satisfies(n -> assertThat(n)
          .containsEntry("type", "NEW_COMPLAINT").containsEntry("title", "618호 새 민원")
          .containsEntry("body", "습해요 · 더워요").containsEntry("complaint_id", complaintId));
      assertThat(notifications(W2)).isEmpty();
    }

    @Test
    void 반복_민원은_5건째에_1건_같은_주_6_7건째엔_추가_없음() throws Exception {
      for (int i = 1; i <= 4; i++) {
        complain(r618, "client-" + i, "[\"STUFFY\"]");
      }
      assertThat(notifications(W1)).extracting(n -> n.get("type")).doesNotContain("REPEATED_COMPLAINT");

      complain(r618, "client-5", "[\"STUFFY\"]");
      complain(r618, "client-6", "[\"STUFFY\"]");
      complain(r618, "client-7", "[\"STUFFY\"]");

      List<Map<String, Object>> repeated = notifications(W1).stream()
          .filter(n -> n.get("type").equals("REPEATED_COMPLAINT")).toList();
      assertThat(repeated).singleElement().satisfies(n -> assertThat(n)
          .containsEntry("title", "618호 반복 민원").containsEntry("body", "같은 민원이 5건 모였어요"));
      assertThat(notifications(W1)).filteredOn(n -> n.get("type").equals("NEW_COMPLAINT")).hasSize(7);
    }

    @Test
    void notifyComplaint_가_꺼져_있으면_없다() throws Exception {
      settings(W1, "{\"notifyComplaint\":false}");
      complain(r618, "client-a", "[\"HOT\"]");
      assertThat(notifications(W1)).isEmpty();
    }
  }

  @Nested
  class Inbox {

    private long insert(String loginId, String type, LocalDateTime createdAt) {
      return jdbc.queryForObject("""
          insert into notification (member_id, type, room_id, title, body, dedup_key, created_at)
          values ((select id from member where login_id = ?), ?, ?, '618호 환기 필요', '지금 1,180ppm', ?, ?)
          returning id""", Long.class, loginId, type, r618, "test:" + createdAt, createdAt);
    }

    @Test
    void 최신순_명세_형식_7일_지난_알림은_제외() throws Exception {
      insert(W1, "SENSOR_ERROR", NOW.minusDays(8));
      long older = insert(W1, "VENTILATION_SOON", NOW.minusHours(1));
      long newer = insert(W1, "VENTILATION_NOW", NOW.minusMinutes(5));

      as(W1, get("/api/notifications"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.unreadCount").value(2))
          .andExpect(jsonPath("$.data.hasNext").value(false))
          .andExpect(jsonPath("$.data.content[*].notificationId", contains((int) newer, (int) older)))
          .andExpect(jsonPath("$.data.content[0].type").value("VENTILATION_NOW"))
          .andExpect(jsonPath("$.data.content[0].title").value("618호 환기 필요"))
          .andExpect(jsonPath("$.data.content[0].body").value("지금 1,180ppm"))
          .andExpect(jsonPath("$.data.content[0].roomId").value(r618))
          .andExpect(jsonPath("$.data.content[0].actionId").value(nullValue()))
          .andExpect(jsonPath("$.data.content[0].complaintId").value(nullValue()))
          .andExpect(jsonPath("$.data.content[0].read").value(false))
          .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-21T13:55:00"));
    }

    @Test
    void 페이지() throws Exception {
      for (int i = 1; i <= 3; i++) {
        insert(W1, "VENTILATION_NOW", NOW.minusMinutes(i));
      }
      as(W1, get("/api/notifications?page=0&size=2"))
          .andExpect(jsonPath("$.data.content", hasSize(2))).andExpect(jsonPath("$.data.hasNext").value(true));
      as(W1, get("/api/notifications?page=1&size=2"))
          .andExpect(jsonPath("$.data.content", hasSize(1))).andExpect(jsonPath("$.data.hasNext").value(false));
      as(W1, get("/api/notifications?size=51")).andExpect(status().isBadRequest());
    }

    @Test
    void 읽음은_본인_것만_이미_읽었으면_그대로_200() throws Exception {
      long mine = insert(W1, "VENTILATION_NOW", NOW.minusMinutes(5));

      as(W2, patch("/api/notifications/{id}/read", mine)).andExpect(status().isForbidden());
      as(W1, patch("/api/notifications/{id}/read", 99999)).andExpect(status().isNotFound());
      as(W1, patch("/api/notifications/{id}/read", mine))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));
      LocalDateTime readAt = jdbc.queryForObject("select read_at from notification where id = ?", LocalDateTime.class, mine);
      as(W1, patch("/api/notifications/{id}/read", mine)).andExpect(status().isOk());
      assertThat(jdbc.queryForObject("select read_at from notification where id = ?", LocalDateTime.class, mine))
          .isEqualTo(readAt);
      as(W1, get("/api/notifications")).andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void 모두_읽음_후_unreadCount_0_다른_사람_것은_그대로() throws Exception {
      insert(W1, "VENTILATION_NOW", NOW.minusMinutes(5));
      insert(W1, "SENSOR_ERROR", NOW.minusMinutes(3));
      insert(W2, "NEW_COMPLAINT", NOW.minusMinutes(1));

      as(W1, patch("/api/notifications/read-all"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.data.readCount").value(2));
      as(W1, get("/api/notifications")).andExpect(jsonPath("$.data.unreadCount").value(0));
      as(W2, get("/api/notifications")).andExpect(jsonPath("$.data.unreadCount").value(1));
    }
  }

  @Nested
  class Qr {

    @Test
    void PNG_를_디코딩하면_민원_주소() throws Exception {
      byte[] png = as(W1, get("/api/rooms/{id}/qr", r617))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.IMAGE_PNG))
          .andExpect(header().string("Content-Disposition", "attachment; filename=\"qr-bukak-617.png\""))
          .andReturn().getResponse().getContentAsByteArray();

      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image.getWidth()).isEqualTo(512);
      String decoded = new MultiFormatReader().decode(
          new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
      assertThat(decoded).isEqualTo("https://front.test/complaint?code=bukak-617");
    }

    @Test
    void 정보() throws Exception {
      as(W1, get("/api/rooms/{id}/qr/info", r617))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.roomId").value(r617))
          .andExpect(jsonPath("$.data.room").value("북악관 617호"))
          .andExpect(jsonPath("$.data.url").value("https://front.test/complaint?code=bukak-617"));
    }

    @Test
    void 화장실_기타_방은_400_없는_방은_404() throws Exception {
      as(W1, get("/api/rooms/{id}/qr", wc6)).andExpect(status().isBadRequest());
      as(W1, get("/api/rooms/{id}/qr", roomId("bukak-604"))).andExpect(status().isBadRequest());
      as(W1, get("/api/rooms/{id}/qr/info", wc6)).andExpect(status().isBadRequest());
      as(W1, get("/api/rooms/{id}/qr", 99999)).andExpect(status().isNotFound());
    }
  }
}
