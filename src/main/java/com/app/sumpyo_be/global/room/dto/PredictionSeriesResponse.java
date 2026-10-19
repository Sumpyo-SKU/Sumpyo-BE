package com.app.sumpyo_be.global.room.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** GET /api/rooms/{roomId}/predictions : 그래프 예측 (target_at 마다 가장 최근 예측) */
public record PredictionSeriesResponse(String modelVersion, List<Point> points) {

  public record Point(LocalDateTime at, Integer co2, BigDecimal temp, Integer humidity) {}
}
