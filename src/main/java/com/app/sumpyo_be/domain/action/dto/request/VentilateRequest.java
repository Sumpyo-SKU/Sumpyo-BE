package com.app.sumpyo_be.domain.action.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/actions/ventilate */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강의실 [환기 하기]")
public class VentilateRequest {

  @Schema(description = "강의실 ID", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "roomId 는 필수입니다.")
  private Long roomId;
}
