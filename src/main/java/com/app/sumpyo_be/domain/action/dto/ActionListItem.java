package com.app.sumpyo_be.domain.action.dto;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import com.app.sumpyo_be.domain.air.Thresholds;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조치 목록 항목. 홈 "창문 환기 예측" 가로 카드도 이걸로 그린다:
 * "618호 · 지금 · 창문 열어주세요 · 10분 뒤 기준 초과 예정 · CO2 1,250ppm / 기준 1,000"
 * "605호 · 15:00 · 1시간 뒤 · 창문을 닫아주세요 · 1시간 뒤 비 예상 · 습도 예상 64% / 적정 40–60"
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "조치 목록 항목 (GET /api/actions)")
public class ActionListItem {

  static final int NOW_WITHIN_MINUTES = 10;
  static final int SOON_WITHIN_MINUTES = 30;
  static final String NOW_LABEL = "지금";
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  @Schema(description = "조치 ID", example = "88")
  private Long actionId;

  @Schema(description = "강의실 ID", example = "42")
  private Long roomId;

  @Schema(description = "강의실 이름 (건물 + 호수)", example = "북악관 618호")
  private String room;

  @Schema(description = "호수 ('호' 없이)", example = "618")
  private String number;

  @Schema(description = "조치 발생 원인 (PREDICTION: 예측, COMPLAINT: 민원)", example = "PREDICTION")
  private ActionSource source;

  @Schema(description = "추천 조치 (OPEN: 창문 열기, CLOSE: 창문 닫기)", example = "OPEN")
  private Recommend recommend;

  @Schema(description = "긴급도 (NOW: 지났거나 10분 이내, SOON: 30분 이내, LATER: 그 외)", example = "NOW")
  private ActionTiming timing;

  @Schema(description = "시각 표시. 지났거나 10분 이내면 '지금', 아니면 expectedAt 의 HH:mm", example = "14:30")
  private String timeLabel;

  @Schema(description = "남은 시간 ('30분 뒤', '1시간 뒤', '1시간 20분 뒤'). 지금이면 null", example = "30분 뒤")
  private String relative;

  @Schema(description = "카드 제목 (창문 열어주세요 / 수업 전에 열어두세요 / 창문을 닫아주세요)", example = "수업 전에 열어두세요")
  private String title;

  @Schema(description = "조치 이유 (N분 뒤 기준 초과 예정 / 기준 초과 / 1시간 뒤 비 예상 / 학생 민원 접수)",
      example = "30분 뒤 기준 초과 예정")
  private String reason;

  @Schema(description = "대상 지표 (CO2, HUMIDITY, TEMP)", example = "CO2")
  private Metric metric;

  @Schema(description = "값 이름. 지금이면 'CO2'·'습도'·'온도', 미래면 뒤에 ' 예상'", example = "CO2 예상")
  private String metricLabel;

  @Schema(description = "예측값", example = "1100")
  private Integer predictedValue;

  @Schema(description = "단위 (CO2 ppm, 습도 %, 온도 °C)", example = "ppm")
  private String unit;

  @Schema(description = "CO2 기준값 (조치 생성 당시, 없으면 현재 사용자 기준값). 습도·온도 조치는 저장된 값만 (보통 null)",
      example = "1000")
  private Integer threshold;

  @Schema(description = "적정 범위 최솟값 (습도·온도 조치, 내 설정). CO2 는 null", example = "40")
  private Integer rangeMin;

  @Schema(description = "적정 범위 최댓값 (습도·온도 조치, 내 설정). CO2 는 null", example = "60")
  private Integer rangeMax;

  @Schema(description = "기준 초과(비) 예상 시각 (KST). 민원 조치는 null 일 수 있음", example = "2026-10-16T14:30:00")
  private LocalDateTime expectedAt;

  @Schema(description = "조치 상태 (PENDING: 대기, CHECKED: 확인, IN_PROGRESS: 환기 중, DONE: 완료)", example = "PENDING")
  private ActionStatus status;

