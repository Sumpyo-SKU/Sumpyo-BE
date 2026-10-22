package com.app.sumpyo_be.domain.action.entity;

import com.app.sumpyo_be.domain.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 환기/냉난방 조치. 예측·민원으로 생성되고 근로생이 확인 → 환기 → 완료 처리한다. */
@Entity
@Table(name = "action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Action {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private ActionSource source;

  // 예측/민원/시간표는 조회만 하므로 ID 로만 둔다
  @Column(name = "prediction_id")
  private Long predictionId;

  @Column(name = "complaint_id")
  private Long complaintId;

  @Column(name = "class_schedule_id")
  private Long classScheduleId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Recommend recommend;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Metric metric;

  @Column(name = "predicted_value")
  private Integer predictedValue;

  private Integer threshold;

  @Column(name = "expected_at")
  private LocalDateTime expectedAt;

  @Column(length = 200)
  private String summary;

  @Column(name = "co2_before")
  private Integer co2Before;

  @Column(name = "expected_co2_after")
  private Integer expectedCo2After;

  @Column(name = "actual_co2_after")
  private Integer actualCo2After;

  @Column(name = "window_opened_at")
  private LocalDateTime windowOpenedAt;

  @Column(name = "window_closed_at")
  private LocalDateTime windowClosedAt;

  @Column(name = "ac_on_at")
  private LocalDateTime acOnAt;

  @Column(name = "ac_target_temp")
  private Integer acTargetTemp;

  @Column(name = "member_id")
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private ActionStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  /** 민원 추천 체크리스트 JSON 배열 (PlanStep). 예측 조치는 null */
  @Column(columnDefinition = "text")
  private String plan;

  /** 추천 이유 한 줄 */
  @Column(length = 200)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "plan_source", length = 10)
  private PlanSource planSource;

  /** 결과 CO2 를 잴 시각 (창문 연 시각 + 추천 분). 지나면 ActionMeasureService 가 actual_co2_after 를 채운다 */
  @Column(name = "measure_at")
  private LocalDateTime measureAt;

  /** 추천 당시 그대로 두면 예상 CO2 (수업 종료 시각, 수업이 없으면 30분 뒤). 예측이 없으면 null */
  @Column(name = "forecast_co2")
  private Integer forecastCo2;

  /** 요약 문구 (비 예보 창문 닫기) */
  public static final String RAIN_CLOSE_SUMMARY = "비 예상 · 창문 닫기";

  /**
   * 비 예보 창문 닫기 조치 (RainCloseActionService). 예측 조치로 만들고 지표는 습도.
   * @param humidity   그 시각 습도 예측, 없으면 현재 습도 (둘 다 없으면 null)
   * @param expectedAt 비 예상 시각
   */
  public static Action rainClose(Room room, Integer humidity, LocalDateTime expectedAt, LocalDateTime now) {
    Action a = new Action();
    a.room = room;
    a.source = ActionSource.PREDICTION;
    a.recommend = Recommend.CLOSE;
    a.metric = Metric.HUMIDITY;
    a.predictedValue = humidity;
    a.expectedAt = expectedAt;
    a.summary = RAIN_CLOSE_SUMMARY;
    a.status = ActionStatus.PENDING;
    a.createdAt = now;
    return a;
  }

  /** 비 예보로 만든 창문 닫기 조치인지 (습도 지표의 CLOSE 예측 조치) */
  public boolean isRainClose() {
    return source == ActionSource.PREDICTION && recommend == Recommend.CLOSE && metric == Metric.HUMIDITY;
  }

  /** 민원 추천 조치 (PENDING). 숫자(co2Before, expectedCo2After, 창문 분)는 서버가 계산한 값 */
  public static Action complaintPlan(Room room, Long complaintId, Recommend recommend, Metric metric,
      String summary, String reason, String planJson, PlanSource planSource, Integer co2Before,
      Integer expectedCo2After, Integer forecastCo2, Integer acTargetTemp, LocalDateTime now) {
    Action a = new Action();
    a.room = room;
    a.source = ActionSource.COMPLAINT;
    a.complaintId = complaintId;
    a.status = ActionStatus.PENDING;
    a.createdAt = now;
    a.replacePlan(recommend, metric, summary, reason, planJson, planSource, co2Before, expectedCo2After, forecastCo2,
        acTargetTemp);
    return a;
  }

  /** [다른 방법] 대기 중인 민원 조치의 추천을 새 추천으로 바꾼다 */
  public void replacePlan(Recommend recommend, Metric metric, String summary, String reason, String planJson,
      PlanSource planSource, Integer co2Before, Integer expectedCo2After, Integer forecastCo2, Integer acTargetTemp) {
    if (!status.isWaiting()) {
      throw new IllegalStateException("대기 중인 조치만 바꿀 수 있음: " + status);
    }
    this.recommend = recommend;
    this.metric = metric;
    this.summary = summary;
    this.reason = reason;
    this.plan = planJson;
    this.planSource = planSource;
    this.co2Before = co2Before;
    this.expectedCo2After = expectedCo2After;
    this.forecastCo2 = forecastCo2;
    this.acTargetTemp = acTargetTemp;
  }

  /** 체크리스트로 진행하는 조치 (민원 추천). start 때 창문 시각을 찍지 않고 단계별로 기록한다 */
  public boolean hasPlan() {
    return plan != null;
  }

  /**
   * [체크리스트] 단계 수행 시각 기록. 이미 기록돼 있으면 그대로 둔다.
   * @return 새로 기록했으면 true
   */
  public boolean checkStep(StepType type, Integer targetTemp, LocalDateTime now) {
    requireStatus(ActionStatus.IN_PROGRESS);
    if (isStepDone(type)) {
      return false;
    }
    switch (type) {
      case WINDOW_OPEN -> windowOpenedAt = now;
      case WINDOW_CLOSE -> windowClosedAt = now;
      case AC_ON -> {
        acOnAt = now;
        if (targetTemp != null) {
          acTargetTemp = targetTemp;
        }
      }
    }
    return true;
  }

  public boolean isStepDone(StepType type) {
    return stepDoneAt(type) != null;
  }

  public LocalDateTime stepDoneAt(StepType type) {
    return switch (type) {
      case WINDOW_OPEN -> windowOpenedAt;
      case WINDOW_CLOSE -> windowClosedAt;
      case AC_ON -> acOnAt;
    };
  }

  /**
   * [체크리스트 자동 완료] IN_PROGRESS → DONE.
   * @param measureAt      결과 CO2 를 잴 시각 (창문 단계가 없으면 now)
   * @param actualCo2After 지금 기록할 결과 CO2 (창문 단계가 있으면 null → 나중에 measure_at 에 잰다)
   */
  public void finishPlan(LocalDateTime now, LocalDateTime measureAt, Integer actualCo2After) {
    requireStatus(ActionStatus.IN_PROGRESS);
    this.status = ActionStatus.DONE;
    this.completedAt = now;
    this.measureAt = measureAt;
    this.actualCo2After = actualCo2After;
  }

  /** measure_at 이 지난 뒤 결과 CO2 기록 */
  public void recordResult(Integer actualCo2After) {
    requireStatus(ActionStatus.DONE);
    this.actualCo2After = actualCo2After;
  }

  // 상태 전이 전 검사는 ActionService 에서 409 로 처리한다. 여기 검사는 잘못된 호출 방지용.

  /** [확인] PENDING → CHECKED. 이미 CHECKED 면 그대로 둔다. */
  public void check() {
    if (status == ActionStatus.CHECKED) {
      return;
    }
    requireStatus(ActionStatus.PENDING);
    status = ActionStatus.CHECKED;
  }

  /**
   * [환기 시작] PENDING/CHECKED → IN_PROGRESS.
   * 체크리스트 조치(민원 추천)는 창문 시각을 찍지 않고(단계별로 기록), 지금 CO2 가 없으면 추천 때 값을 유지한다.
   */
  public void start(Integer co2Before, Long memberId, LocalDateTime now) {
    if (!status.isWaiting()) {
      throw new IllegalStateException("시작할 수 없는 상태: " + status);
    }
    this.status = ActionStatus.IN_PROGRESS;
    if (hasPlan()) {
      if (co2Before != null) {
        this.co2Before = co2Before;
      }
    } else {
      this.windowOpenedAt = now;
      this.co2Before = co2Before;
    }
    this.memberId = memberId;
  }

  /** [완료] IN_PROGRESS → DONE */
  public void complete(Integer actualCo2After, LocalDateTime now) {
    requireStatus(ActionStatus.IN_PROGRESS);
    this.status = ActionStatus.DONE;
    this.actualCo2After = actualCo2After;
    this.windowClosedAt = now;
    this.completedAt = now;
  }

  private void requireStatus(ActionStatus expected) {
    if (status != expected) {
      throw new IllegalStateException(expected + " 상태가 아님: " + status);
    }
  }
}
