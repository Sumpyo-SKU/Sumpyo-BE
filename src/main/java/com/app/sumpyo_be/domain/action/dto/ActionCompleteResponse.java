package com.app.sumpyo_be.domain.action.dto;

import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/actions/{actionId}/complete */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "조치 완료 결과")
public class ActionCompleteResponse {

  @Schema(description = "환기 시작 시점 CO2 (ppm)", example = "1250")
  private Integer co2Before;

  @Schema(description = "완료 시점 실제 CO2 (ppm). 최근 10분 측정이 없으면 null", example = "820")
  private Integer actualCo2After;

  @Schema(description = "목표 달성 여부. 민원 AI 추천 조치는 실제 CO2 ≤ 예상 CO2, 예측 조치는 실제 CO2 < 처리자 기준값. "
      + "센서가 끊겨 실제 CO2 가 없으면 null", example = "true")
  private Boolean goalReached;

  @Schema(description = "조치 상태 (완료 후 DONE)", example = "DONE")
  private ActionStatus status;
}
