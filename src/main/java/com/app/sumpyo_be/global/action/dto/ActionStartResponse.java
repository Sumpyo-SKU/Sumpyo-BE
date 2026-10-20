package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionSource;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/actions/{actionId}/start */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "환기 시작 결과")
public class ActionStartResponse {

  @Schema(description = "조치 ID", example = "88")
  private Long actionId;

  @Schema(description = "강의실 ID", example = "42")
  private Long roomId;

  @Schema(description = "조치 발생 원인 (PREDICTION: 예측, COMPLAINT: 민원)", example = "PREDICTION")
  private ActionSource source;

  @Schema(description = "연결된 민원 ID (민원 조치가 아니면 null)", example = "null")
  private Long complaintId;

  @Schema(description = "조치 요약", example = "20분 뒤 CO2 1100 예상")
  private String summary;

  @Schema(description = "환기 시작 시점 CO2 (ppm). 최근 10분 측정이 없으면 null", example = "1250")
  private Integer co2Before;

  @Schema(description = "환기 후 예상 CO2 (ppm)", example = "800")
  private Integer expectedCo2After;

  @Schema(description = "환기 후 실제 CO2 (ppm). 완료 전에는 null", example = "null")
  private Integer actualCo2After;

  @Schema(description = "기록된 진행 단계, 시간 순 (시작 직후엔 WINDOW_OPEN 1개)")
  private List<ActionStep> steps;

  @Schema(description = "조치 상태 (시작 후 IN_PROGRESS)", example = "IN_PROGRESS")
  private ActionStatus status;

  @Schema(description = "처리자 이름. 비로그인이면 null", example = "김근로")
  private String handledBy;

  public static ActionStartResponse of(Action a, String handledBy) {
    return ActionStartResponse.builder()
        .actionId(a.getId())
        .roomId(a.getRoom().getId())
        .source(a.getSource())
        .complaintId(a.getComplaintId())
        .summary(a.getSummary())
        .co2Before(a.getCo2Before())
        .expectedCo2After(a.getExpectedCo2After())
        .actualCo2After(a.getActualCo2After())
        .steps(ActionStep.from(a))
        .status(a.getStatus())
        .handledBy(handledBy)
        .build();
  }
}
