package com.app.sumpyo_be.global.room.dto;

import java.time.LocalDateTime;
import java.util.List;

/** GET /api/rooms/{roomId}/readings : 그래프 실측 (interval 분 평균, at = 구간 시작 시각) */
public record ReadingSeriesResponse(int interval, List<Point> points) {

  public record Point(LocalDateTime at, Integer co2, Integer temp, Integer humidity) {}
}
