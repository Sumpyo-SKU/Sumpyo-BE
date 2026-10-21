package com.app.sumpyo_be.domain.action.dto;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "조치 목록 항목 (GET /api/actions)")
public class ActionListItem {

  static final int NOW_WITHIN_MINUTES = 10;
  static final int SOON_WITHIN_MINUTES = 30;

  @Schema(description = "조치 ID", example = "88")
  private Long actionId;

  @Schema(description = "강의실 ID", example = "42")
  private Long roomId;

  @Schema(description = "강의실 이름 (건물 + 호수)", example = "북악관 618호")
  private String room;

  @Schema(description = "조치 발생 원인 (PREDICTION: 예측, COMPLAINT: 민원)", example = "PREDICTION")
  private ActionSource source;

  @Schema(description = "추천 조치 (OPEN: 창문 열기, CLOSE: 창문 닫기)", example = "OPEN")
  private Recommend recommend;

  @Schema(description = "긴급도 (NOW: 지났거나 10분 이내, SOON: 30분 이내, LATER: 그 외)", example = "NOW")
  private ActionTiming timing;

  @Schema(description = "카드 제목", example = "창문 열어주세요")
  private String title;

  @Schema(description = "조치 이유", example = "10분 뒤 기준 초과 예정")
  private String reason;

  @Schema(description = "대상 지표 (CO2, HUMIDITY, TEMP)", example = "CO2")
  private Metric metric;

  @Schema(description = "예측값", example = "1250")
  private Integer predictedValue;

  @Schema(description = "기준값 (조치 생성 당시, 없으면 현재 사용자 기준값)", example = "1000")
  private Integer threshold;

  @Schema(description = "기준 초과 예상 시각 (KST). 민원 조치는 null 일 수 있음", example = "2026-10-16T14:10:00")
  private LocalDateTime expectedAt;

  @Schema(description = "조치 상태 (PENDING: 대기, CHECKED: 확인, IN_PROGRESS: 환기 중, DONE: 완료)", example = "PENDING")
  private ActionStatus status;

  /**
   * room·building 은 fetch join 으로 이미 로딩된 상태여야 한다.
   * @param fallbackThreshold 조치에 기준값이 저장돼 있지 않을 때 쓸 현재 사용자 기준값
   */
  public static ActionListItem of(Action a, int fallbackThreshold, LocalDateTime now) {
    LocalDateTime at = dueAt(a);
    return ActionListItem.builder()
        .actionId(a.getId())
        .roomId(a.getRoom().getId())
        .room(a.getRoom().getBuilding().getName() + " " + a.getRoom().getNumber() + "호")
        .source(a.getSource())
        .recommend(a.getRecommend())
        .timing(timing(at, now))
        .title(title(a.getRecommend()))
        .reason(reason(a.getSource(), at, now))
        .metric(a.getMetric())
        .predictedValue(a.getPredictedValue())
        .threshold(a.getThreshold() != null ? a.getThreshold() : fallbackThreshold)
        .expectedAt(a.getExpectedAt())
        .status(a.getStatus())
        .build();
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
