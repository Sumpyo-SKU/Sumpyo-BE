package com.app.sumpyo_be.domain.action.entity;

/** 조치 체크리스트 단계 (민원 추천 plan, 진행 기록) */
public enum StepType {
  /** 창문 열기 → action.window_opened_at */
  WINDOW_OPEN,
  /** 창문 닫기 → action.window_closed_at */
  WINDOW_CLOSE,
  /** 에어컨 켜기 → action.ac_on_at, ac_target_temp */
  AC_ON
}
