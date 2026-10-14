package com.app.sumpyo_be.global.reading.dto;

import com.app.sumpyo_be.global.reading.entity.SensorReading;
import java.time.LocalDateTime;

public record SensorReadingResponse(
    Long id,
    String roomId,
    Integer co2,
    Integer temp,
    Integer humidity,
    LocalDateTime measuredAt
) {
  public static SensorReadingResponse from(SensorReading r) {
    return new SensorReadingResponse(
        r.getId(), r.getRoomId(), r.getCo2(), r.getTemp(), r.getHumidity(), r.getMeasuredAt());
  }
}