  /**
   * room·building 은 fetch join 으로 이미 로딩된 상태여야 한다.
   * @param t              현재 사용자 기준값 (CO2 기준이 저장돼 있지 않을 때, 습도·온도 적정 범위)
   * @param classBeforeDue 지금 ~ 예상 시각 사이에 그 방 수업이 시작하는지
   */
  public static ActionListItem of(Action a, Thresholds t, LocalDateTime now, boolean classBeforeDue) {
    LocalDateTime at = dueAt(a);
    boolean isNow = isNow(at, now);
    String relative = isNow ? null : relative(minutesUntil(at, now));
    Metric metric = a.getMetric() != null ? a.getMetric() : Metric.CO2;
    return ActionListItem.builder()
        .actionId(a.getId())
        .roomId(a.getRoom().getId())
        .room(a.getRoom().getBuilding().getName() + " " + a.getRoom().getNumber() + "호")
        .number(a.getRoom().getNumber())
        .source(a.getSource())
        .recommend(a.getRecommend())
        .timing(timing(at, now))
        .timeLabel(isNow ? NOW_LABEL : at.format(HH_MM))
        .relative(relative)
        .title(title(a.getRecommend(), isNow, classBeforeDue))
        .reason(reason(a, at, now))
        .metric(a.getMetric())
        .metricLabel(metricName(metric) + (isNow ? "" : " 예상"))
        .predictedValue(a.getPredictedValue())
        .unit(unit(metric))
        .threshold(threshold(a, metric, t))
        .rangeMin(metric == Metric.HUMIDITY ? Integer.valueOf(t.humidityMin())
            : metric == Metric.TEMP ? Integer.valueOf(t.tempMin()) : null)
        .rangeMax(metric == Metric.HUMIDITY ? Integer.valueOf(t.humidityMax())
            : metric == Metric.TEMP ? Integer.valueOf(t.tempMax()) : null)
        .expectedAt(a.getExpectedAt())
        .status(a.getStatus())
        .build();
  }

  /** 정렬·긴급도 기준 시각. 예상 시각이 없는 조치(민원 등)는 생성 시각 = 이미 처리할 때 */
  public static LocalDateTime dueAt(Action a) {
    return a.getExpectedAt() != null ? a.getExpectedAt() : a.getCreatedAt();
  }

  /** 지났거나 10분 이내 */
  public static boolean isNow(LocalDateTime at, LocalDateTime now) {
    return !at.isAfter(now) || minutesUntil(at, now) <= NOW_WITHIN_MINUTES;
  }

  static ActionTiming timing(LocalDateTime at, LocalDateTime now) {
    if (isNow(at, now)) {
      return ActionTiming.NOW;
    }
    return minutesUntil(at, now) <= SOON_WITHIN_MINUTES ? ActionTiming.SOON : ActionTiming.LATER;
  }

  static String title(Recommend recommend, boolean isNow, boolean classBeforeDue) {
    if (recommend == Recommend.CLOSE) {
      return "창문을 닫아주세요";
    }
    return !isNow && classBeforeDue ? "수업 전에 열어두세요" : "창문 열어주세요";
  }

  static String reason(Action a, LocalDateTime at, LocalDateTime now) {
    if (a.getSource() == ActionSource.COMPLAINT) {
      return "학생 민원 접수";
    }
    if (a.isRainClose()) {
      return at.isAfter(now) ? relative(minutesUntil(at, now)) + " 비 예상" : "곧 비 예상";
    }
    if (!at.isAfter(now)) {
      return "기준 초과";
    }
    return relative(minutesUntil(at, now)) + " 기준 초과 예정";
  }

  /** CO2 는 저장된 기준(없으면 현재 사용자 기준), 습도·온도는 저장된 값만 */
  private static Integer threshold(Action a, Metric metric, Thresholds t) {
    if (a.getThreshold() != null) {
      return a.getThreshold();
    }
    return metric == Metric.CO2 ? Integer.valueOf(t.co2()) : null;
  }

  /** 30분 뒤 / 1시간 뒤 / 1시간 20분 뒤 */
  static String relative(long minutes) {
    if (minutes < 60) {
      return minutes + "분 뒤";
    }
    long hours = minutes / 60;
    long rest = minutes % 60;
    return rest == 0 ? hours + "시간 뒤" : hours + "시간 " + rest + "분 뒤";
  }

  private static String metricName(Metric metric) {
    return switch (metric) {
      case CO2 -> "CO2";
      case HUMIDITY -> "습도";
      case TEMP -> "온도";
    };
  }

  private static String unit(Metric metric) {
    return switch (metric) {
      case CO2 -> "ppm";
      case HUMIDITY -> "%";
      case TEMP -> "°C";
    };
  }

  /** 남은 분, 올림 (9분 30초 → 10분). 캠퍼스 현황의 minutesToExceed 와 같은 방식 */
  private static long minutesUntil(LocalDateTime at, LocalDateTime now) {
    long seconds = Duration.between(now, at).toSeconds();
    return Math.ceilDiv(seconds, 60);
  }
}
