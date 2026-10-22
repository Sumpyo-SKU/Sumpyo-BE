package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.dto.ActionStep;
import com.app.sumpyo_be.domain.action.dto.ComplaintActionResponse;
import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Action → ComplaintActionResponse. 체크리스트는 plan 순서 + 단계별 수행 시각, 목표 달성은 결과가 있을 때만 */
@Component
@RequiredArgsConstructor
public class ComplaintActionAssembler {

  private final PlanCodec planCodec;
  private final MemberRepository memberRepository;

  public ComplaintActionResponse toResponse(Action a) {
    return ComplaintActionResponse.builder()
        .actionId(a.getId())
        .status(a.getStatus())
        .summary(a.getSummary())
        .reason(a.getReason())
        .source(a.getPlanSource())
        .co2Before(a.getCo2Before())
        .expectedCo2After(a.getExpectedCo2After())
        .steps(steps(a))
        .completedAt(a.getCompletedAt())
        .measureAt(a.getMeasureAt())
        .actualCo2After(a.getActualCo2After())
        .goalReached(ActionService.goalReached(a.getActualCo2After(), a.getExpectedCo2After(),
            processorThreshold(a.getMemberId())))
        .build();
  }

  /** plan 이 없는 조치(추천 없이 만든 민원 조치)는 기록된 단계만 시간 순으로 */
  private List<ComplaintActionResponse.Step> steps(Action a) {
    if (!a.hasPlan()) {
      return ActionStep.from(a).stream()
          .map(s -> ComplaintActionResponse.Step.builder().type(s.getType()).label(s.getLabel())
              .doneAt(s.getDoneAt()).build())
          .toList();
    }
    return planCodec.read(a.getPlan()).stream().map(s -> step(a, s)).toList();
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
