package com.app.sumpyo_be.domain.action.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 환기 자동 완료. 끄려면 sumpyo.action.auto-complete-enabled=false */
@Component
@ConditionalOnProperty(prefix = "sumpyo.action", name = "auto-complete-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ActionAutoCompleteScheduler {

  private final ActionAutoCompleteService autoCompleteService;
  private final Clock clock;

  // 시뮬레이터(매분 0초) 측정 뒤, 결과 CO2 측정(20초)과 겹치지 않게
  @Scheduled(cron = "30 * * * * *", zone = "Asia/Seoul")
  public void tick() {
    autoCompleteService.completeDue(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
