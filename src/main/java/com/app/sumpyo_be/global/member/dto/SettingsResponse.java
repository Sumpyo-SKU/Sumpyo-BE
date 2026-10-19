package com.app.sumpyo_be.global.member.dto;

import com.app.sumpyo_be.global.air.AirQualityJudge;
import com.app.sumpyo_be.global.air.Thresholds;
import com.app.sumpyo_be.global.member.entity.Member;

/** GET/PUT /api/me/settings 응답. cautionFrom/criticalFrom 은 저장하지 않고 기준값에서 계산 */
public record SettingsResponse(
    int co2Threshold,
    int cautionFrom,    // 기준 × 0.9
    int criticalFrom,   // 기준 × 1.2
    int tempMin,
    int tempMax,
    int humidityMin,
    int humidityMax,
    boolean alertEnabled,
    int alertLeadMinutes
) {
  static final int DEFAULT_ALERT_LEAD_MINUTES = 15;

  public static SettingsResponse from(Member m) {
    Thresholds t = Thresholds.from(m);
    return new SettingsResponse(t.co2(),
        AirQualityJudge.cautionFrom(t.co2()), AirQualityJudge.criticalFrom(t.co2()),
        t.tempMin(), t.tempMax(), t.humidityMin(), t.humidityMax(),
        m.getAlertEnabled() == null || m.getAlertEnabled(),
        m.getAlertLeadMinutes() != null ? m.getAlertLeadMinutes() : DEFAULT_ALERT_LEAD_MINUTES);
  }
}
