package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.ActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /api/actions/{actionId}/complete
 * TODO: 명세의 응답 필드가 goalReached 까지만 확인됨. 나머지 필드 받으면 추가.
 */
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

  @Schema(description = "목표 달성 여부 (완료 시점 CO2 가 기준 미만이면 true)", example = "true")
  private boolean goalReached;

  @Schema(description = "조치 상태 (완료 후 DONE)", example = "DONE")
  private ActionStatus status;
}
