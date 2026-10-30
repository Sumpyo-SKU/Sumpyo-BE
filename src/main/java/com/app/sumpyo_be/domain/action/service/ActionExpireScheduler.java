package com.app.sumpyo_be.domain.action.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 10분마다 지난 예측 조치 만료 (예상 시각이 1시간 넘게 지난 대기 중 예측 조치 → EXPIRED). 끄려면 sumpyo.action.expire-enabled=false */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sumpyo.action", name = "expire-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ActionExpireScheduler {

  private final ActionService actionService;
  private final Clock clock;

  // 비 예보 창문 닫기(매 10분 0초)와 겹치지 않게 40초
  @Scheduled(cron = "40 */10 * * * *", zone = "Asia/Seoul")
  public void tick() {
    int expired = actionService.expireStalePredictions(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    if (expired > 0) {
      log.info("지난 예측 조치 {}건 만료", expired);
    }
  }
}
