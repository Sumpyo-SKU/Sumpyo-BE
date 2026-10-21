package com.app.sumpyo_be.domain.reading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "센서 측정값 전송 (아두이노 → 서버)")
public class SensorPayload {

  @Schema(description = "CO2 (ppm, 300~10000)", example = "850", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull @Min(300) @Max(10000)
  private Integer co2;

  @Schema(description = "온도 (°C, -20~60)", example = "24")
  @Min(-20) @Max(60)
  private Integer temp;

  @Schema(description = "습도 (%, 0~100)", example = "45")
  @Min(0) @Max(100)
  private Integer humidity;
}
