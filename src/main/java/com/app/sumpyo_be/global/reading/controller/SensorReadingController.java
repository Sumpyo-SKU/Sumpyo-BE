package com.app.sumpyo_be.global.reading.controller;

import com.app.sumpyo_be.global.config.DeviceProperties;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.reading.dto.SensorPayload;
import com.app.sumpyo_be.global.reading.entity.SensorReading;
import com.app.sumpyo_be.global.reading.repository.SensorReadingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
  private final SensorReadingRepository repository;
  private final DeviceProperties deviceProperties;

  @PostMapping
  public ResponseEntity<Void> save(@PathVariable String roomId,
      @RequestHeader("X-Device-Key") String key,
      @Valid @RequestBody SensorPayload payload) {
    if (!deviceProperties.isValid(roomId, key)) {
      throw new CustomException(GlobalErrorCode.INVALID_DEVICE_KEY);
    }
    repository.save(SensorReading.of(roomId, payload, LocalDateTime.now(KST)));
    return ResponseEntity.ok().build();
  }

  @GetMapping("/latest")
  public ResponseEntity<SensorReading> latest(@PathVariable String roomId) {
    return ResponseEntity.of(repository.findTopByRoomIdOrderByMeasuredAtDesc(roomId));
  }

  @GetMapping
  public List<SensorReading> recent(@PathVariable String roomId,
      @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int minutes) {
    return repository.findByRoomIdAndMeasuredAtAfterOrderByMeasuredAtAsc(
        roomId, LocalDateTime.now(KST).minusMinutes(minutes));
  }
}