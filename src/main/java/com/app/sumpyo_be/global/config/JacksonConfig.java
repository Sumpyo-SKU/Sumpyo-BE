package com.app.sumpyo_be.global.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 응답 시각 형식 통일: 모든 LocalDateTime 을 "yyyy-MM-dd'T'HH:mm:ss" (KST, 초까지, 소수점 없음).
 * DB 의 마이크로초(측정 시각 등)나 정각의 초 생략("14:00")이 응답마다 달라지지 않게 한다.
 */
@Configuration
public class JacksonConfig {

  public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

  @Bean
  public JsonMapperBuilderCustomizer localDateTimeFormat() {
    SimpleModule module = new SimpleModule("sumpyo-date-time");
    module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
    return builder -> builder.addModule(module);
  }
}
