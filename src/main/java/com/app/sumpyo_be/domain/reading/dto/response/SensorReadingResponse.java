package com.app.sumpyo_be.domain.reading.dto.response;

import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "센서 측정값")
public class SensorReadingResponse {

  @Schema(description = "측정값 ID", example = "1234")
  private Long id;

  @Schema(description = "강의실 코드", example = "bukak-617")
  private String roomCode;

  @Schema(description = "CO2 (ppm)", example = "850")
  private Integer co2;

  @Schema(description = "온도 (°C)", example = "24")
  private Integer temp;

  @Schema(description = "습도 (%)", example = "45")
  private Integer humidity;

  @Schema(description = "측정 시각 (KST)", example = "2026-10-16T14:05:00")
  private LocalDateTime measuredAt;

  // open-in-view=false 라 r.getRoom() 지연 로딩을 피하려고 코드는 호출 쪽에서 넘겨받는다.
  public static SensorReadingResponse from(SensorReading r, String roomCode) {
    return new SensorReadingResponse(
        r.getId(), roomCode, r.getCo2(), r.getTemp(), r.getHumidity(), r.getMeasuredAt());
  }
}
