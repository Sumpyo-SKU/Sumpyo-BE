package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.dto.response.ActionStep;
import com.app.sumpyo_be.domain.action.dto.response.ComplaintActionResponse;
import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Action → ComplaintActionResponse. 체크리스트는 plan 순서 + 단계별 수행 시각, 목표 달성은 결과가 있을 때만 */
@Component
@RequiredArgsConstructor
public class ComplaintActionAssembler {

  private final PlanCodec planCodec;
  private final MemberRepository memberRepository;
  private final SensorReadingRepository readingRepository;
  private final ComplaintRepository complaintRepository;
  private final Clock clock;

  /** 예상 효과 (지표, 조치 전, 조치 후). 계산할 수 없으면 NONE (셋 다 null) */
  record Effect(Metric metric, Integer before, Integer after) {
    static final Effect NONE = new Effect(null, null, null);
  }

  public ComplaintActionResponse toResponse(Action a) {
    List<PlanStep> plan = planCodec.read(a.getPlan());
    Effect effect = effect(plan, a.getCo2Before(), a.getExpectedCo2After(), acTargetTemp(a, plan), () -> currentTemp(a));
    return ComplaintActionResponse.builder()
        .actionId(a.getId())
        .status(a.getStatus())
        .summary(a.getSummary())
        .reason(a.getReason())
        .source(a.getPlanSource())
        .co2Before(a.getCo2Before())
        .expectedCo2After(a.getExpectedCo2After())
        .effectMetric(effect.metric())
        .effectBefore(effect.before())
        .effectAfter(effect.after())
        .forecastCo2(a.getForecastCo2())
        .steps(steps(a, plan))
        .completedAt(a.getCompletedAt())
        .measureAt(a.getMeasureAt())
        .actualCo2After(a.getActualCo2After())
        .goalReached(ActionService.goalReached(a.getActualCo2After(), a.getExpectedCo2After(),
            processorThreshold(a.getMemberId())))
        .build();
  }

  /**
   * 예상 효과. 창문 단계가 있으면 CO2 (조치 전 CO2 → 예상 CO2), 아니면 에어컨 단계가 있을 때 TEMP (현재 온도 → 희망 온도).
   * 값이 하나라도 없으면 다음 규칙, 다 안 되면 NONE (프론트가 null 을 0 으로 그리지 않게 0 은 만들지 않는다).
   * @param currentTemp 현재 온도 (에어컨 효과를 낼 때만 부른다)
   */
  static Effect effect(List<PlanStep> plan, Integer co2Before, Integer expectedCo2After, Integer acTargetTemp,
      Supplier<Integer> currentTemp) {
    boolean window = plan.stream().anyMatch(s -> s.type() == StepType.WINDOW_OPEN);
    if (window && co2Before != null && expectedCo2After != null) {
      return new Effect(Metric.CO2, co2Before, expectedCo2After);
    }
    boolean ac = plan.stream().anyMatch(s -> s.type() == StepType.AC_ON);
    if (ac && acTargetTemp != null) {
      Integer temp = currentTemp.get();
      if (temp != null) {
        return new Effect(Metric.TEMP, temp, acTargetTemp);
      }
    }
    return Effect.NONE;
  }

  /** 희망 온도: 체크 때 바꾼 값, 없으면 추천 값 */
  private static Integer acTargetTemp(Action a, List<PlanStep> plan) {
    if (a.getAcTargetTemp() != null) {
      return a.getAcTargetTemp();
    }
    return plan.stream().filter(s -> s.type() == StepType.AC_ON).map(PlanStep::targetTemp)
        .filter(Objects::nonNull).findFirst().orElse(null);
  }

  /** 현재 온도: 최근 10분 측정, 없으면 민원 접수 때 값 (반올림) */
  private Integer currentTemp(Action a) {
    LocalDateTime now = LocalDateTime.now(clock);
    Integer measured = readingRepository.findLatestSince(List.of(a.getRoom().getId()), now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream().findFirst().map(ReadingSnapshot::getTemp).orElse(null);
    if (measured != null || a.getComplaintId() == null) {
      return measured;
    }
    return complaintRepository.findById(a.getComplaintId())
        .map(c -> c.getTempSnapshot() == null ? null : (int) Math.round(c.getTempSnapshot().doubleValue()))
        .orElse(null);
  }

  /** plan 이 없는 조치(추천 없이 만든 민원 조치)는 기록된 단계만 시간 순으로 */
  private List<ComplaintActionResponse.Step> steps(Action a, List<PlanStep> plan) {
    if (!a.hasPlan()) {
      return ActionStep.from(a).stream()
          .map(s -> ComplaintActionResponse.Step.builder().type(s.getType()).label(s.getLabel())
              .doneAt(s.getDoneAt()).build())
          .toList();
    }
    return plan.stream().map(s -> step(a, s)).toList();
  }

  private static ComplaintActionResponse.Step step(Action a, PlanStep s) {
    return ComplaintActionResponse.Step.builder()
        .type(s.type())
        .label(s.label())
        .windows(s.windows())
        .targetTemp(s.type() == StepType.AC_ON && a.getAcTargetTemp() != null
            ? a.getAcTargetTemp() : s.targetTemp())
        .minutes(s.minutes())
        .doneAt(a.stepDoneAt(s.type()))
        .build();
  }

  /** 처리자의 CO2 기준. 처리자가 없으면(비로그인) 기본 1000 */
  int processorThreshold(Long memberId) {
    if (memberId == null) {
      return Thresholds.DEFAULT.co2();
    }
    return memberRepository.findById(memberId).map(Thresholds::from).orElse(Thresholds.DEFAULT).co2();
  }
}
