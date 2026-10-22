package com.app.sumpyo_be.domain.action.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 measure_at 이 지난 완료 조치의 결과 CO2 기록. 끄려면 sumpyo.action.measure-enabled=false */
@Component
@ConditionalOnProperty(prefix = "sumpyo.action", name = "measure-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ActionMeasureScheduler {

  private final ActionMeasureService measureService;
  private final Clock clock;

  // 시뮬레이터(매분 0초) 측정 뒤
  @Scheduled(cron = "20 * * * * *", zone = "Asia/Seoul")
  public void tick() {
    measureService.measureDue(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
