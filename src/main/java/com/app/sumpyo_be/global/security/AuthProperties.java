package com.app.sumpyo_be.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정.
 * @param jwtSecret     JWT_SECRET. HS256 서명 키, 32바이트 이상이어야 하고 없으면 앱이 뜨지 않는다
 * @param tokenValidity 액세스 토큰 유효기간 (기본 7일, 리프레시 토큰 없음)
 */
@ConfigurationProperties(prefix = "sumpyo.auth")
public record AuthProperties(String jwtSecret, Duration tokenValidity) {

  public static final int MIN_SECRET_BYTES = 32;

  public AuthProperties {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException("JWT_SECRET 이 없거나 " + MIN_SECRET_BYTES + "바이트보다 짧습니다. "
          + "예: openssl rand -base64 48 로 만든 값을 넣어주세요.");
    }
    tokenValidity = tokenValidity == null ? Duration.ofDays(7) : tokenValidity;
  }
}
