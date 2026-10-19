package com.app.sumpyo_be.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  /** 모든 시간은 KST. 서비스는 LocalDateTime.now(clock) 으로 현재 시각을 얻는다 (테스트에서 고정 가능). */
  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
