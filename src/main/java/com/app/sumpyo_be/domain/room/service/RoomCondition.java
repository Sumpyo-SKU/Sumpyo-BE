package com.app.sumpyo_be.domain.room.service;

import com.app.sumpyo_be.domain.air.RoomStatus;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;

/** 방 하나의 현재 상태. reading 은 최근 10분 내 측정값이 없으면 null */
public record RoomCondition(ReadingSnapshot reading, RoomStatus status, Integer minutesToExceed) {

  public Integer co2() {
    return reading == null ? null : reading.getCo2();
  }

  public Integer temp() {
    return reading == null ? null : reading.getTemp();
  }

  public Integer humidity() {
    return reading == null ? null : reading.getHumidity();
  }
}
