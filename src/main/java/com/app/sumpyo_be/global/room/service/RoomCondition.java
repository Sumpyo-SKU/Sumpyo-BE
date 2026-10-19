package com.app.sumpyo_be.global.room.service;

import com.app.sumpyo_be.global.air.RoomStatus;
import com.app.sumpyo_be.global.reading.dto.ReadingSnapshot;

/** 방 하나의 현재 상태. reading 은 최근 10분 내 측정값이 없으면 null */
public record RoomCondition(ReadingSnapshot reading, RoomStatus status, Integer minutesToExceed) {

  public Integer co2() {
    return reading == null ? null : reading.co2();
  }

  public Integer temp() {
    return reading == null ? null : reading.temp();
  }

  public Integer humidity() {
    return reading == null ? null : reading.humidity();
  }
}
