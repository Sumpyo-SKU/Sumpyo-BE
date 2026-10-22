package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.service.MeService;
import com.app.sumpyo_be.domain.notification.entity.NotificationType;
import com.app.sumpyo_be.domain.notification.repository.NotificationRepository;
import com.app.sumpyo_be.domain.reading.dto.projection.LastMeasured;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1분마다 스캔해서 지금 환기·곧 환기·센서 끊김 알림을 만든다 (조치는 predict.py 가 DB 에 직접 넣으므로 이벤트가 아니라 스캔).
 * 모든 생성은 dedup_key 로 멱등이라 같은 시각에 두 번 돌려도 중복이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScanService {

  /** CO2 가 기준 근처에서 오르내리면 에피소드가 자꾸 새로 생기므로, 같은 방 지금 환기 알림은 이 간격 안에 한 번만 */
  static final Duration VENTILATION_NOW_COOLDOWN = Duration.ofMinutes(30);
  /** 곧 환기: 설정 가능한 가장 긴 알림 시점까지 조회해 두고 사람마다 alertLeadMinutes 로 거른다 */
  static final Duration MAX_LEAD = Duration.ofMinutes(Collections.max(MeService.ALERT_LEAD_MINUTES));

  private final RoomRepository roomRepository;
  private final SensorReadingRepository readingRepository;
  private final ActionRepository actionRepository;
  private final NotificationRepository notificationRepository;
  private final NotificationPublisher publisher;

  /** @return 새로 만든 알림 수 */
  @Transactional
  public int scan(LocalDateTime now) {
    Recipients recipients = new Recipients();
    int created = ventilationNow(now, recipients) + ventilationSoon(now, recipients) + sensorError(now, recipients);
    if (created > 0) {
      log.info("알림 {}건 생성", created);
    }
    return created;
  }

  /** 최근 10분 내 최신 CO2 가 받는 사람의 기준 이상(OVER)인 방 */
  private int ventilationNow(LocalDateTime now, Recipients recipients) {
    List<Room> rooms = roomRepository.findWithBuildingBySensorLevelIn(List.of(SensorLevel.FULL, SensorLevel.SIM));
    if (rooms.isEmpty()) {
      return 0;
    }
    Map<Long, ReadingSnapshot> latest = readingRepository
        .findLatestSince(rooms.stream().map(Room::getId).toList(), now.minus(AirQualityJudge.FRESH_WINDOW)).stream()
        .collect(Collectors.toMap(ReadingSnapshot::getRoomId, Function.identity(), (a, b) -> a));

    int created = 0;
    for (Room room : rooms) {
      ReadingSnapshot reading = latest.get(room.getId());
      if (reading == null || reading.getCo2() == null) {
        continue;
      }
      int co2 = reading.getCo2();
      List<Member> to = recipients.of(room).stream()
          .filter(m -> m.isNotifyVentilation() && co2 >= Thresholds.from(m).co2())
          .filter(m -> !notificationRepository.existsByMemberIdAndRoomIdAndTypeAndCreatedAtGreaterThanEqual(
              m.getId(), room.getId(), NotificationType.VENTILATION_NOW, now.minus(VENTILATION_NOW_COOLDOWN)))
          .toList();
      if (to.isEmpty()) {
        continue;
      }
      Long actionId = actionRepository.findTopByRoomIdAndStatusInOrderByCreatedAtDesc(room.getId(), ActionStatus.WAITING)
          .map(Action::getId).orElse(null);
      Map<Integer, LocalDateTime> lastBelow = new HashMap<>();   // 기준값별 (사람마다 기준이 다를 수 있음)
      for (Member m : to) {
        int threshold = Thresholds.from(m).co2();
        LocalDateTime below = lastBelow.computeIfAbsent(threshold,
            t -> readingRepository.findLastBelow(room.getId(), t));
        created += publish(m, NotificationDraft.ventilationNow(room, co2, actionId, below), now);
      }
    }
    return created;
  }

  /** 대기(PENDING) 예측 조치의 기준 초과 예상 시각까지 남은 분이 받는 사람의 alertLeadMinutes 이내 */
  private int ventilationSoon(LocalDateTime now, Recipients recipients) {
    int created = 0;
    for (Action action : actionRepository.findExpectedBetween(ActionSource.PREDICTION, ActionStatus.PENDING,
        now, now.plus(MAX_LEAD))) {
      // ppm 문구라 CO2 예측만 (metric 이 비어 있으면 CO2 로 본다)
      if (action.getPredictedValue() == null || (action.getMetric() != null && action.getMetric() != Metric.CO2)) {
        continue;
      }
      int minutes = minutesUntil(now, action.getExpectedAt());
      NotificationDraft draft = NotificationDraft.ventilationSoon(action, minutes);
      for (Member m : recipients.of(action.getRoom())) {
        if (m.isNotifyVentilation() && m.isAlertOn() && minutes <= m.alertLeadMinutesOrDefault()) {
          created += publish(m, draft, now);
        }
      }
    }
    return created;
  }

  /** 실측(FULL) 센서 방의 마지막 측정이 10분보다 오래됨. SIM 은 서버가 만드는 값이라 제외, 측정이 한 번도 없는 방도 제외 */
  private int sensorError(LocalDateTime now, Recipients recipients) {
    List<Room> rooms = roomRepository.findWithBuildingBySensorLevelIn(List.of(SensorLevel.FULL));
    if (rooms.isEmpty()) {
      return 0;
    }
    Map<Long, LocalDateTime> last = readingRepository.findLastMeasuredAt(rooms.stream().map(Room::getId).toList())
        .stream().collect(Collectors.toMap(LastMeasured::getRoomId, LastMeasured::getMeasuredAt));
    int created = 0;
    for (Room room : rooms) {
      LocalDateTime lastAt = last.get(room.getId());
      if (lastAt == null || lastAt.isAfter(now.minus(AirQualityJudge.FRESH_WINDOW))) {
        continue;
      }
      NotificationDraft draft = NotificationDraft.sensorError(room, lastAt);
      for (Member m : recipients.of(room)) {
        if (m.isNotifySensorError()) {
          created += publish(m, draft, now);
        }
      }
    }
    return created;
  }

  /** 남은 분, 올림 (14분 10초 → 15분) */
  static int minutesUntil(LocalDateTime now, LocalDateTime at) {
    return (int) Math.ceilDiv(Duration.between(now, at).toSeconds(), 60);
  }

  private int publish(Member m, NotificationDraft draft, LocalDateTime now) {
    return publisher.publish(m, draft, now) ? 1 : 0;
  }

  /** 스캔 한 번 동안 (건물, 층) 별 받는 사람을 한 번만 조회 */
  private class Recipients {
    private final Map<String, List<Member>> cache = new HashMap<>();

    List<Member> of(Room room) {
      return cache.computeIfAbsent(room.getBuilding().getId() + ":" + room.getFloor(),
          k -> publisher.recipients(room));
    }
  }
}
