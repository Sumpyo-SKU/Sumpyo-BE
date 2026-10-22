package com.app.sumpyo_be.domain.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/{roomId}/predictions : 그래프 예측 (target_at 마다 가장 최근 예측) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강의실 예측 그래프")
public class PredictionSeriesResponse {

  @Schema(description = "예측 모델 버전. 예측이 없으면 null", example = "v1")
  private String modelVersion;

  @Schema(description = "예측 점 목록 (시간 순)")
  private List<Point> points;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "PredictionSeriesPoint", description = "예측 한 점")
  public static class Point {

    @Schema(description = "예측 대상 시각 (KST)", example = "2026-10-16T14:10:00")
    private LocalDateTime at;

    @Schema(description = "예측 CO2 (ppm)", example = "1250")
    private Integer co2;

    @Schema(description = "예측 온도 (°C)", example = "24.5")
    private BigDecimal temp;

    @Schema(description = "예측 습도 (%)", example = "46")
    private Integer humidity;
  }
}
