package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.notification.entity.NotificationType;
import com.app.sumpyo_be.domain.room.entity.Room;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 만들 알림 한 건 (받는 사람 제외). 종류별 문구와 dedup_key 규칙은 여기서만 정한다.
 * ppm·건수는 천 단위 쉼표, 방은 "317호" (건물 이름 없이).
 */
record NotificationDraft(NotificationType type, Long roomId, Long actionId, Long complaintId,
                         String title, String body, String dedupKey) {

  /**
   * 지금 환기. 한 번 기준을 넘은 동안(에피소드)은 한 번만: 마지막으로 기준 미만이었던 측정 시각을 키에 넣는다.
   * @param lastBelowAt 기준 미만이었던 마지막 측정 시각, 한 번도 없으면 null
   * @param actionId    그 방의 대기 중 조치 (없으면 null → 누르면 강의실 상세)
   */
  static NotificationDraft ventilationNow(Room room, int co2, Long actionId, LocalDateTime lastBelowAt) {
    return new NotificationDraft(NotificationType.VENTILATION_NOW, room.getId(), actionId, null,
        room.getNumber() + "호 환기 필요", "지금 " + comma(co2) + "ppm",
        "VENT_NOW:room:" + room.getId() + ":" + (lastBelowAt == null ? "start" : seconds(lastBelowAt)));
  }

  /** 곧 환기. 조치 하나에 한 번. ppm 은 조치의 predictedValue */
  static NotificationDraft ventilationSoon(Action action, int minutes) {
    Room room = action.getRoom();
    return new NotificationDraft(NotificationType.VENTILATION_SOON, room.getId(), action.getId(), null,
        room.getNumber() + "호 " + minutes + "분 뒤 환기 필요",
        minutes + "분 뒤 " + comma(action.getPredictedValue()) + "ppm 예상",
        "SOON:action:" + action.getId());
  }

  /** 새 민원. 민원 하나에 한 번. 내용 = 분류 이름 (보낸 순서) */
  static NotificationDraft newComplaint(Complaint c) {
    Room room = c.getRoom();
    return new NotificationDraft(NotificationType.NEW_COMPLAINT, room.getId(), null, c.getId(),
        room.getNumber() + "호 새 민원",
        c.getCategoryList().stream().map(ComplaintCategory::getLabel).collect(Collectors.joining(" · ")),
        "COMPLAINT:" + c.getId());
  }

  /** 반복 민원. 방·ISO 주 단위로 한 번 (예: REPEAT:room:12:2026-W43) */
  static NotificationDraft repeatedComplaint(Complaint latest, long count, LocalDateTime now) {
    Room room = latest.getRoom();
    String week = "%d-W%02d".formatted(now.get(IsoFields.WEEK_BASED_YEAR), now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    return new NotificationDraft(NotificationType.REPEATED_COMPLAINT, room.getId(), null, latest.getId(),
        room.getNumber() + "호 반복 민원", "같은 민원이 " + comma(count) + "건 모였어요",
        "REPEAT:room:" + room.getId() + ":" + week);
  }

  /** 센서 끊김. 끊긴 시작(마지막 측정 시각)마다 한 번 → 복구 후 다시 끊기면 새 알림 */
  static NotificationDraft sensorError(Room room, LocalDateTime lastMeasuredAt) {
    return new NotificationDraft(NotificationType.SENSOR_ERROR, room.getId(), null, null,
        room.getNumber() + "호 센서 연결 끊김", "10분째 데이터가 없어요",
        "SENSOR:room:" + room.getId() + ":" + seconds(lastMeasuredAt));
  }

  static String comma(long value) {
    return String.format(Locale.KOREA, "%,d", value);
  }

  private static String seconds(LocalDateTime t) {
    return t.truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
