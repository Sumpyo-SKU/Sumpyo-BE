package com.app.sumpyo_be.domain.action.entity;

/** 민원 추천 조치의 출처 */
public enum PlanSource {
  /** LLM 이 조치를 고르고 숫자는 서버가 계산 */
  LLM,
  /** 키 없음·타임아웃·검증 실패 때 규칙 기반 */
  RULE
}
