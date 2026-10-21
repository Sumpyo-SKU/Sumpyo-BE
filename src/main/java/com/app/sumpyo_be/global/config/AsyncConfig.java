package com.app.sumpyo_be.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 사용 (캠퍼스톡 추천 답변 비동기 생성). 실행기는 Spring Boot 기본 applicationTaskExecutor */
@Configuration
@EnableAsync
public class AsyncConfig {
}
