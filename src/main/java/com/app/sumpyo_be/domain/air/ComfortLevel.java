package com.app.sumpyo_be.domain.air;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 쾌적도 등급 (쾌적도 점수 구간). 판정은 AirQualityJudge.comfortLevel */
@Getter
@RequiredArgsConstructor
public enum ComfortLevel {
  GOOD("좋음"),     // 70 이상
  NORMAL("보통"),   // 41 ~ 69
  BAD("나쁨");      // 40 이하

  private final String label;
}
