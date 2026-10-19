package com.app.sumpyo_be.global.prediction.dto;

import java.time.LocalDateTime;

/** 예측 CO2 한 점. 같은 target_at 을 여러 번 예측했으면 가장 최근 예측만 쓴다. */
public record PredictionPoint(Long roomId, LocalDateTime targetAt, Integer co2) {}
