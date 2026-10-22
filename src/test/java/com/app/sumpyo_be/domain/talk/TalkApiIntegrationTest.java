package com.app.sumpyo_be.domain.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executor;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 캠퍼스톡. 실제 PostgreSQL(일회용 컨테이너)에 Flyway 를 적용하고 호출한다. Docker 가 켜져 있어야 한다.
 * 시각은 일요일 03:00 으로 고정 (V2 시간표는 월~금이라, 테스트가 넣은 수업만 영향을 준다).
 * 계정: worker01(김근로, 북악관 5층으로 설정) / worker02(이근로, 북악관 6층 — 데모 초기화가 만듦). X-Demo-Login 으로 전환.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    // 로컬 .env 에 공공데이터 키가 있어도 외부 API 를 부르지 않도록
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    // 로컬 .env 에 LLM 키가 있어도 테스트는 외부 API 를 부르지 않고 규칙 기반으로만
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc(addFilters = false)   // main 엔 시큐리티가 없음. security-test 의존성이 테스트에서만 필터를 켜서 끈다
@Testcontainers
// @Nested 테스트에도 고정 시각이 적용되도록 명시. 추천 답변 비동기 생성은 동기 실행기로 (결과가 매번 같도록)
@Import({TalkApiIntegrationTest.FixedClock.class, TalkApiIntegrationTest.SyncAsync.class})
class TalkApiIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  /** 2026-10-25 (일) 03:00 KST */
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 3, 0);
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

  /** @Async 를 요청 스레드에서 커밋 직후 바로 실행 (AsyncConfigurer 의 실행기가 기본 실행기보다 우선) */
  @TestConfiguration
  static class SyncAsync implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
      return new SyncTaskExecutor();
    }
  }

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  long bukak;
  long hyein;
  long r617;
  long r618;
  long r508;
  long wc6;
  long r604;   // V8 부터 기타(ETC)
  long hyeinRoom;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.update("delete from talk_reply");
    jdbc.update("delete from talk_post");
    jdbc.update("delete from member_talk_filter");
    jdbc.update("delete from class_cancellation where memo = 'talk-test'");
    jdbc.update("delete from class_schedule where semester = 'talk-test'");
    jdbc.update("delete from sensor_reading");
    bukak = id("select id from building where name = '북악관'");
    hyein = id("select id from building where name = '혜인관'");
    r617 = roomId("bukak-617");
    r618 = roomId("bukak-618");
    r508 = roomId("bukak-508");
    wc6 = roomId("bukak-6F-WC1");
    r604 = roomId("bukak-604");
    jdbc.update("""
        insert into room (building_id, code, number, floor, type, sensor_level)
        values (?, 'hyein-301', '301', 3, 'LECTURE', 'NONE') on conflict (code) do nothing""", hyein);
    hyeinRoom = roomId("hyein-301");
    as(W1, put("/api/me/zones"), "[{\"buildingId\":" + bukak + ",\"floors\":[5]}]").andExpect(status().isOk());
  }

  private long id(String sql) {
    return jdbc.queryForObject(sql, Long.class);
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

  /** API 로 보내고 talkId 반환 */
  private long send(String loginId, int floor, Long roomId, String content) throws Exception {
    String res = as(loginId, post("/api/talks"),
        "{\"floor\":" + floor + ",\"roomId\":" + roomId + ",\"content\":\"" + content + "\"}")
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(res, "$.data.talkId")).longValue();
  }

  /** 다른 건물 글은 API 로 못 보내므로 직접 넣는다 */
  private long insertHyeinPost() {
    return jdbc.queryForObject("""
        insert into talk_post (sender_id, building_id, floor, content, status, created_at)
        values ((select id from member where login_id = 'worker01'), ?, 3, '혜인관 글', 'UNREAD', ?)
        returning id""", Long.class, hyein, NOW);
  }

  private void filter(String loginId, String floorsJson) throws Exception {
    as(loginId, put("/api/me/talk-filter"), "{\"floors\":" + floorsJson + "}").andExpect(status().isOk());
  }

  @Nested
  class ListFilter {

    long floor5;
    long floor6;
    long room617;
    long room618;

    @BeforeEach
    void posts() throws Exception {
      floor5 = send(W1, 5, null, "5층 전체");
      floor6 = send(W1, 6, null, "6층 전체");
      room617 = send(W1, 6, r617, "617호 확인");
      room618 = send(W1, 6, r618, "618호 확인");
      insertHyeinPost();
    }

    @Test
    void 필터가_없으면_내_건물_전체_다른_건물_글은_안_보인다() throws Exception {
      as(W2, get("/api/talks"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.building").value("북악관"))
          .andExpect(jsonPath("$.data.filter", empty()))
          .andExpect(jsonPath("$.data.content[*].talkId", contains(
              (int) room618, (int) room617, (int) floor6, (int) floor5)))
          .andExpect(jsonPath("$.data.counts.all").value(4))
          .andExpect(jsonPath("$.data.counts.unread").value(4))
          .andExpect(jsonPath("$.data.content[0].target").value("6층 · 618호"))
          .andExpect(jsonPath("$.data.content[2].target").value("6층"))
          .andExpect(jsonPath("$.data.content[0].sentByMe").value(false))
          .andExpect(jsonPath("$.data.hasNext").value(false));
      as(W1, get("/api/talks")).andExpect(jsonPath("$.data.content[0].sentByMe").value(true));
    }

    @Test
    void 층만_고르면_그_층의_모든_글() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[]}]");
      as(W2, get("/api/talks"))
          .andExpect(jsonPath("$.data.content[*].talkId", containsInAnyOrder(
              (int) floor6, (int) room617, (int) room618)))
          .andExpect(jsonPath("$.data.counts.all").value(3))
          .andExpect(jsonPath("$.data.filter[0].floor").value(6))
          .andExpect(jsonPath("$.data.filter[0].rooms", empty()));
    }

    @Test
    void 층과_강의실을_고르면_그_강의실로_보낸_글만_층_전체_글은_빠진다() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[" + r617 + "]}]");
      as(W2, get("/api/talks"))
          .andExpect(jsonPath("$.data.content[*].talkId", contains((int) room617)))
          .andExpect(jsonPath("$.data.counts.all").value(1))
          .andExpect(jsonPath("$.data.filter[0].rooms[0].number").value("617"));
    }

    @Test
    void 여러_층을_섞으면_층마다_OR_counts도_같은_기준() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[" + r617 + "," + r618 + "]},{\"floor\":5,\"roomIds\":[]}]");
      as(W2, get("/api/talks/{id}", room617)).andExpect(status().isOk());   // CHECKING 하나

      as(W2, get("/api/talks"))
          .andExpect(jsonPath("$.data.content[*].talkId", containsInAnyOrder(
              (int) floor5, (int) room617, (int) room618)))
          .andExpect(jsonPath("$.data.counts.all").value(3))
          .andExpect(jsonPath("$.data.counts.unread").value(2))
          .andExpect(jsonPath("$.data.counts.checking").value(1))
          .andExpect(jsonPath("$.data.counts.answered").value(0))
          .andExpect(jsonPath("$.data.filter[*].floor", contains(5, 6)))
          .andExpect(jsonPath("$.data.filter[1].rooms[*].number", contains("617", "618")));
      as(W2, get("/api/talks").param("status", "CHECKING"))
          .andExpect(jsonPath("$.data.content[*].talkId", contains((int) room617)))
          .andExpect(jsonPath("$.data.counts.all").value(3));
    }

    @Test
    void 무한_스크롤_nextCursor_로_이어서_가져오고_중간에_새_글이_와도_중복이_없다() throws Exception {
      as(W2, get("/api/talks").param("size", "3"))
          .andExpect(jsonPath("$.data.content[*].talkId", contains((int) room618, (int) room617, (int) floor6)))
          .andExpect(jsonPath("$.data.hasNext").value(true))
          .andExpect(jsonPath("$.data.nextCursor").value(floor6))
          .andExpect(jsonPath("$.data.counts.all").value(4));

      send(W1, 5, null, "스크롤 중 새 글");   // offset 페이징이면 다음 페이지에 floor6 이 또 나온다

      as(W2, get("/api/talks").param("size", "3").param("cursor", String.valueOf(floor6)))
          .andExpect(jsonPath("$.data.content[*].talkId", contains((int) floor5)))
          .andExpect(jsonPath("$.data.hasNext").value(false))
          .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
          .andExpect(jsonPath("$.data.counts.all").value(5));
    }

    @Test
    void 딱_맞게_끝나면_hasNext_false() throws Exception {
      as(W2, get("/api/talks").param("size", "4"))
          .andExpect(jsonPath("$.data.content", hasSize(4)))
          .andExpect(jsonPath("$.data.hasNext").value(false));
    }
  }

  @Nested
  class FilterApi {

    @Test
    void 저장하면_GET_과_같은_형식_강의실_중복은_합친다() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[" + r618 + "," + r617 + "," + r617 + "]},{\"floor\":5,\"roomIds\":[" + r508 + "]}]");
      as(W2, get("/api/me/talk-filter"))
          .andExpect(jsonPath("$.data.buildingId").value(bukak))
          .andExpect(jsonPath("$.data.building").value("북악관"))
          .andExpect(jsonPath("$.data.floors[*].floor", contains(5, 6)))
          .andExpect(jsonPath("$.data.floors[0].rooms[0].roomId").value(r508))
          .andExpect(jsonPath("$.data.floors[0].rooms[0].number").value("508"))
          .andExpect(jsonPath("$.data.floors[1].rooms[*].number", contains("617", "618")));
    }

    @Test
    void 통째로_교체되고_빈_배열이면_건물_전체() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[" + r617 + "]}]");
      filter(W2, "[{\"floor\":5,\"roomIds\":[]}]");
      as(W2, get("/api/me/talk-filter")).andExpect(jsonPath("$.data.floors[*].floor", contains(5)));
      filter(W2, "[]");
      as(W2, get("/api/me/talk-filter")).andExpect(jsonPath("$.data.floors", empty()));
    }

    @Test
    void 범위_밖_층은_400() throws Exception {
      as(W2, put("/api/me/talk-filter"), "{\"floors\":[{\"floor\":8,\"roomIds\":[]}]}").andExpect(status().isBadRequest());
      as(W2, put("/api/me/talk-filter"), "{\"floors\":[{\"floor\":0,\"roomIds\":[]}]}").andExpect(status().isBadRequest());
    }

    @Test
    void 다른_층_방_다른_건물_방_강의실_아닌_방은_400_실패하면_기존_값_유지() throws Exception {
      filter(W2, "[{\"floor\":6,\"roomIds\":[" + r617 + "]}]");
      for (long roomId : new long[] {r508, hyeinRoom, wc6, r604, 999_999}) {
        as(W2, put("/api/me/talk-filter"), "{\"floors\":[{\"floor\":6,\"roomIds\":[" + roomId + "]}]}")
            .andExpect(status().isBadRequest());
      }
      as(W2, get("/api/me/talk-filter")).andExpect(jsonPath("$.data.floors[0].rooms[0].number").value("617"));
    }

    @Test
    void 층_중복은_400() throws Exception {
      as(W2, put("/api/me/talk-filter"), "{\"floors\":[{\"floor\":6,\"roomIds\":[]},{\"floor\":6,\"roomIds\":[" + r617 + "]}]}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("같은 층을 중복해서 보낼 수 없습니다."));
    }

    @Test
    void 담당_구역이_없으면_400() throws Exception {
      as(W1, put("/api/me/zones"), "[]").andExpect(status().isOk());
      as(W1, get("/api/me/talk-filter")).andExpect(status().isBadRequest());
      as(W1, get("/api/talks")).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class Send {

    @Test
    void floor가_없으면_400() throws Exception {
      as(W1, post("/api/talks"), "{\"content\":\"안녕하세요\"}").andExpect(status().isBadRequest());
    }

    @Test
    void 범위_밖_층_다른_건물_방_다른_층_방_강의실_아닌_방은_400() throws Exception {
      for (long roomId : new long[] {wc6, r604}) {
        as(W1, post("/api/talks"), "{\"floor\":6,\"roomId\":" + roomId + ",\"content\":\"x\"}")
            .andExpect(status().isBadRequest());
      }
      as(W1, post("/api/talks"), "{\"floor\":8,\"content\":\"x\"}").andExpect(status().isBadRequest());
      as(W1, post("/api/talks"), "{\"floor\":3,\"roomId\":" + hyeinRoom + ",\"content\":\"x\"}")
          .andExpect(status().isBadRequest());
      as(W1, post("/api/talks"), "{\"floor\":5,\"roomId\":" + r617 + ",\"content\":\"x\"}")
          .andExpect(status().isBadRequest());
    }

    @Test
    void 내용은_공백_제거_후_1에서_300자() throws Exception {
      as(W1, post("/api/talks"), "{\"floor\":1,\"content\":\"   \"}").andExpect(status().isBadRequest());
      as(W1, post("/api/talks"), "{\"floor\":1,\"content\":\"" + "가".repeat(301) + "\"}").andExpect(status().isBadRequest());
      as(W1, post("/api/talks"), "{\"floor\":1,\"content\":\" " + "가".repeat(300) + " \"}").andExpect(status().isOk());
    }

    @Test
    void 건물은_내_건물로_자동() throws Exception {
      long talkId = send(W1, 1, null, "북악관 1층에 계신 분 있나요?");
      as(W2, get("/api/talks/{id}", talkId))
          .andExpect(jsonPath("$.data.building").value("북악관"))
          .andExpect(jsonPath("$.data.target").value("1층"));
    }
  }

  @Nested
  class StatusAndPermission {

    @Test
    void 보낸_사람이_열면_UNREAD_그대로_다른_사람이_처음_열면_CHECKING_첫_답변이면_ANSWERED() throws Exception {
      long talkId = send(W1, 6, r617, "지금 수업 중인가요?");

      as(W1, get("/api/talks/{id}", talkId))
          .andExpect(jsonPath("$.data.status").value("UNREAD"))
          .andExpect(jsonPath("$.data.sentByMe").value(true))
          .andExpect(jsonPath("$.data.suggestions", empty()));
      as(W2, get("/api/talks/{id}", talkId))
          .andExpect(jsonPath("$.data.status").value("CHECKING"))
          .andExpect(jsonPath("$.data.sentByMe").value(false));

      as(W2, post("/api/talks/{id}/replies", talkId), "{\"content\":\" 617호 지금 수업 중이에요 \",\"isSuggested\":true}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.replyId").isNumber())
          .andExpect(jsonPath("$.data.talkStatus").value("ANSWERED"));

      as(W1, get("/api/talks/{id}", talkId))
          .andExpect(jsonPath("$.data.status").value("ANSWERED"))
          .andExpect(jsonPath("$.data.replies", hasSize(1)))
          .andExpect(jsonPath("$.data.replies[0].content").value("617호 지금 수업 중이에요"))
          .andExpect(jsonPath("$.data.replies[0].author").value("이근로"))
          .andExpect(jsonPath("$.data.replies[0].isSuggested").value(true));
      // 답변 후 다시 열어도 CHECKING 으로 돌아가지 않는다
      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.status").value("ANSWERED"));
    }

    @Test
    void 보낸_사람_본인은_답변_403() throws Exception {
      long talkId = send(W1, 6, null, "6층 확인 부탁");
      as(W1, post("/api/talks/{id}/replies", talkId), "{\"content\":\"셀프 답변\"}").andExpect(status().isForbidden());
    }

    @Test
    void 다른_건물_글은_조회_답변_403_없는_글은_404() throws Exception {
      long hyeinPost = insertHyeinPost();
      as(W2, get("/api/talks/{id}", hyeinPost)).andExpect(status().isForbidden());
      as(W2, post("/api/talks/{id}/replies", hyeinPost), "{\"content\":\"답\"}").andExpect(status().isForbidden());
      as(W2, get("/api/talks/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void 답변_내용_검증과_isSuggested_기본값() throws Exception {
      long talkId = send(W1, 6, null, "6층 확인 부탁");
      as(W2, post("/api/talks/{id}/replies", talkId), "{\"content\":\" \"}").andExpect(status().isBadRequest());
      as(W2, post("/api/talks/{id}/replies", talkId), "{\"content\":\"" + "가".repeat(301) + "\"}")
          .andExpect(status().isBadRequest());
      as(W2, post("/api/talks/{id}/replies", talkId), "{\"content\":\"확인했어요\"}").andExpect(status().isOk());
      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.replies[0].isSuggested").value(false));
    }

    @Test
    void 데모_계정이_아닌_X_Demo_Login_은_401() throws Exception {
      as("admin", get("/api/talks")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class Suggestions {

    private long schedule617(String start, String end) {
      return jdbc.queryForObject("""
          insert into class_schedule (room_id, course_name, day_of_week, start_time, end_time, semester)
          values (?, '데이터크롤링', 7, ?::time, ?::time, 'talk-test') returning id""", Long.class, r617, start, end);
    }

    @Test
    void 강의실_수업_중이고_CO2_센서가_있으면_수업과_CO2_그리고_위치() throws Exception {
      schedule617("02:30", "03:45");
      jdbc.update("insert into sensor_reading (room_id, co2, temp, humidity, measured_at) values (?, 1050, 24, 45, ?)",
          r617, NOW.minusMinutes(2));
      long talkId = send(W1, 6, r617, "지금 수업 중인가요?");

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "617호 지금 수업 중이에요 (데이터크롤링, 03:45까지)",
          "617호 지금 CO2 1,050ppm이에요",
          "지금 북악관 6층에 있어요")));
    }

    @Test
    void 수업이_없으면_수업_없어요() throws Exception {
      schedule617("05:00", "06:15");   // 오늘이지만 아직 시작 전
      long talkId = send(W1, 6, r617, "617호 비었나요?");

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "617호 지금 수업 없어요", "지금 북악관 6층에 있어요")));
    }

    @Test
    void 오늘_휴강이면_수업_없어요() throws Exception {
      long scheduleId = schedule617("02:30", "03:45");
      jdbc.update("insert into class_cancellation (class_schedule_id, cancel_date, memo) values (?, ?, 'talk-test')",
          scheduleId, NOW.toLocalDate());
      long talkId = send(W1, 6, r617, "수업 있나요?");

      as(W2, get("/api/talks/{id}", talkId))
          .andExpect(jsonPath("$.data.suggestions[0]").value("617호 지금 수업 없어요"));
    }

    @Test
    void 강의실이_없으면_위치와_키워드만() throws Exception {
      long talkId = send(W1, 1, null, "1층에 계신 분 있나요? 창문 좀 봐주세요");

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "창문 확인 완료 했어요", "지금 북악관 1층에 있어요")));
    }

    @Test
    void 강의실을_안_골라도_본문의_호수로_찾는다() throws Exception {
      long talkId = send(W1, 6, null, "618호 에어컨 켜져 있나요?");

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "618호 지금 수업 없어요", "에어컨 확인 완료 했어요", "지금 북악관 6층에 있어요")));
    }

    @Test
    void 작성_커밋_후_만든_추천을_저장하고_30분_이내면_그대로_쓴다() throws Exception {
      long talkId = send(W1, 6, r617, "지금 수업 중인가요?");
      // 테스트는 동기 실행기라 응답 전에 비동기 생성이 끝나 있다 (LLM 설정 없음 → 규칙 기반)
      assertThat(jdbc.queryForObject("select suggestions_generated_at from talk_post where id = ?",
          LocalDateTime.class, talkId)).isEqualTo(NOW);

      // 저장값이 30분 이내면 정보가 바뀌어도 다시 만들지 않는다
      schedule617("02:30", "03:45");
      jdbc.update("update talk_post set suggestions = '[\"저장된 추천\"]', suggestions_generated_at = ? where id = ?",
          NOW.minusMinutes(29), talkId);
      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains("저장된 추천")));
      as(W1, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", empty()));
    }

    @Test
    void 저장된_지_30분이_지나면_규칙_기반으로_다시_만들어_저장한다() throws Exception {
      long talkId = send(W1, 6, r617, "지금 수업 중인가요?");
      schedule617("02:30", "03:45");   // 그 사이 수업이 시작됐다고 가정
      jdbc.update("update talk_post set suggestions = '[\"오래된 추천\"]', suggestions_generated_at = ? where id = ?",
          NOW.minusMinutes(31), talkId);

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "617호 지금 수업 중이에요 (데이터크롤링, 03:45까지)", "지금 북악관 6층에 있어요")));
      assertThat(jdbc.queryForObject("select suggestions from talk_post where id = ?", String.class, talkId))
          .contains("데이터크롤링");
      assertThat(jdbc.queryForObject("select suggestions_generated_at from talk_post where id = ?",
          LocalDateTime.class, talkId)).isEqualTo(NOW);
    }

    @Test
    void 추천이_아직_없으면_규칙_기반으로_즉시_주고_저장하지_않는다() throws Exception {
      long talkId = send(W1, 6, r617, "지금 수업 중인가요?");
      jdbc.update("update talk_post set suggestions = null, suggestions_generated_at = null where id = ?", talkId);

      as(W2, get("/api/talks/{id}", talkId)).andExpect(jsonPath("$.data.suggestions", contains(
          "617호 지금 수업 없어요", "지금 북악관 6층에 있어요")));
      assertThat(jdbc.queryForObject("select suggestions from talk_post where id = ?", String.class, talkId)).isNull();
    }
  }
}
