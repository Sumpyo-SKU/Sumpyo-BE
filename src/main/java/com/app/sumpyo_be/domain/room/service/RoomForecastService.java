package com.app.sumpyo_be.domain.room.service;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.member.service.ThresholdService;
import com.app.sumpyo_be.domain.prediction.service.PredictionLookup;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.dto.response.NextClassForecastResponse;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService.CurrentAndNext;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수업 중 예보. predict.py 가 10분마다 방별로 120분 뒤까지 5분 간격, "창문 닫힌 상태" 가정으로 예측을 넣는다.
 * 대상 수업: 진행 중이면 그 수업, 아니면 오늘 남은 다음 수업 (휴강 제외).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomForecastService {

  static final String CONDITION = "창문을 닫아두면";
  static final String BEFORE_CLASS = "수업 전에 환기가 필요해요";
  static final String LATE_IN_CLASS = "수업 후반엔 환기가 필요해요";
  static final String COMFORTABLE = "수업 내내 쾌적할 거예요";
  static final String COLLECTING = "예측 데이터를 모으는 중이에요";
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final MemberAccess memberAccess;
  private final RoomRepository roomRepository;
  private final ClassScheduleService classScheduleService;
  private final PredictionLookup predictionLookup;
  private final SensorReadingRepository readingRepository;
  private final ThresholdService thresholdService;
  private final Clock clock;

  /** 대상 수업이 없으면 null */
  public NextClassForecastResponse nextClass(Long roomId) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    roomRepository.findById(roomId).orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now(clock);
    CurrentAndNext cn = ClassScheduleService.currentAndNext(
        classScheduleService.classesOn(roomId, now.toLocalDate()), now.toLocalTime());
    ClassSchedule target = cn.current() != null ? cn.current() : cn.next();
    if (target == null) {
      return null;
    }
    LocalDateTime start = now.toLocalDate().atTime(target.getStartTime());
    LocalDateTime end = now.toLocalDate().atTime(target.getEndTime());
    int threshold = thresholdService.current().co2();
    NextClassForecastResponse.NextClassForecastResponseBuilder res = NextClassForecastResponse.builder()
        .courseName(target.getCourseName())
        .start(start.format(HH_MM))
        .end(end.format(HH_MM))
        .threshold(threshold)
        .condition(CONDITION);

    Integer co2AtEnd = nearestPrediction(roomId, end);
    if (co2AtEnd == null) {
      return res.headline(COLLECTING).build();
    }
    // 이미 시작한 수업은 시작 시각 예측 대신 지금 값으로 본다
    Integer co2AtStart = start.isAfter(now) ? nearestPrediction(roomId, start) : currentCo2(roomId, now);
    boolean startOver = co2AtStart != null && co2AtStart >= threshold;
    boolean endOver = co2AtEnd >= threshold;
    return res.co2AtEnd(co2AtEnd)
        .needsVentilation(startOver || endOver)
        .headline(startOver ? BEFORE_CLASS : endOver ? LATE_IN_CLASS : COMFORTABLE)
        .build();
  }

  private Integer nearestPrediction(Long roomId, LocalDateTime at) {
    return predictionLookup.co2At(roomId, at).orElse(null);
  }

  private Integer currentCo2(Long roomId, LocalDateTime now) {
    return readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW)).stream()
        .findFirst().map(ReadingSnapshot::getCo2).orElse(null);
  }
}
