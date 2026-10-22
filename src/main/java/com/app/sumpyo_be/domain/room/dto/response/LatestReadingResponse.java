package com.app.sumpyo_be.domain.room.dto.response;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.ComfortLevel;
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

  @Schema(description = AirQualityJudge.COMFORT_SCORE_DESCRIPTION, example = "74")
  private Integer comfortScore;

  @Schema(description = "쾌적도 등급 (GOOD: 좋음 = 70 이상, NORMAL: 보통 = 41~69, BAD: 나쁨 = 40 이하). 점수가 없으면 null",
      example = "GOOD")
  private ComfortLevel level;

  @Schema(description = "측정 시각 (KST)", example = "2026-10-16T14:05:00")
  private LocalDateTime measuredAt;

  @Schema(description = "센서 연결 여부 (최근 10분 안에 측정이 있으면 true)", example = "true")
  private boolean sensorOnline;
}
