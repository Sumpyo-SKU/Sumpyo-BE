package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionSource;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import com.app.sumpyo_be.global.action.entity.Metric;
import com.app.sumpyo_be.global.action.entity.Recommend;
import java.time.Duration;
import java.time.LocalDateTime;

/** GET /api/actions 항목 */
public record ActionListItem(
    Long actionId,
    Long roomId,
    String room,
    ActionSource source,
    Recommend recommend,
    ActionTiming timing,
    String title,
    String reason,
    Metric metric,
    Integer predictedValue,
    Integer threshold,
    LocalDateTime expectedAt,
    ActionStatus status
) {
  static final int NOW_WITHIN_MINUTES = 10;
  static final int SOON_WITHIN_MINUTES = 30;

  /**
   * room·building 은 fetch join 으로 이미 로딩된 상태여야 한다.
   * @param fallbackThreshold 조치에 기준값이 저장돼 있지 않을 때 쓸 현재 사용자 기준값
   */
  public static ActionListItem of(Action a, int fallbackThreshold, LocalDateTime now) {
    LocalDateTime at = dueAt(a);
    return new ActionListItem(a.getId(), a.getRoom().getId(),
        a.getRoom().getBuilding().getName() + " " + a.getRoom().getNumber() + "호",
        a.getSource(), a.getRecommend(), timing(at, now), title(a.getRecommend()),
        reason(a.getSource(), at, now), a.getMetric(), a.getPredictedValue(),
        a.getThreshold() != null ? a.getThreshold() : fallbackThreshold, a.getExpectedAt(), a.getStatus());
  }

  /** 정렬·긴급도 기준 시각. 예상 시각이 없는 조치(민원 등)는 생성 시각 = 이미 처리할 때 */
  public static LocalDateTime dueAt(Action a) {
    return a.getExpectedAt() != null ? a.getExpectedAt() : a.getCreatedAt();
  }

  static ActionTiming timing(LocalDateTime at, LocalDateTime now) {
    if (!at.isAfter(now)) {
      return ActionTiming.NOW;
    }
    long minutes = minutesUntil(at, now);
    if (minutes <= NOW_WITHIN_MINUTES) {
      return ActionTiming.NOW;
    }
    return minutes <= SOON_WITHIN_MINUTES ? ActionTiming.SOON : ActionTiming.LATER;
  }

  static String title(Recommend recommend) {
    return recommend == Recommend.CLOSE ? "창문 닫아주세요" : "창문 열어주세요";
  }

  static String reason(ActionSource source, LocalDateTime at, LocalDateTime now) {
    if (source == ActionSource.COMPLAINT) {
      return "학생 민원 접수";
    }
    if (!at.isAfter(now)) {
      return "기준 초과";
    }
    return minutesUntil(at, now) + "분 뒤 기준 초과 예정";
  }

  /** 남은 분, 올림 (9분 30초 → 10분). 캠퍼스 현황의 minutesToExceed 와 같은 방식 */
  private static long minutesUntil(LocalDateTime at, LocalDateTime now) {
    long seconds = Duration.between(now, at).toSeconds();
    return Math.ceilDiv(seconds, 60);
  }
}
