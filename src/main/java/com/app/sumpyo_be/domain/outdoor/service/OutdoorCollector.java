package com.app.sumpyo_be.domain.outdoor.service;

import com.app.sumpyo_be.domain.outdoor.OutdoorProperties;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.ForecastSlot;
import com.app.sumpyo_be.domain.outdoor.client.OutdoorApiClient.Nowcast;
import com.app.sumpyo_be.domain.outdoor.repository.OutdoorObservationRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 바깥 공기 수집 (매시 10분, OutdoorScheduler). 키·위치가 없거나 호출이 실패하면 건너뛰고 경고만 남긴다.
 * 세 호출은 서로 독립: 하나가 실패해도 나머지는 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutdoorCollector {

  private final OutdoorProperties props;
  private final OutdoorApiClient client;
  private final OutdoorObservationRepository repository;
  private final RainForecast rainForecast;

  /** @return 저장(또는 갱신)했으면 true */
  public boolean collect(LocalDateTime now) {
    if (!props.isReady()) {
      log.warn("바깥 공기 수집 건너뜀: DATA_GO_KR_KEY·WEATHER_NX·WEATHER_NY·AIR_STATION_NAME 중 빈 값이 있습니다.");
      return false;
    }
    // 초단기실황은 정시 자료. 이번 정시 자료가 아직 발표 전이면 한 시간 전 자료
    LocalDateTime thisHour = now.truncatedTo(ChronoUnit.HOURS);
    LocalDateTime observedAt = thisHour;
    Optional<Nowcast> nowcast = call("기상청 초단기실황", () -> client.nowcast(thisHour));
    if (nowcast.isEmpty()) {
      observedAt = thisHour.minusHours(1);
      nowcast = call("기상청 초단기실황", () -> client.nowcast(thisHour.minusHours(1)));
    }
    Optional<Integer> pm10 = call("에어코리아 실시간", client::pm10);
    collectForecast(now);
    if (nowcast.isEmpty() && pm10.isEmpty()) {
      log.warn("바깥 공기 저장 건너뜀: 받은 값이 없습니다.");
      return false;
    }
    repository.upsert(observedAt, nowcast.map(Nowcast::temp).orElse(null),
        nowcast.map(Nowcast::humidity).orElse(null), pm10.orElse(null));
    return true;
  }

  /** 초단기예보는 매시 30분 발표, 15분쯤 뒤부터 제공 → 그 시각이 지난 가장 최근 발표분 */
  private void collectForecast(LocalDateTime now) {
    LocalDateTime available = now.minusMinutes(15);
    LocalDateTime baseAt = available.truncatedTo(ChronoUnit.HOURS).plusMinutes(30);
    if (baseAt.isAfter(available)) {
      baseAt = baseAt.minusHours(1);
    }
    LocalDateTime base = baseAt;
    Optional<List<ForecastSlot>> slots = call("기상청 초단기예보", () -> Optional.of(client.forecast(base)));
    slots.filter(s -> !s.isEmpty()).ifPresent(s -> rainForecast.update(s, now));
  }

  /** 실패하면 경고 로그(예외 종류·메시지만, 키는 URL 에만 있어 남지 않는다) 후 empty */
  private <T> Optional<T> call(String name, java.util.function.Supplier<Optional<T>> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      log.warn("{} 호출 실패: {}", name, e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
