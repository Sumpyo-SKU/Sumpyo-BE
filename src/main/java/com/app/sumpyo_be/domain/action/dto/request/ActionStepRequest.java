package com.app.sumpyo_be.domain.action.dto.request;

import com.app.sumpyo_be.domain.action.entity.StepType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /api/actions/{actionId}/steps */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "체크리스트 단계 완료")
public class ActionStepRequest {

  @Schema(description = "완료한 단계 (plan 에 있는 것만)", example = "AC_ON", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "type 은 필수입니다.")
  private StepType type;

  @Schema(description = "실제 설정한 희망 온도 °C (AC_ON, 비우면 추천 값)", example = "23")
  @Min(value = 18, message = "targetTemp 는 18~28 사이여야 합니다.")
  @Max(value = 28, message = "targetTemp 는 18~28 사이여야 합니다.")
  private Integer targetTemp;
}
