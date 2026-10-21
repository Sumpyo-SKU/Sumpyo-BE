package com.app.sumpyo_be.domain.outdoor.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매시 10분 바깥 공기 수집. 끄려면 sumpyo.outdoor.collect-enabled=false */
@Component
@ConditionalOnProperty(prefix = "sumpyo.outdoor", name = "collect-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutdoorScheduler {

  private final OutdoorCollector collector;
  private final Clock clock;

  @Scheduled(cron = "0 10 * * * *", zone = "Asia/Seoul")
  public void tick() {
    collector.collect(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
