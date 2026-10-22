package com.app.sumpyo_be.domain.outdoor.dto.response;

import com.app.sumpyo_be.domain.outdoor.entity.OutdoorObservation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/outdoor/latest : 홈 [지금] "바깥 공기 · 창문 환기" */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "가장 최근 바깥 공기 (기상청 초단기실황 + 에어코리아)")
public class OutdoorLatestResponse {

  @Schema(description = "실외 기온 (°C)", example = "18.0")
  private Double temp;

  @Schema(description = "실외 습도 (%)", example = "62")
  private Integer humidity;

  @Schema(description = "미세먼지 PM10 (µg/m³)", example = "28")
  private Integer pm10;

  @Schema(description = "관측 시각 (KST, 정시)", example = "2026-10-22T10:00:00")
  private LocalDateTime observedAt;

  public static OutdoorLatestResponse from(OutdoorObservation o) {
    return new OutdoorLatestResponse(o.getTemp() == null ? null : o.getTemp().doubleValue(), o.getHumidity(),
        o.getPm10(), o.getObservedAt());
  }
}
