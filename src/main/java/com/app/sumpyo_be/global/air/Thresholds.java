package com.app.sumpyo_be.global.air;

import com.app.sumpyo_be.global.member.entity.Member;

/** 판단 기준값. 로그인 사용자의 member 설정, 비로그인이면 DEFAULT. */
public record Thresholds(int co2, int tempMin, int tempMax, int humidityMin, int humidityMax) {

  public static final Thresholds DEFAULT = new Thresholds(1000, 20, 26, 40, 60);

  /** member 의 nullable 컬럼은 기본값으로 채운다 */
  public static Thresholds from(Member m) {
    return new Thresholds(
        or(m.getCo2Threshold(), DEFAULT.co2),
        or(m.getTempMin(), DEFAULT.tempMin),
        or(m.getTempMax(), DEFAULT.tempMax),
        or(m.getHumidityMin(), DEFAULT.humidityMin),
        or(m.getHumidityMax(), DEFAULT.humidityMax));
  }

  private static int or(Integer value, int fallback) {
    return value != null ? value : fallback;
  }
}
