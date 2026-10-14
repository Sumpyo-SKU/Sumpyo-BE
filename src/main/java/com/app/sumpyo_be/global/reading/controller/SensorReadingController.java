package com.app.sumpyo_be.global.reading.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.config.DeviceProperties;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.reading.dto.SensorPayload;
import com.app.sumpyo_be.global.reading.dto.SensorReadingResponse;
import com.app.sumpyo_be.global.reading.entity.SensorReading;
import com.app.sumpyo_be.global.reading.repository.SensorReadingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{roomId}/readings")
@RequiredArgsConstructor
public class SensorReadingController {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Duration MAX_RANGE = Duration.ofDays(31);
  private final SensorReadingRepository repository;
  private final DeviceProperties deviceProperties;

  @PostMapping
  public BaseResponse<Void> save(@PathVariable String roomId,
      @RequestHeader("X-Device-Key") String key,
      @Valid @RequestBody SensorPayload payload) {
    if (!deviceProperties.isValid(roomId, key)) {
      throw new CustomException(GlobalErrorCode.INVALID_DEVICE_KEY);
    }
    repository.save(SensorReading.of(roomId, payload, LocalDateTime.now(KST)));
    return BaseResponse.success("측정 데이터가 저장되었습니다.", null);
  }

  @GetMapping("/latest")
  public BaseResponse<SensorReadingResponse> latest(@PathVariable String roomId) {
    SensorReading reading = repository.findTopByRoomIdOrderByMeasuredAtDesc(roomId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.READING_NOT_FOUND));
    return BaseResponse.success(SensorReadingResponse.from(reading));
  }

  @GetMapping
  public BaseResponse<List<SensorReadingResponse>> recent(@PathVariable String roomId,
      @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int minutes) {
    List<SensorReading> readings = repository.findByRoomIdAndMeasuredAtAfterOrderByMeasuredAtAsc(
        roomId, LocalDateTime.now(KST).minusMinutes(minutes));
    return BaseResponse.success(toResponses(readings));
  }

  /**
   * 기록 화면용 구간 조회. [from, to) 범위이며 최대 31일까지 조회할 수 있습니다.
   * 예: /range?from=2026-10-14T00:00:00&to=2026-10-15T00:00:00
   */
  @GetMapping("/range")
  public BaseResponse<List<SensorReadingResponse>> range(@PathVariable String roomId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new CustomException(GlobalErrorCode.INVALID_TIME_RANGE);
    }
    List<SensorReading> readings = repository
        .findByRoomIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(roomId, from, to);
    return BaseResponse.success(toResponses(readings));
  }

  private List<SensorReadingResponse> toResponses(List<SensorReading> readings) {
    return readings.stream().map(SensorReadingResponse::from).toList();
  }
}
