package com.app.sumpyo_be.global.room.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.room.dto.LatestReadingResponse;
import com.app.sumpyo_be.global.room.dto.PredictionSeriesResponse;
import com.app.sumpyo_be.global.room.dto.ReadingSeriesResponse;
import com.app.sumpyo_be.global.room.dto.RoomResponse;
import com.app.sumpyo_be.global.room.service.RoomService;
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

/** 강의실 바텀시트·상세 + 그래프 (요청/응답 예시: docs/campus-api.md) */
@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;

  @GetMapping
  public BaseResponse<RoomResponse> room(@PathVariable Long roomId) {
    return BaseResponse.success(roomService.getRoom(roomId));
  }

  /** 마지막 측정값 + 센서 연결 여부 */
  @GetMapping("/readings/latest")
  public BaseResponse<LatestReadingResponse> latestReading(@PathVariable Long roomId) {
    return BaseResponse.success(roomService.getLatestReading(roomId));
  }

  /** 그래프 실측. from/to 를 비우면 오늘 0시 ~ 지금, interval(분) 기본 5 */
  @GetMapping("/readings")
  public BaseResponse<ReadingSeriesResponse> readings(@PathVariable Long roomId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @RequestParam(defaultValue = "5") @Min(1) @Max(60) int interval) {
    return BaseResponse.success(roomService.getReadings(roomId, from, to, interval));
  }

  /** 그래프 예측. from/to 를 비우면 지금 ~ 내일 0시 */
  @GetMapping("/predictions")
  public BaseResponse<PredictionSeriesResponse> predictions(@PathVariable Long roomId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    return BaseResponse.success(roomService.getPredictions(roomId, from, to));
  }
}
