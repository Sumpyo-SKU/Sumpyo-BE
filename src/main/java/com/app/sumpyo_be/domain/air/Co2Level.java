package com.app.sumpyo_be.domain.air;

/** 기준값 대비 CO2 수준 */
public enum Co2Level {
  NORMAL,    // 기준의 90% 미만
  CAUTION,   // 기준의 90% 이상
  CRITICAL   // 기준의 120% 이상
}
