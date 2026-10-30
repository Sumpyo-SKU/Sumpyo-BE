package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환기 자동 완료. 환기 중(IN_PROGRESS)인 OPEN 조치(체크리스트 조치 제외)를
 * CO2 가 800 이하로 떨어졌거나 시작 후 60분이 지나면 DONE 으로 바꾼다 (auto_completed = true).
 * 결과 CO2 = 최근 10분 내 최신 측정 (없으면 null).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionAutoCompleteService {

  /** CO2 가 이 값 이하로 떨어지면 환기 끝 */
  static final int DONE_CO2 = 800;
  /** 시작 후 이 시간이 지나면 CO2 와 상관없이 끝 */
  static final Duration MAX_DURATION = Duration.ofMinutes(60);

  private final ActionRepository actionRepository;
  private final SensorReadingRepository readingRepository;

  /** @return 자동 완료한 조치 수 */
  @Transactional
  public int completeDue(LocalDateTime now) {
    List<Action> actions = actionRepository.findAutoCompleteCandidates();
    if (actions.isEmpty()) {
      return 0;
    }
    List<Long> roomIds = actions.stream().map(a -> a.getRoom().getId()).distinct().toList();
    Map<Long, Integer> latestCo2 = readingRepository.findLatestSince(roomIds, now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream()
        .filter(r -> r.getCo2() != null)
        .collect(Collectors.toMap(ReadingSnapshot::getRoomId, ReadingSnapshot::getCo2, (a, b) -> a));

    int completed = 0;
    for (Action a : actions) {
      Integer co2 = latestCo2.get(a.getRoom().getId());
      if (isDue(a.getStartedAt() != null ? a.getStartedAt() : a.getCreatedAt(), a.getCo2Before(), co2, now)) {
        a.autoComplete(co2, now);
        completed++;
      }
    }
    if (completed > 0) {
      log.info("환기 자동 완료 {}건", completed);
    }
    return completed;
  }

  /**
   * 시작 후 60분이 지났거나, CO2 가 800 이하로 "떨어졌으면" true.
   * 시작할 때부터 800 이하였으면(정상 상태에서 환기 하기) CO2 로는 끝내지 않고 60분 규칙만 적용한다
   * (안 그러면 누르자마자 1분 안에 끝나 버림). 시작 CO2 를 모르면 지금 값만 본다.
   */
  static boolean isDue(LocalDateTime startedAt, Integer co2Before, Integer currentCo2, LocalDateTime now) {
    if (!startedAt.plus(MAX_DURATION).isAfter(now)) {
      return true;
    }
    boolean dropped = co2Before == null || co2Before > DONE_CO2;
    return dropped && currentCo2 != null && currentCo2 <= DONE_CO2;
  }
}
