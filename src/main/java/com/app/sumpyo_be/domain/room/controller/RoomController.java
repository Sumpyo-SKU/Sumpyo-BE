package com.app.sumpyo_be.domain.room.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.room.dto.LatestReadingResponse;
import com.app.sumpyo_be.domain.room.dto.PredictionSeriesResponse;
import com.app.sumpyo_be.domain.room.dto.ReadingSeriesResponse;
import com.app.sumpyo_be.domain.room.dto.RoomResponse;
import com.app.sumpyo_be.domain.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 강의실 바텀시트·상세 + 그래프 */
@RestController
@Tag(name = "Room", description = "강의실 상세 · 그래프")
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;

  @Operation(summary = "강의실 상세", description = "평면도 바텀시트·강의실 상세 공용. 현재 CO2·온습도, 쾌적도, 대기 조치, 현재/다음 수업을 반환합니다.")
  @GetMapping
  public BaseResponse<RoomResponse> room(@Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId) {
    return BaseResponse.success(roomService.getRoom(roomId));
  }

  /** 마지막 측정값 + 센서 연결 여부 */
  @Operation(summary = "최신 측정값", description = "마지막 측정값과 센서 연결 여부(최근 10분 내 측정)를 반환합니다.")
  @GetMapping("/readings/latest")
  public BaseResponse<LatestReadingResponse> latestReading(@Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId) {
    return BaseResponse.success(roomService.getLatestReading(roomId));
  }

  /** 그래프 실측. from/to 를 비우면 오늘 0시 ~ 지금, interval(분) 기본 5 */
  @Operation(summary = "실측 그래프", description = "interval 분 평균값 목록. from/to 를 비우면 오늘 0시 ~ 지금, interval 기본 5분(1~60), 최대 31일.")
  @GetMapping("/readings")
  public BaseResponse<ReadingSeriesResponse> readings(
      @Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId,
      @Parameter(description = "시작 시각 (KST, 포함)", example = "2026-10-16T00:00:00")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @Parameter(description = "끝 시각 (KST, 미포함)", example = "2026-10-16T18:00:00")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @Parameter(description = "평균 구간 (분, 1~60)", example = "5")
      @RequestParam(defaultValue = "5") @Min(1) @Max(60) int interval) {
    return BaseResponse.success(roomService.getReadings(roomId, from, to, interval));
  }

  /** 그래프 예측. from/to 를 비우면 지금 ~ 내일 0시 */
  @Operation(summary = "예측 그래프", description = "시각마다 가장 최근 예측값 목록. from/to 를 비우면 지금 ~ 내일 0시.")
  @GetMapping("/predictions")
  public BaseResponse<PredictionSeriesResponse> predictions(
      @Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId,
      @Parameter(description = "시작 시각 (KST)", example = "2026-10-16T14:00:00")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @Parameter(description = "끝 시각 (KST)", example = "2026-10-17T00:00:00")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    return BaseResponse.success(roomService.getPredictions(roomId, from, to));
  }
}
