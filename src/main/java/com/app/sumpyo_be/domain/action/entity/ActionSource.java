package com.app.sumpyo_be.domain.action.entity;

public enum ActionSource {
  PREDICTION, COMPLAINT,
  /** 추천 조치 없이 근로생이 직접 [환기 하기] */
  MANUAL
}
