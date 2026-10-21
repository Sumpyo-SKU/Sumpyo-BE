package com.app.sumpyo_be.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 알림 스캔. 끄려면 sumpyo.notification.scan-enabled=false (테스트는 끄고 scan 을 직접 호출) */
@Component
@ConditionalOnProperty(prefix = "sumpyo.notification", name = "scan-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationScanService scanService;
  private final Clock clock;

  // 시뮬레이터(매분 0초)가 측정값을 넣은 뒤에 돌도록 30초
  @Scheduled(cron = "30 * * * * *", zone = "Asia/Seoul")
  public void tick() {
    scanService.scan(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }
}
