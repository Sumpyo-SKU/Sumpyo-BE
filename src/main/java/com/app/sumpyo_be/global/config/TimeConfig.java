package com.app.sumpyo_be.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  /**
   * 모든 시간은 KST. 서비스는 LocalDateTime.now(clock) 으로 현재 시각을 얻는다 (테스트에서 고정 가능).
   * 밀리초 단위로 끊는다 (Windows 는 100ns 단위라 응답에 소수점 7자리가 찍힘).
   */
  @Bean
  public Clock clock() {
    return Clock.tickMillis(ZoneId.of("Asia/Seoul"));
  }
}
