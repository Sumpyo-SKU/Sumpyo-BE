package com.app.sumpyo_be.domain.air;

/** 평면도 칸 색/카드 상태 */
public enum RoomStatus {
  OVER,       // 최신 CO2 ≥ 기준 (빨강)
  PREDICTED,  // 30분 안에 기준 초과 예측 (노랑)
  NORMAL,     // 정상 (회색)
  NO_SENSOR   // CO2 센서 없음(sensor_level NONE/TH) 또는 10분 넘게 데이터 없음(센서 끊김)
}
