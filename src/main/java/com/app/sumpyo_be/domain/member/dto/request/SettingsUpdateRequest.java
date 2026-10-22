package com.app.sumpyo_be.domain.member.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PUT /api/me/settings 본문. 보낸 값만 바꾼다 (null 은 그대로) → 온보딩에서 { "alertEnabled": true } 만 보내도 됨.
 * cautionFrom/criticalFrom 은 계산값이라 보내도 무시한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "내 설정 변경 요청. 보낸 값만 바뀌고 안 보낸 값은 그대로")
public class SettingsUpdateRequest {

  @Schema(description = "CO2 기준값 (ppm, 600~1500)", example = "1100")
  @Min(value = 600, message = "co2Threshold 는 600~1500 사이여야 합니다.")
  @Max(value = 1500, message = "co2Threshold 는 600~1500 사이여야 합니다.")
  private Integer co2Threshold;

  @Schema(description = "적정 온도 최솟값 (°C). tempMax 보다 작아야 함", example = "20")
  private Integer tempMin;

  @Schema(description = "적정 온도 최댓값 (°C)", example = "25")
  private Integer tempMax;

  @Schema(description = "적정 습도 최솟값 (%). humidityMax 보다 작아야 함", example = "40")
  private Integer humidityMin;

  @Schema(description = "적정 습도 최댓값 (%)", example = "60")
  private Integer humidityMax;

  @Schema(description = "예측되면 미리 알림 (VENTILATION_SOON)", example = "true")
  private Boolean alertEnabled;

  @Schema(description = "기준 초과 몇 분 전에 알릴지 (10·15·20 중 하나)", example = "20")
  private Integer alertLeadMinutes;

  @Schema(description = "환기 알림 받기 (지금 환기·곧 환기)", example = "true")
  private Boolean notifyVentilation;

  @Schema(description = "민원 알림 받기 (새 민원·반복 민원)", example = "true")
  private Boolean notifyComplaint;

  @Schema(description = "센서 연결 끊김 알림 받기", example = "false")
  private Boolean notifySensorError;
}
