package com.app.sumpyo_be.global.action.entity;

import com.app.sumpyo_be.global.room.entity.Room;
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

  // 상태 전이 전 검사는 ActionService 에서 409 로 처리한다. 여기 검사는 잘못된 호출 방지용.

  /** [확인] PENDING → CHECKED. 이미 CHECKED 면 그대로 둔다. */
  public void check() {
    if (status == ActionStatus.CHECKED) {
      return;
    }
    requireStatus(ActionStatus.PENDING);
    status = ActionStatus.CHECKED;
  }

  /** [환기 시작] PENDING/CHECKED → IN_PROGRESS */
  public void start(Integer co2Before, Long memberId, LocalDateTime now) {
    if (!status.isWaiting()) {
      throw new IllegalStateException("시작할 수 없는 상태: " + status);
    }
    this.status = ActionStatus.IN_PROGRESS;
    this.windowOpenedAt = now;
    this.co2Before = co2Before;
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
