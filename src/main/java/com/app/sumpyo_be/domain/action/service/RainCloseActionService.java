package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.outdoor.service.RainForecast;
import com.app.sumpyo_be.domain.prediction.entity.Prediction;
import com.app.sumpyo_be.domain.prediction.repository.PredictionRepository;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비 예보 창문 닫기 조치 (10분마다, RainCloseScheduler).
 * 창문이 열린 방(환기 중이고 창문을 아직 닫지 않은 조치가 있는 방)에서 다음 1시간 안에 비 예보면
 * CLOSE·HUMIDITY 예측 조치를 PENDING 으로 만든다. 같은 방에 대기 중인 CLOSE 조치가 있으면 만들지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RainCloseActionService {

  static final Duration RAIN_WITHIN = Duration.ofHours(1);
  /** 비 예상 시각의 습도 예측을 찾는 범위 */
  static final Duration PREDICTION_MATCH = Duration.ofMinutes(10);

  private final RainForecast rainForecast;
  private final ActionRepository actionRepository;
  private final PredictionRepository predictionRepository;
  private final SensorReadingRepository readingRepository;

  /** @return 새로 만든 조치 수 */
  @Transactional
  public int createIfRain(LocalDateTime now) {
    Optional<LocalDateTime> rainAt = rainForecast.firstRainWithin(now, RAIN_WITHIN);
    if (rainAt.isEmpty()) {
      return 0;
    }
    int created = 0;
    for (Room room : actionRepository.findRoomsWithOpenWindow()) {
      if (actionRepository.existsByRoomIdAndRecommendAndStatus(room.getId(), Recommend.CLOSE, ActionStatus.PENDING)) {
        continue;
      }
      actionRepository.save(Action.rainClose(room, humidityAt(room.getId(), rainAt.get(), now), rainAt.get(), now));
      created++;
    }
    if (created > 0) {
      log.info("비 예보 창문 닫기 조치 {}건 생성 (비 예상 {})", created, rainAt.get());
    }
    return created;
  }

  /** 비 예상 시각 ±10분의 가장 가까운 습도 예측, 없으면 최근 10분 측정 습도 */
  private Integer humidityAt(Long roomId, LocalDateTime at, LocalDateTime now) {
    List<Prediction> predictions = predictionRepository.findLatestForChart(roomId,
        at.minus(PREDICTION_MATCH), at.plus(PREDICTION_MATCH).plusNanos(1));
    return predictions.stream()
        .filter(p -> p.getHumidity() != null)
        .min(Comparator.comparing((Prediction p) -> Duration.between(p.getTargetAt(), at).abs()))
        .map(Prediction::getHumidity)
        .orElseGet(() -> readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW))
            .stream().findFirst().map(ReadingSnapshot::getHumidity).orElse(null));
  }
}
