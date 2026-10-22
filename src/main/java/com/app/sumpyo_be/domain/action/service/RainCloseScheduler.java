package com.app.sumpyo_be.domain.action.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 10분마다 비 예보 창문 닫기 조치. 끄려면 sumpyo.action.rain-close-enabled=false */
@Component
@ConditionalOnProperty(prefix = "sumpyo.action", name = "rain-close-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RainCloseScheduler {

  private final RainCloseActionService rainCloseActionService;
  private final Clock clock;

  @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
  public void tick() {
    rainCloseActionService.createIfRain(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
