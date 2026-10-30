package com.app.sumpyo_be.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.sumpyo_be.global.security.AuthProperties;
import com.app.sumpyo_be.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 로그인·회원가입·JWT. 시큐리티 필터를 켠 채로(addFilters 기본값) 호출한다. 데모 모드는 꺼져 있다 → 토큰 없으면 근로생 API 401.
 * 데모 계정 worker01(담당 구역 없음)·worker02(북악관 6층) 비밀번호 = test-demo-password.
 */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.auto-complete-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.action.measure-enabled=false",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=false",
    "sumpyo.complaint.salt=test-salt",
    // 운영 값과 같은 형식 (정확한 주소 + Netlify 미리보기 와일드카드)
    "cors.allowed-origins=https://sumpyo-sku.netlify.app, https://deploy-preview-*--sumpyo-sku.netlify.app, http://localhost:5173",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

  private static final String DEMO_PASSWORD = "test-demo-password";

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Value("${sumpyo.auth.jwt-secret}") String jwtSecret;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from talk_reply");
    jdbc.update("delete from talk_post");
    jdbc.update("delete from member_zone where member_id in (select id from member where login_id like 'new%')");
    jdbc.update("delete from member where login_id like 'new%'");
  }

  private ResultActions postJson(String url, String body, String ip) throws Exception {
    return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
        .with(r -> {
          r.setRemoteAddr(ip);
          return r;
        }));
  }

  private String token(ResultActions res) throws Exception {
    return JsonPath.read(res.andReturn().getResponse().getContentAsString(), "$.data.accessToken");
  }

  private String login(String loginId, String password) throws Exception {
    return token(postJson("/api/auth/login", "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}",
        "10.0.0.1").andExpect(status().isOk()));
  }

  private String signup(String loginId) throws Exception {
    return token(postJson("/api/auth/signup", "{\"name\":\"박근로\",\"loginId\":\"" + loginId
        + "\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}", "10.0.0.1").andExpect(status().isOk()));
  }

  private ResultActions withToken(MockHttpServletRequestBuilder req, String token) throws Exception {
    return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
  }

  @Nested
  class Signup {

    @Test
    void 가입하면_바로_로그인_상태_근로생_기본값_BCrypt() throws Exception {
      postJson("/api/auth/signup", """
          {"name":" 박근로 ","loginId":"newbie01","password":"abc12345","passwordConfirm":"abc12345"}""", "10.0.1.1")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.accessToken").isString())
          .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
          .andExpect(jsonPath("$.data.expiresIn").value(604800))
          .andExpect(jsonPath("$.data.name").value("박근로"))
          .andExpect(jsonPath("$.data.role").value("WORKER"))
          .andExpect(jsonPath("$.data.hasZones").value(false));
      var row = jdbc.queryForMap("select password, role, co2_threshold, notify_ventilation from member where login_id = 'newbie01'");
      assertThat((String) row.get("password")).startsWith("$2").doesNotContain("abc12345");
      assertThat(row).containsEntry("role", "WORKER").containsEntry("co2_threshold", 1000)
          .containsEntry("notify_ventilation", true);
    }

    @Test
    void 필드_형식_오류_400() throws Exception {
      String[] bodies = {
          "{\"name\":\"\",\"loginId\":\"newbie02\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}",
          "{\"name\":\"" + "가".repeat(31) + "\",\"loginId\":\"newbie02\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}",
          "{\"name\":\"박\",\"loginId\":\"Newbie02\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}",
          "{\"name\":\"박\",\"loginId\":\"new\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}",
          "{\"name\":\"박\",\"loginId\":\"1newbie\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}",
          "{\"name\":\"박\",\"loginId\":\"newbie02\",\"password\":\"abcdefgh\",\"passwordConfirm\":\"abcdefgh\"}",
          "{\"name\":\"박\",\"loginId\":\"newbie02\",\"password\":\"12345678\",\"passwordConfirm\":\"12345678\"}",
          "{\"name\":\"박\",\"loginId\":\"newbie02\",\"password\":\"abc1234\",\"passwordConfirm\":\"abc1234\"}",
          "{\"name\":\"박\",\"loginId\":\"newbie02\",\"password\":\"abc12345\"}",
      };
      for (String body : bodies) {
        postJson("/api/auth/signup", body, "10.0.1.2").andExpect(status().isBadRequest());
      }
      postJson("/api/auth/signup", "{\"name\":\"\",\"loginId\":\"\",\"password\":\"\",\"passwordConfirm\":\"\"}", "10.0.1.2")
          .andExpect(jsonPath("$.message").value(containsString("모든 정보를 입력해 주세요.")));
      postJson("/api/auth/signup", "{\"name\":\"박\",\"loginId\":\"newbie02\",\"password\":\"abcdefgh\",\"passwordConfirm\":\"abcdefgh\"}", "10.0.1.2")
          .andExpect(jsonPath("$.message").value(containsString("비밀번호 형식 오류 (영어, 숫자 포함 8글자 이상)")));
      assertThat(jdbc.queryForObject("select count(*) from member where login_id = 'newbie02'", Integer.class)).isZero();
    }

    @Test
    void 비밀번호_확인_불일치_400_중복_아이디_409() throws Exception {
      postJson("/api/auth/signup", "{\"name\":\"박\",\"loginId\":\"newbie03\",\"password\":\"abc12345\",\"passwordConfirm\":\"abc12346\"}", "10.0.1.3")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("비밀번호가 일치하지 않습니다."));
      signup("newbie03");
      postJson("/api/auth/signup", "{\"name\":\"이\",\"loginId\":\"newbie03\",\"password\":\"xyz12345\",\"passwordConfirm\":\"xyz12345\"}", "10.0.1.3")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }
  }

  @Test
  void 아이디_중복_확인() throws Exception {
    mvc.perform(get("/api/auth/check-login-id").param("loginId", "newbie04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.loginId").value("newbie04"))
        .andExpect(jsonPath("$.data.available").value(true));
    mvc.perform(get("/api/auth/check-login-id").param("loginId", "worker01"))
        .andExpect(jsonPath("$.data.available").value(false));
    mvc.perform(get("/api/auth/check-login-id").param("loginId", "Ab!")).andExpect(status().isBadRequest());
  }

  @Nested
  class Login {

    @Test
    void 데모_계정도_로그인_hasZones() throws Exception {
      postJson("/api/auth/login", "{\"loginId\":\"worker02\",\"password\":\"" + DEMO_PASSWORD + "\"}", "10.0.2.1")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.name").value("이근로"))
          .andExpect(jsonPath("$.data.role").value("WORKER"))
          .andExpect(jsonPath("$.data.hasZones").value(true));
      postJson("/api/auth/login", "{\"loginId\":\"worker01\",\"password\":\"" + DEMO_PASSWORD + "\"}", "10.0.2.1")
          .andExpect(jsonPath("$.data.hasZones").value(false));
    }

    @Test
    void 틀린_비밀번호와_없는_아이디는_같은_401() throws Exception {
      String wrongPassword = postJson("/api/auth/login", "{\"loginId\":\"worker01\",\"password\":\"wrong1234\"}", "10.0.2.2")
          .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
      String unknownId = postJson("/api/auth/login", "{\"loginId\":\"nobody99\",\"password\":\"wrong1234\"}", "10.0.2.2")
          .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
      assertThat(wrongPassword).isEqualTo(unknownId).contains("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void 같은_IP_10분에_10번_넘게_실패하면_429_다른_IP_는_영향_없음() throws Exception {
      for (int i = 0; i < 10; i++) {
        postJson("/api/auth/login", "{\"loginId\":\"worker01\",\"password\":\"wrong1234\"}", "10.0.2.3")
            .andExpect(status().isUnauthorized());
      }
      postJson("/api/auth/login", "{\"loginId\":\"worker01\",\"password\":\"" + DEMO_PASSWORD + "\"}", "10.0.2.3")
          .andExpect(status().isTooManyRequests());
      postJson("/api/auth/login", "{\"loginId\":\"worker01\",\"password\":\"" + DEMO_PASSWORD + "\"}", "10.0.2.4")
          .andExpect(status().isOk());
    }

    @Test
    void 로그인은_오래된_토큰이_붙어_와도_무시() throws Exception {
      mvc.perform(post("/api/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer broken")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"loginId\":\"worker01\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  class Token {

    @Test
    void 유효한_토큰으로_내_정보() throws Exception {
      String token = login("worker02", DEMO_PASSWORD);
      withToken(get("/api/me"), token)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.loginId").value("worker02"))
          .andExpect(jsonPath("$.data.name").value("이근로"))
          .andExpect(jsonPath("$.data.role").value("WORKER"))
          .andExpect(jsonPath("$.data.hasZones").value(true))
          .andExpect(jsonPath("$.data.zone").value("북악관 · 6층"));
      withToken(get("/api/me"), login("worker01", DEMO_PASSWORD))   // 담당 구역 없음
          .andExpect(jsonPath("$.data.hasZones").value(false))
          .andExpect(jsonPath("$.data.zone").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 내_정보_근로_구역은_여러_층이면_가운데_점_여러_건물이면_첫_건물() throws Exception {
      long bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
      long hyein = jdbc.queryForObject("select id from building where name = '혜인관'", Long.class);
      String token = signup("newbie06");
      withToken(put("/api/me/zones").contentType(MediaType.APPLICATION_JSON).content(
          "[{\"buildingId\":" + hyein + ",\"floors\":[3]},{\"buildingId\":" + bukak + ",\"floors\":[6,5]}]"), token)
          .andExpect(status().isOk());
      withToken(get("/api/me"), token).andExpect(jsonPath("$.data.zone").value("북악관 · 5·6층"));   // 북악관이 표시 순서 앞
    }

    @Test
    void 만료_위조_형식_오류_토큰은_401_로그인이_필요합니다() throws Exception {
      long memberId = jdbc.queryForObject("select id from member where login_id = 'worker01'", Long.class);
      Clock eightDaysAgo = Clock.fixed(Instant.now().minus(Duration.ofDays(8)), ZoneId.of("Asia/Seoul"));
      String expired = new JwtTokenProvider(new AuthProperties(jwtSecret, Duration.ofDays(7)), eightDaysAgo)
          .issue(memberId, "WORKER");
      String forged = new JwtTokenProvider(new AuthProperties("other-secret-0123456789-abcdefghijklmnop", null),
          Clock.systemUTC()).issue(memberId, "WORKER");

      for (String bad : new String[] {expired, forged, "not-a-jwt"}) {
        withToken(get("/api/me"), bad)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
      }
      mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Basic abc")).andExpect(status().isUnauthorized());
      withToken(get("/api/buildings"), "not-a-jwt").andExpect(status().isUnauthorized());   // 공개 API 여도
    }

    @Test
    void 토큰이_없고_데모_모드가_꺼져_있으면_근로생_API_401() throws Exception {
      long roomId = jdbc.queryForObject("select id from room where code = 'bukak-617'", Long.class);
      for (String url : new String[] {"/api/me", "/api/me/zones", "/api/talks", "/api/rooms/" + roomId}) {
        mvc.perform(get(url))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
      }
      mvc.perform(get("/api/me").header("X-Demo-Login", "worker02")).andExpect(status().isUnauthorized());
    }

    @Test
    void 근로생_API_는_토큰_계정_기준_담당_구역_sentByMe() throws Exception {
      long bukak = jdbc.queryForObject("select id from building where name = '북악관'", Long.class);
      String mine = signup("newbie05");
      withToken(put("/api/me/zones").contentType(MediaType.APPLICATION_JSON)
          .content("[{\"buildingId\":" + bukak + ",\"floors\":[6]}]"), mine).andExpect(status().isOk());
      withToken(get("/api/me"), mine).andExpect(jsonPath("$.data.hasZones").value(true));

      String created = withToken(post("/api/talks").contentType(MediaType.APPLICATION_JSON)
          .content("{\"floor\":6,\"content\":\"6층 확인 부탁\"}"), mine)
          .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      long talkId = ((Number) JsonPath.read(created, "$.data.talkId")).longValue();

      withToken(get("/api/talks/{id}", talkId), mine).andExpect(jsonPath("$.data.sentByMe").value(true));
      withToken(get("/api/talks"), mine).andExpect(jsonPath("$.data.content[0].sentByMe").value(true));
      String other = login("worker02", DEMO_PASSWORD);
      withToken(get("/api/talks/{id}", talkId), other).andExpect(jsonPath("$.data.sentByMe").value(false));
    }
  }

  @Test
  void 공개_API_는_토큰_없이_동작() throws Exception {
    long roomId = jdbc.queryForObject("select id from room where code = 'bukak-618'", Long.class);
    mvc.perform(get("/api/buildings")).andExpect(status().isOk());
    mvc.perform(get("/api/rooms/lookup").param("code", "bukak-618")).andExpect(status().isOk());
    mvc.perform(post("/api/complaints").contentType(MediaType.APPLICATION_JSON)
            .content("{\"roomId\":" + roomId + ",\"categories\":[\"STUFFY\"],\"clientId\":\"auth-test\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/api")).andExpect(status().isOk());
    // 센서 저장은 시큐리티를 통과하고 기기 키로 따로 검증한다
    mvc.perform(post("/api/rooms/bukak-617/readings").header("X-Device-Key", "wrong")
            .contentType(MediaType.APPLICATION_JSON).content("{\"co2\":800,\"temp\":24,\"humidity\":45}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("유효하지 않은 기기 키입니다."));
  }

  @Nested
  class Cors {

    private ResultActions preflight(String url, String method, String origin, String headers) throws Exception {
      MockHttpServletRequestBuilder req = options(url).header(HttpHeaders.ORIGIN, origin)
          .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
      if (headers != null) {
        req.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers);
      }
      return mvc.perform(req);
    }

    @Test
    void 허용_출처의_프리플라이트는_인증_없이_200_근로생_API_공개_API_모두() throws Exception {
      for (String origin : new String[] {"https://sumpyo-sku.netlify.app",
          "https://deploy-preview-12--sumpyo-sku.netlify.app", "http://localhost:5173"}) {
        preflight("/api/talks", "GET", origin, "authorization, x-demo-login")
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                containsString("authorization")))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        preflight("/api/complaints", "POST", origin, "content-type")
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
      }
      preflight("/api/talks/1/replies/2", "DELETE", "https://sumpyo-sku.netlify.app", "authorization")
          .andExpect(status().isOk());
    }

    @Test
    void 실제_요청에도_허용_헤더() throws Exception {
      mvc.perform(get("/api/buildings").header(HttpHeaders.ORIGIN, "https://sumpyo-sku.netlify.app"))
          .andExpect(status().isOk())
          .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://sumpyo-sku.netlify.app"));
    }

    @Test
    void 허용_안_된_출처_헤더는_거부_허용_헤더_없음() throws Exception {
      for (String origin : new String[] {"https://evil.example", "https://sumpyo-sku.netlify.app.evil.example",
          "https://deploy-preview-1--other.netlify.app"}) {
        preflight("/api/talks", "GET", origin, "authorization")
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
      }
      mvc.perform(get("/api/buildings").header(HttpHeaders.ORIGIN, "https://evil.example"))
          .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
      preflight("/api/talks", "GET", "https://sumpyo-sku.netlify.app", "x-device-key").andExpect(status().isForbidden());
    }
  }
}
