package com.app.sumpyo_be.global.reading.dto;

import com.app.sumpyo_be.global.reading.entity.SensorReading;
import java.time.LocalDateTime;

public record SensorReadingResponse(
    Long id,
    String roomCode,
    Integer co2,
    Integer temp,
    Integer humidity,
    LocalDateTime measuredAt
) {
  // open-in-view=false 라 r.getRoom() 지연 로딩을 피하려고 코드는 호출 쪽에서 넘겨받는다.
  public static SensorReadingResponse from(SensorReading r, String roomCode) {
    return new SensorReadingResponse(
        r.getId(), roomCode, r.getCo2(), r.getTemp(), r.getHumidity(), r.getMeasuredAt());
  }
}
