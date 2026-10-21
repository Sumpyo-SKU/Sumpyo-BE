package com.app.sumpyo_be.domain.room.dto;

import com.app.sumpyo_be.domain.air.Co2Level;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * GET /api/rooms/{roomId}/readings/latest
 * 마지막 측정값. 10분 넘게 지난 값이어도 내려주고 sensorOnline=false 로 표시한다.
 * 측정이 한 번도 없으면 값은 모두 null, sensorOnline=false.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강의실 최신 측정값")
public class LatestReadingResponse {

  @Schema(description = "강의실 ID", example = "7")
  private Long roomId;

  @Schema(description = "CO2 (ppm)", example = "1050")
  private Integer co2;

  @Schema(description = "온도 (°C)", example = "24")
  private Integer temp;

  @Schema(description = "습도 (%)", example = "45")
  private Integer humidity;

  @Schema(description = "쾌적도 점수 (0~100)", example = "72")
  private Integer comfortScore;

  @Schema(description = "CO2 수준 (NORMAL: 정상, CAUTION: 주의 = 기준×0.9 이상, CRITICAL: 위험 = 기준×1.2 이상)",
      example = "CAUTION")
  private Co2Level level;

  @Schema(description = "측정 시각 (KST)", example = "2026-10-16T14:05:00")
  private LocalDateTime measuredAt;

  @Schema(description = "센서 연결 여부 (최근 10분 안에 측정이 있으면 true)", example = "true")
  private boolean sensorOnline;
}
