package com.app.sumpyo_be.global.security;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 시큐리티 필터 단계의 401·403 도 공통 응답 { success:false, code, message, data:null } 으로 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

  private final JsonMapper jsonMapper;

  /** 토큰도 없고 데모 모드도 아님 → 401 */
  public AuthenticationEntryPoint loginRequired() {
    return (request, response, e) -> write(response, GlobalErrorCode.LOGIN_REQUIRED);
  }

  public AccessDeniedHandler accessDenied() {
    return (request, response, e) -> write(response, GlobalErrorCode.ACCESS_DENIED);
  }

  /** 만료·위조된 토큰 → 401 */
  void invalidToken(HttpServletResponse response) throws IOException {
    write(response, GlobalErrorCode.INVALID_TOKEN);
  }

  private void write(HttpServletResponse response, GlobalErrorCode code) throws IOException {
    response.setStatus(code.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(jsonMapper.writeValueAsString(
        BaseResponse.error(code.getStatus().value(), code.getMessage())));
  }
}
