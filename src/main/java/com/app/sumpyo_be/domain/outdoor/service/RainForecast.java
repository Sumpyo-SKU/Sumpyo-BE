package com.app.sumpyo_be.domain.outdoor.service;

import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.ForecastSlot;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 기상청 초단기예보 강수형태(PTY)를 메모리에만 보관 (매시 수집 때 갱신, 재시작하면 다음 수집까지 없음).
 * 초단기예보는 1시간 단위 예보 시각(HH:00)이라, 지금이 속한 시각 칸부터 본다.
 */
@Component
public class RainForecast {

  /** 수집이 이보다 오래되면 예보를 믿지 않는다 (수집 실패가 이어질 때) */
  static final Duration STALE_AFTER = Duration.ofHours(2);

  private record Snapshot(List<ForecastSlot> slots, LocalDateTime fetchedAt) {}

  private final AtomicReference<Snapshot> latest = new AtomicReference<>();

  public void update(List<ForecastSlot> slots, LocalDateTime fetchedAt) {
    latest.set(new Snapshot(List.copyOf(slots), fetchedAt));
  }

  /** (지금이 속한 시각 칸 ~ now + within] 안에서 처음 비가 오는 예보 시각 */
  public Optional<LocalDateTime> firstRainWithin(LocalDateTime now, Duration within) {
    Snapshot s = latest.get();
    if (s == null || s.fetchedAt().isBefore(now.minus(STALE_AFTER))) {
      return Optional.empty();
    }
    LocalDateTime from = now.truncatedTo(ChronoUnit.HOURS);
    LocalDateTime to = now.plus(within);
    return s.slots().stream()
        .filter(ForecastSlot::isRain)
        .map(ForecastSlot::at)
        .filter(at -> !at.isBefore(from) && !at.isAfter(to))
        .min(LocalDateTime::compareTo);
  }
}
