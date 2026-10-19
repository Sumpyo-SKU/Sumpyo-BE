package com.app.sumpyo_be.global.room.dto;

import com.app.sumpyo_be.global.air.Co2Level;
import java.time.LocalDateTime;

/**
 * GET /api/rooms/{roomId}/readings/latest
 * 마지막 측정값. 10분 넘게 지난 값이어도 내려주고 sensorOnline=false 로 표시한다.
 * 측정이 한 번도 없으면 값은 모두 null, sensorOnline=false.
 */
public record LatestReadingResponse(
    Long roomId,
    Integer co2,
    Integer temp,
    Integer humidity,
    Integer comfortScore,
    Co2Level level,
    LocalDateTime measuredAt,
    boolean sensorOnline
) {}
