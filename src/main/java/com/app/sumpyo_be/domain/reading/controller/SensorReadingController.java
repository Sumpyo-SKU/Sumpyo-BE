package com.app.sumpyo_be.domain.reading.controller;

import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.common.PublicApi;
import io.swagger.v3.oas.annotations.Hidden;
import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.config.DeviceProperties;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.reading.dto.request.SensorPayload;
import com.app.sumpyo_be.domain.reading.dto.response.SensorReadingResponse;
import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "SensorReading", description = "센서 측정값 수집 (아두이노)")
@RequestMapping("/api/rooms/{code}/readings")
@RequiredArgsConstructor
public class SensorReadingController {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Duration MAX_RANGE = Duration.ofDays(31);
  private final SensorReadingRepository repository;
  private final RoomRepository roomRepository;
  private final DeviceProperties deviceProperties;

  // TODO: ESP32 가 아직 /api/rooms/517/readings 로 보냄. 펌웨어 교체 전까지 device.aliases 로 517 → bukak-617 매핑
  @Operation(summary = "측정값 저장 (센서 전용)", description = "ESP32 가 1분마다 보내는 CO2·온습도를 저장합니다. X-Device-Key 헤더가 강의실 키와 맞아야 합니다. 517 은 bukak-617 로 처리합니다.")
  @PublicApi
  @ApiErrors(value = {GlobalErrorCode.INVALID_DEVICE_KEY, GlobalErrorCode.ROOM_NOT_FOUND}, validation = true)
  @PostMapping
  public BaseResponse<Void> save(
      @Parameter(description = "강의실 코드", example = "bukak-617") @PathVariable String code,
      @Parameter(description = "기기 인증 키") @RequestHeader("X-Device-Key") String key,
      @Valid @RequestBody SensorPayload payload) {
    String roomCode = deviceProperties.resolveCode(code);
    if (!deviceProperties.isValid(roomCode, key)) {
      throw new CustomException(GlobalErrorCode.INVALID_DEVICE_KEY);
    }
    repository.save(SensorReading.of(findRoom(roomCode), payload, LocalDateTime.now(KST)));
    return BaseResponse.success("측정 데이터가 저장되었습니다.", null);
  }

  // 아래 두 GET 은 roomId 기반 API 와 경로가 겹쳐 RoomController 로 옮김:
  //   GET /api/rooms/{code}/readings        → GET /api/rooms/{roomId}/readings (그래프 실측)
  //   GET /api/rooms/{code}/readings/latest → GET /api/rooms/{roomId}/readings/latest

  /**
   * 기록 화면용 구간 조회. [from, to) 범위이며 최대 31일까지 조회할 수 있습니다.
   * 예: /range?from=2026-10-14T00:00:00&to=2026-10-15T00:00:00
   */
  @Operation(summary = "구간 측정값 조회", description = "기록 화면용 원본 측정값. [from, to) 범위, 최대 31일.")
  @Hidden
  @GetMapping("/range")
  public BaseResponse<List<SensorReadingResponse>> range(
      @Parameter(description = "강의실 코드", example = "bukak-617") @PathVariable String code,
      @Parameter(description = "시작 시각 (KST, 포함)", example = "2026-10-14T00:00:00")
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @Parameter(description = "끝 시각 (KST, 미포함)", example = "2026-10-15T00:00:00")
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new CustomException(GlobalErrorCode.INVALID_TIME_RANGE);
    }
    Room room = findRoom(deviceProperties.resolveCode(code));
    List<SensorReading> readings = repository
        .findByRoomAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(room, from, to);
    return BaseResponse.success(toResponses(readings, room));
  }

  private Room findRoom(String code) {
    return roomRepository.findByCode(code)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_NOT_FOUND));
  }

  private List<SensorReadingResponse> toResponses(List<SensorReading> readings, Room room) {
    return readings.stream().map(r -> SensorReadingResponse.from(r, room.getCode())).toList();
  }
}
