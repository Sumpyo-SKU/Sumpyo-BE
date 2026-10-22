package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 민원 조치 결과 CO2. 창문을 막 열고 바로 재면 아직 안 떨어졌으므로 measure_at(창문 연 시각 + 추천 분)에 잰다.
 * 값 = measure_at 이전 10분 안의 가장 늦은 측정. 30분이 지나도 측정이 없으면(센서 끊김) 포기하고 null 로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionMeasureService {

  static final Duration READING_WINDOW = Duration.ofMinutes(10);
  static final Duration GIVE_UP_AFTER = Duration.ofMinutes(30);

  private final ActionRepository actionRepository;
  private final SensorReadingRepository readingRepository;

  /** @return 결과를 기록한 조치 수 */
  @Transactional
  public int measureDue(LocalDateTime now) {
    int measured = 0;
    for (Action a : actionRepository.findMeasureDue(now, now.minus(GIVE_UP_AFTER))) {
      if (measure(a)) {
        measured++;
      }
    }
    if (measured > 0) {
      log.info("조치 결과 CO2 {}건 기록", measured);
    }
    return measured;
  }

  /** measure_at 시각의 측정으로 결과 기록. 측정이 없으면 false */
  boolean measure(Action a) {
    LocalDateTime at = a.getMeasureAt();
    return readingRepository.findFirstByRoomIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
            a.getRoom().getId(), at.minus(READING_WINDOW), at)
        .map(SensorReading::getCo2)
        .map(co2 -> {
          a.recordResult(co2);
          return true;
        })
        .orElse(false);
  }
}
