package com.app.sumpyo_be.domain.room.service;

import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.service.ThresholdService;
import com.app.sumpyo_be.domain.prediction.entity.Prediction;
import com.app.sumpyo_be.domain.prediction.repository.PredictionRepository;
import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.dto.LatestReadingResponse;
import com.app.sumpyo_be.domain.room.dto.PredictionSeriesResponse;
import com.app.sumpyo_be.domain.room.dto.ReadingSeriesResponse;
import com.app.sumpyo_be.domain.room.dto.RoomResponse;
import com.app.sumpyo_be.domain.room.dto.RoomResponse.CurrentClass;
import com.app.sumpyo_be.domain.room.dto.RoomResponse.NextClass;
import com.app.sumpyo_be.domain.room.dto.RoomResponse.PendingAction;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService.CurrentAndNext;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

  /** 그래프 조회 최대 구간 */
  static final Duration MAX_RANGE = Duration.ofDays(31);
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final RoomRepository roomRepository;
  private final RoomConditionService roomConditionService;
  private final ThresholdService thresholdService;
  private final ActionRepository actionRepository;
  private final SensorReadingRepository readingRepository;
  private final PredictionRepository predictionRepository;
  private final ClassScheduleService classScheduleService;
  private final Clock clock;

  public RoomResponse getRoom(Long roomId) {
    Room room = roomRepository.findWithBuildingById(roomId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    Thresholds t = thresholdService.current();
    RoomCondition c = roomConditionService.evaluate(room, t, now);

    boolean ventilating = actionRepository.existsByRoomIdAndStatus(roomId, ActionStatus.IN_PROGRESS);
    PendingAction pendingAction = actionRepository
        .findTopByRoomIdAndStatusInOrderByCreatedAtDesc(roomId, ActionStatus.WAITING)
        .map(a -> new PendingAction(a.getId(),
            AirQualityJudge.pendingActionMessage(c.status(), c.minutesToExceed(), a.getSummary())))
        .orElse(null);

    Integer score = AirQualityJudge.comfortScore(c.co2(), c.temp(), c.humidity(), t);
    List<ClassSchedule> today = classScheduleService.classesOn(roomId, now.toLocalDate());
    CurrentAndNext cn = ClassScheduleService.currentAndNext(today, now.toLocalTime());
    ClassSchedule cur = cn.current();
    ClassSchedule next = cn.next();

    return new RoomResponse(
        room.getId(), room.getBuilding().getName(), room.getNumber(), room.getFloor(),
        room.getSensorLevel().isSimulated(),
        c.co2(), c.temp(), c.humidity(),
        score,
        AirQualityJudge.comfortLevel(score),
        AirQualityJudge.co2Level(c.co2(), t.co2()),
        AirQualityJudge.co2OverBy(c.co2(), t.co2()),
        AirQualityJudge.tempLabel(c.temp(), t.tempMin(), t.tempMax()),
        AirQualityJudge.humidityLabel(c.humidity(), t.humidityMin(), t.humidityMax()),
        ventilating,
        pendingAction,
        cur == null ? null : new CurrentClass(cur.getCourseName(), hhmm(cur.getStartTime()),
            hhmm(cur.getEndTime())),
        next == null ? null : new NextClass(next.getCourseName(), hhmm(next.getStartTime()),
            hhmm(next.getEndTime()), AirQualityJudge.nextClassTip(c.status())));
  }

  /** 마지막 측정값. 10분 넘게 지났으면 sensorOnline=false (값은 그대로 내려줌) */
  public LatestReadingResponse getLatestReading(Long roomId) {
    Room room = findRoom(roomId);
    LocalDateTime now = LocalDateTime.now(clock);
    Thresholds t = thresholdService.current();
    return readingRepository.findTopByRoomOrderByMeasuredAtDesc(room)
        .map(r -> {
          Integer score = AirQualityJudge.comfortScore(r.getCo2(), r.getTemp(), r.getHumidity(), t);
          return new LatestReadingResponse(roomId, r.getCo2(), r.getTemp(), r.getHumidity(), score,
              AirQualityJudge.comfortLevel(score), r.getMeasuredAt(),
              !r.getMeasuredAt().isBefore(now.minus(AirQualityJudge.FRESH_WINDOW)));
        })
        .orElseGet(() -> new LatestReadingResponse(roomId, null, null, null, null, null, null, false));
  }

  /** 그래프 실측. from/to 가 없으면 오늘 0시 ~ 지금 */
  public ReadingSeriesResponse getReadings(Long roomId, LocalDateTime from, LocalDateTime to, int interval) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime start = from != null ? from : now.toLocalDate().atStartOfDay();
    LocalDateTime end = to != null ? to : now;
    validateRange(start, end);
    Room room = findRoom(roomId);

    List<SensorReading> readings = readingRepository
        .findByRoomAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(room, start, end);
    // 구간 경계를 정각에 맞추려고 그날 0시를 기준점으로 쓴다 (09:00, 09:05, ...)
    return new ReadingSeriesResponse(interval,
        averageByInterval(readings, start.toLocalDate().atStartOfDay(), Duration.ofMinutes(interval)));
  }

  /** 그래프 예측. from/to 가 없으면 지금 ~ 내일 0시 */
  public PredictionSeriesResponse getPredictions(Long roomId, LocalDateTime from, LocalDateTime to) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime start = from != null ? from : now;
    LocalDateTime end = to != null ? to : now.toLocalDate().plusDays(1).atStartOfDay();
    validateRange(start, end);
    findRoom(roomId);

    List<Prediction> predictions = predictionRepository.findLatestForChart(roomId, start, end);
    // 가장 최근에 만든 예측의 모델 버전
    String modelVersion = predictions.stream()
        .max(Comparator.comparing(Prediction::getCreatedAt))
        .map(Prediction::getModelVersion)
        .orElse(null);
    return new PredictionSeriesResponse(modelVersion, predictions.stream()
        .map(p -> new PredictionSeriesResponse.Point(p.getTargetAt(), p.getCo2(), p.getTemp(), p.getHumidity()))
        .toList());
  }

  private Room findRoom(Long roomId) {
    return roomRepository.findById(roomId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_NOT_FOUND));
  }

  private static void validateRange(LocalDateTime from, LocalDateTime to) {
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new CustomException(GlobalErrorCode.INVALID_TIME_RANGE);
    }
  }

  /** origin 기준 interval 구간별 평균. 값이 없는 항목은 평균에서 빼고, 측정이 없는 구간은 점을 만들지 않는다. */
  static List<ReadingSeriesResponse.Point> averageByInterval(List<SensorReading> readings,
      LocalDateTime origin, Duration interval) {
    long seconds = interval.getSeconds();
    Map<LocalDateTime, Averager[]> buckets = new TreeMap<>();   // [co2, temp, humidity]
    for (SensorReading r : readings) {
      long index = Duration.between(origin, r.getMeasuredAt()).getSeconds() / seconds;
      Averager[] acc = buckets.computeIfAbsent(origin.plusSeconds(index * seconds),
          k -> new Averager[] {new Averager(), new Averager(), new Averager()});
      acc[0].add(r.getCo2());
      acc[1].add(r.getTemp());
      acc[2].add(r.getHumidity());
    }
    List<ReadingSeriesResponse.Point> points = new ArrayList<>(buckets.size());
    buckets.forEach((at, acc) ->
        points.add(new ReadingSeriesResponse.Point(at, acc[0].average(), acc[1].average(), acc[2].average())));
    return points;
  }

  private static final class Averager {
    private long sum;
    private int count;

    void add(Integer value) {
      if (value != null) {
        sum += value;
        count++;
      }
    }

    Integer average() {
      return count == 0 ? null : (int) Math.round((double) sum / count);
    }
  }

  private static String hhmm(LocalTime time) {
    return time.format(HH_MM);
  }
}
