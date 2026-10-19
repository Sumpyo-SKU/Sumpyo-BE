package com.app.sumpyo_be.global.action.dto;

/**
 * POST /api/actions/{actionId}/complete
 * TODO: 명세의 응답 필드가 goalReached 까지만 확인됨. 나머지 필드 받으면 추가.
 */
public record ActionCompleteResponse(
    Integer co2Before,
    Integer actualCo2After,
    boolean goalReached   // 완료 시점 CO2 가 기준 미만이면 true (임시 정의)
) {}
