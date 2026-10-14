package com.app.sumpyo_be.global.reading.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SensorPayload(
    @NotNull @Min(300) @Max(10000) Integer co2,
    @Min(-20) @Max(60) Integer temp,
    @Min(0) @Max(100) Integer humidity
) {}