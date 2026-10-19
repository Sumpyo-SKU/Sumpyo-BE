package com.app.sumpyo_be.global.room.service;

import com.app.sumpyo_be.global.air.AirQualityJudge;
import com.app.sumpyo_be.global.air.AirQualityJudge.StatusResult;
import com.app.sumpyo_be.global.air.Thresholds;
import com.app.sumpyo_be.global.prediction.dto.PredictionPoint;
import com.app.sumpyo_be.global.prediction.repository.PredictionRepository;
import com.app.sumpyo_be.global.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.global.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.global.room.entity.Room;
import com.app.sumpyo_be.global.room.entity.SensorLevel;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 방의 현재 상태를 한 번에 계산한다. 방 수와 상관없이 쿼리 2번
 * (최신 측정값 1번 + 30분 예측 1번). 센서 없는 방은 쿼리 대상에서 뺀다.
 * TH(온습도만) 방은 온습도는 내려주지만 CO2 가 없으므로 상태는 NO_SENSOR.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomConditionService {

  private final SensorReadingRepository readingRepository;
  private final PredictionRepository predictionRepository;

  public Map<Long, RoomCondition> evaluate(Collection<Room> rooms, Thresholds thresholds, LocalDateTime now) {
    List<Long> sensorRoomIds = rooms.stream()
        .filter(r -> r.getSensorLevel() != SensorLevel.NONE)
        .map(Room::getId)
        .toList();

    Map<Long, ReadingSnapshot> latest = Map.of();
    Map<Long, List<PredictionPoint>> predictions = Map.of();
    if (!sensorRoomIds.isEmpty()) {
      latest = readingRepository.findLatestSince(sensorRoomIds, now.minus(AirQualityJudge.FRESH_WINDOW))
          .stream()
          // 같은 시각 측정이 2건 이상이면 하나만
          .collect(Collectors.toMap(ReadingSnapshot::roomId, Function.identity(), (a, b) -> a));
      predictions = predictionRepository
          .findLatestPoints(sensorRoomIds, now, now.plus(AirQualityJudge.PREDICTION_HORIZON))
          .stream()
          .collect(Collectors.groupingBy(PredictionPoint::roomId));
    }

    Map<Long, RoomCondition> result = new HashMap<>();
    for (Room room : rooms) {
      ReadingSnapshot reading = latest.get(room.getId());
      StatusResult status = AirQualityJudge.status(room.getSensorLevel(),
          reading == null ? null : reading.co2(), predictions.get(room.getId()), thresholds.co2(), now);
      result.put(room.getId(), new RoomCondition(reading, status.status(), status.minutesToExceed()));
    }
    return result;
  }

  public RoomCondition evaluate(Room room, Thresholds thresholds, LocalDateTime now) {
    return evaluate(List.of(room), thresholds, now).get(room.getId());
  }
}
