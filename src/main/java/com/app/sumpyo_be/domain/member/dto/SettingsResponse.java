package com.app.sumpyo_be.domain.member.dto;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET/PUT /api/me/settings 응답. cautionFrom/criticalFrom 은 저장하지 않고 기준값에서 계산 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "내 설정 (알림 기준값)")
public class SettingsResponse {

  @Schema(description = "CO2 기준값 (ppm)", example = "1000")
  private int co2Threshold;

  @Schema(description = "주의 시작 CO2 (기준 × 0.9, 올림). 계산값", example = "900")
  private int cautionFrom;

  @Schema(description = "위험 시작 CO2 (기준 × 1.2, 올림). 계산값", example = "1200")
  private int criticalFrom;

  @Schema(description = "적정 온도 최솟값 (°C)", example = "20")
  private int tempMin;

  @Schema(description = "적정 온도 최댓값 (°C)", example = "26")
  private int tempMax;

  @Schema(description = "적정 습도 최솟값 (%)", example = "40")
  private int humidityMin;

  @Schema(description = "적정 습도 최댓값 (%)", example = "60")
  private int humidityMax;

  @Schema(description = "예측되면 미리 알림 (VENTILATION_SOON). notifyVentilation 이 꺼져 있으면 효과 없음", example = "true")
  private boolean alertEnabled;

  @Schema(description = "기준 초과 몇 분 전에 알릴지 (10·15·20)", example = "15")
  private int alertLeadMinutes;

  @Schema(description = "환기 알림 받기 (지금 환기·곧 환기). 끄면 alertEnabled 와 상관없이 환기 알림 없음", example = "true")
  private boolean notifyVentilation;

  @Schema(description = "민원 알림 받기 (새 민원·반복 민원)", example = "true")
  private boolean notifyComplaint;

  @Schema(description = "센서 연결 끊김 알림 받기", example = "true")
  private boolean notifySensorError;

  public static SettingsResponse from(Member m) {
    Thresholds t = Thresholds.from(m);
    return SettingsResponse.builder()
        .co2Threshold(t.co2())
        .cautionFrom(AirQualityJudge.cautionFrom(t.co2()))
        .criticalFrom(AirQualityJudge.criticalFrom(t.co2()))
        .tempMin(t.tempMin())
        .tempMax(t.tempMax())
        .humidityMin(t.humidityMin())
        .humidityMax(t.humidityMax())
        .alertEnabled(m.isAlertOn())
        .alertLeadMinutes(m.alertLeadMinutesOrDefault())
        .notifyVentilation(m.isNotifyVentilation())
        .notifyComplaint(m.isNotifyComplaint())
        .notifySensorError(m.isNotifySensorError())
        .build();
  }
}
