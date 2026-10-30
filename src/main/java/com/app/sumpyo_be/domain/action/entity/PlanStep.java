package com.app.sumpyo_be.domain.action.entity;

/**
 * action.plan JSON 배열의 한 단계 (내부 값 객체).
 * @param windows    WINDOW_OPEN 창문 수 (1~4), 그 외 null
 * @param targetTemp AC_ON 희망 온도 (18~28), 그 외 null
 * @param minutes    WINDOW_OPEN 추천 환기 시간 (5분 단위, 10~60), 그 외 null. 결과 측정 시각 계산에 쓴다
 */
public record PlanStep(StepType type, String label, Integer windows, Integer targetTemp, Integer minutes) {
}
