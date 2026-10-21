package com.app.sumpyo_be.domain.action.dto;

import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /api/actions/{actionId}/check */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "조치 상태 변경 결과")
public class ActionStatusResponse {

  @Schema(description = "변경 후 조치 상태", example = "CHECKED")
  private ActionStatus status;
}
