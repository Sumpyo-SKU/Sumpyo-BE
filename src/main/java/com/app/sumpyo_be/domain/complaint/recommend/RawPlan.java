package com.app.sumpyo_be.domain.complaint.recommend;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import java.util.List;

/**
 * 추천기가 고른 조치 (숫자 전). steps 는 type·windows·targetTemp 만, 라벨·창문 분은 PlanCalculator 가 채운다.
 * summary 가 null 이면 단계로 서버가 문장을 만든다.
 */
public record RawPlan(List<PlanStep> steps, String summary, String reason) {
}
