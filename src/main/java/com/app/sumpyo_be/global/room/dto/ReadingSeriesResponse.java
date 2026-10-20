package com.app.sumpyo_be.global.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/{roomId}/readings : 그래프 실측 (interval 분 평균, at = 구간 시작 시각) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강의실 실측 그래프")
public class ReadingSeriesResponse {

  @Schema(description = "평균 구간 (분)", example = "5")
  private int interval;

  @Schema(description = "구간별 평균값 목록 (시간 순)")
  private List<Point> points;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "실측 구간 평균 한 점")
  public static class Point {

    @Schema(description = "구간 시작 시각 (KST)", example = "2026-10-16T14:00:00")
    private LocalDateTime at;

    @Schema(description = "평균 CO2 (ppm)", example = "980")
    private Integer co2;

    @Schema(description = "평균 온도 (°C)", example = "24")
    private Integer temp;

    @Schema(description = "평균 습도 (%)", example = "45")
    private Integer humidity;
  }
}
