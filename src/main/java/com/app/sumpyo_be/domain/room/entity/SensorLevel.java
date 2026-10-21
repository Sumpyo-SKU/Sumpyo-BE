package com.app.sumpyo_be.domain.room.entity;

/** FULL: CO2+온습도, TH: 온습도만, NONE: 센서 없음, SIM: 시뮬레이터가 만든 CO2+온습도 */
public enum SensorLevel {
  FULL, TH, NONE, SIM;

  /** CO2 값이 있는 방 (실측 FULL 또는 시뮬레이터 SIM) */
  public boolean hasCo2() {
    return this == FULL || this == SIM;
  }

  public boolean isSimulated() {
    return this == SIM;
  }
}
