package com.app.sumpyo_be.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 데모 모드(DEMO_HEADER_LOGIN=true) + 시큐리티 필터: 토큰이 있으면 토큰, 없으면 worker01 / X-Demo-Login */
@SpringBootTest(properties = {
    "sumpyo.simulator.enabled=false",
    "sumpyo.notification.scan-enabled=false",
    "sumpyo.outdoor.collect-enabled=false", "sumpyo.action.rain-close-enabled=false", "sumpyo.action.expire-enabled=false", "sumpyo.action.auto-complete-enabled=false", "sumpyo.outdoor.api-key=",
    "sumpyo.action.measure-enabled=false",
    "sumpyo.demo.password=test-demo-password",
    "sumpyo.demo.header-login=true",
    "sumpyo.llm.provider=", "sumpyo.llm.api-key=", "sumpyo.llm.model=",
    "DB_USERNAME=unused", "DB_PASSWORD=unused", "DEVICE_KEY_517=test-device-key"
})
@AutoConfigureMockMvc
@Testcontainers
class DemoModeSecurityTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void 토큰이_없으면_worker01_헤더로_worker02_토큰이_있으면_항상_토큰() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.data.loginId").value("worker01"));
    mvc.perform(get("/api/me").header("X-Demo-Login", "worker02"))
        .andExpect(jsonPath("$.data.loginId").value("worker02"));
    mvc.perform(get("/api/me").header("X-Demo-Login", "admin")).andExpect(status().isUnauthorized());

    jdbc.update("delete from member where login_id = 'demotest1'");
    String res = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"박근로","loginId":"demotest1","password":"abc12345","passwordConfirm":"abc12345"}"""))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = JsonPath.read(res, "$.data.accessToken");
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Demo-Login", "worker02"))
        .andExpect(jsonPath("$.data.loginId").value("demotest1"));
  }
}
