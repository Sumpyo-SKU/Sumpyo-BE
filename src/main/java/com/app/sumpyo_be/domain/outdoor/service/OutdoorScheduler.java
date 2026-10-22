package com.app.sumpyo_be.domain.outdoor.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 10분·40분 바깥 공기 수집. 끄려면 sumpyo.outdoor.collect-enabled=false.
 * 에어코리아 정시 값은 15~30분쯤 늦게 채워져서, 10분에는 그 전 시각 값이 들어가고 40분에 같은 관측 시각 행이 최신 값으로 갱신된다.
 */
@Component
@ConditionalOnProperty(prefix = "sumpyo.outdoor", name = "collect-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutdoorScheduler {

  private final OutdoorCollector collector;
  private final Clock clock;

  @Scheduled(cron = "0 10,40 * * * *", zone = "Asia/Seoul")
  public void tick() {
    collector.collect(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
