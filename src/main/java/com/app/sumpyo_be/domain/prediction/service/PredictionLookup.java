package com.app.sumpyo_be.domain.prediction.service;

import com.app.sumpyo_be.domain.prediction.entity.Prediction;
import com.app.sumpyo_be.domain.prediction.repository.PredictionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * predict.py 가 넣은 예측값 (10분마다 방별로 120분 뒤까지 5분 간격, 창문 닫힌 상태 가정) 조회.
 * 어떤 시각의 값 = 그 시각 ±10분 안의 예측 중 가장 가까운 것 (같은 target_at 이면 created_at 최신, 같은 거리면 앞쪽).
 */
@Component
@RequiredArgsConstructor
public class PredictionLookup {

  /** 5분 간격이라 보통 2분 30초 안에 있다 */
  public static final Duration MATCH = Duration.ofMinutes(10);

  private final PredictionRepository predictionRepository;

  public Optional<Integer> co2At(Long roomId, LocalDateTime at) {
    List<Prediction> candidates = predictionRepository.findLatestForChart(roomId,
        at.minus(MATCH), at.plus(MATCH).plusNanos(1));
    return candidates.stream()
        .filter(p -> p.getCo2() != null)
        .min(Comparator.comparing((Prediction p) -> Duration.between(p.getTargetAt(), at).abs())
            .thenComparing(Prediction::getTargetAt))
        .map(Prediction::getCo2);
  }
}
