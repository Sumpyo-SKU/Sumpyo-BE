package com.app.sumpyo_be.global.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PUT /api/me/settings 본문. 보낸 값만 바꾼다 (null 은 그대로) → 온보딩에서 { "alertEnabled": true } 만 보내도 됨.
 * cautionFrom/criticalFrom 은 계산값이라 보내도 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingsUpdateRequest(
    @Min(value = 600, message = "co2Threshold 는 600~1500 사이여야 합니다.")
    @Max(value = 1500, message = "co2Threshold 는 600~1500 사이여야 합니다.")
    Integer co2Threshold,
    Integer tempMin,
    Integer tempMax,
    Integer humidityMin,
    Integer humidityMax,
    Boolean alertEnabled,
    Integer alertLeadMinutes   // 10·15·20 중 하나 (MeService 에서 검증)
) {}
