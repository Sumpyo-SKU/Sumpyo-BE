package com.app.sumpyo_be.domain.action.dto.response;

import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.PlanSource;
import com.app.sumpyo_be.domain.action.entity.StepType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 민원 조치 상세 (추천 · 체크리스트 · 결과). POST /api/complaints/{id}/recommendation, PATCH /api/actions/{id}/steps,
 * GET /api/complaints/{id} 의 action.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "민원 조치 상세 (AI 추천 · 체크리스트 · 결과)")
public class ComplaintActionResponse {

  @Schema(description = "조치 ID", example = "120")
  private Long actionId;

  @Schema(description = "조치 상태 (PENDING: 추천됨, CHECKED: 확인, IN_PROGRESS: 처리 중, DONE: 완료)", example = "PENDING")
  private ActionStatus status;

  @Schema(description = "추천 조치 문장 (분은 서버가 계산해 넣음)", example = "창문 2곳을 30분 열고 에어컨을 틀어주세요")
  private String summary;

  @Schema(description = "추천 이유 한 줄", example = "실내 CO2가 높고 바깥이 선선해서 창문 환기가 효과적이에요")
  private String reason;

  @Schema(description = "추천 출처 (LLM, RULE: 규칙 기반 대체). 추천 없이 만든 조치는 null", example = "LLM")
  private PlanSource source;

  @Schema(description = "조치 전 CO2 (ppm). 추천 때 현재 값(없으면 접수 스냅샷), 처리 시작 때 다시 잰 값", example = "1250")
  private Integer co2Before;

  @Schema(description = "예상 CO2 (ppm). 창문 단계가 있을 때만 (420 + (C−420)·e^(−k·분/60), C = 현재와 30분 뒤 예측 중 큰 값), "
      + "아니면 null. 화면 '예상 효과'는 effectMetric·effectBefore·effectAfter 를 쓸 것 (null 을 0 으로 그리지 말 것)",
      example = "779", nullable = true)
  private Integer expectedCo2After;

  @Schema(description = "효과 지표 (추천·처리 중 '예상 효과', 완료 'CO₂ 변화' 카드). 창문 단계가 있으면 CO2, "
      + "없고 에어컨 단계가 있으면 TEMP. 값이 없으면 다음 규칙, 계산할 수 없으면 null 이고 effectBefore·effectAfter·effectUnit 도 null "
      + "(0 은 내려주지 않으니 null 을 0 으로 그리지 말 것)", example = "CO2", nullable = true)
  private Metric effectMetric;

  @Schema(description = "조치 전 값. CO2 면 co2Before (ppm). TEMP 면 추천·처리 중엔 현재 온도 "
      + "(최근 10분 측정, 없으면 민원 접수 때 값), 완료면 처리 시작 때 온도 (시작 전 10분 안 측정). effectMetric 이 null 이면 null",
      example = "1250", nullable = true)
  private Integer effectBefore;

  @Schema(description = "조치 후 값. CO2 면 추천·처리 중엔 expectedCo2After, 완료면 결과 CO2 (actualCo2After, 측정 전이면 효과 전부 null). "
      + "TEMP 면 추천·처리 중엔 에어컨 희망 온도, 완료면 완료 때 온도 (결과 측정 시각, 없으면 완료 시각 전 10분 안 측정). "
      + "effectMetric 이 null 이면 null", example = "757", nullable = true)
  private Integer effectAfter;

  @Schema(description = "효과 단위 (CO2 'ppm', TEMP '°C'). effectMetric 이 null 이면 null", example = "ppm", nullable = true)
  private String effectUnit;

  @Schema(description = "그대로 두면 예상 CO2 (ppm, predict.py 예측: 수업 종료 시각, 수업이 없으면 30분 뒤). 예측이 없으면 null. "
      + "화면 '그대로 두면 1,280ppm'", example = "1280")
  private Integer forecastCo2;

  @Schema(description = "체크리스트 (추천 순서). doneAt 이 있으면 완료")
  private List<Step> steps;

  @Schema(description = "완료 시각 (KST). 완료 전 null", example = "2026-10-21T14:36:00")
  private LocalDateTime completedAt;

  @Schema(description = "결과 CO2 를 잴 시각 (창문 연 시각 + 추천 분, 창문 단계가 없으면 완료 시각). 완료 전 null",
      example = "2026-10-21T14:36:00")
  private LocalDateTime measureAt;

  @Schema(description = "결과 CO2 (ppm). 측정 전이면 null → 프론트가 'N분 뒤 결과를 확인할 수 있어요'", example = "780")
  private Integer actualCo2After;

  @Schema(description = "목표 달성 (예상 CO2 가 있으면 결과 ≤ 예상, 없으면 결과 < 처리자 기준값). 측정 전 null", example = "true")
  private Boolean goalReached;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "ComplaintActionStep", description = "체크리스트 단계")
  public static class Step {

    @Schema(description = "단계 (WINDOW_OPEN: 창문 열기, WINDOW_CLOSE: 창문 닫기, AC_ON: 에어컨 켜기)", example = "AC_ON")
    private StepType type;

    @Schema(description = "표시 이름", example = "에어컨 켜기")
    private String label;

    @Schema(description = "열 창문 수 (WINDOW_OPEN 만)", example = "null")
    private Integer windows;

    @Schema(description = "희망 온도 °C (AC_ON 만)", example = "23")
    private Integer targetTemp;

    @Schema(description = "추천 환기 시간 (분, WINDOW_OPEN 만)", example = "null")
    private Integer minutes;

    @Schema(description = "수행 시각 (KST). 아직이면 null", example = "2026-10-21T14:06:00")
    private LocalDateTime doneAt;
  }
}
