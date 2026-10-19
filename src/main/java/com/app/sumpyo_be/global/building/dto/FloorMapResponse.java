package com.app.sumpyo_be.global.building.dto;

import com.app.sumpyo_be.global.air.RoomStatus;
import com.app.sumpyo_be.global.room.entity.MapSide;
import com.app.sumpyo_be.global.room.entity.RoomType;
import java.util.List;

/** GET /api/buildings/{buildingId}/floors/{floor} */
public record FloorMapResponse(
    String building,
    int floor,
    long alertCount,      // OVER + PREDICTED 방 수
    List<MapRoom> rooms   // TOP → BOTTOM → 배치 미정(null), 줄 안에서는 order 순 (null 은 맨 뒤)
) {
  public record MapRoom(
      Long roomId,
      String number,
      RoomType type,
      MapSide side,       // room.map_side
      Integer order,      // room.map_order
      Integer co2,        // CO2 센서 없음/끊김이면 null
      RoomStatus status,
      boolean simulated   // 시뮬레이터 데이터(sensor_level = SIM)
  ) {}
}
