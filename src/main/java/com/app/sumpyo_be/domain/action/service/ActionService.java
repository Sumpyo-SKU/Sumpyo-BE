package com.app.sumpyo_be.domain.action.service;

import com.app.sumpyo_be.domain.action.dto.ActionCompleteResponse;
import com.app.sumpyo_be.domain.action.dto.ActionListItem;
import com.app.sumpyo_be.domain.action.dto.ActionStartResponse;
import com.app.sumpyo_be.domain.action.dto.ActionStatusResponse;
import com.app.sumpyo_be.domain.action.dto.ActionStepRequest;
import com.app.sumpyo_be.domain.action.dto.ComplaintActionResponse;
import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import com.app.sumpyo_be.domain.member.service.CurrentMemberProvider;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.member.service.ThresholdService;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ActionService {

  private final ActionRepository actionRepository;
  private final SensorReadingRepository readingRepository;
  private final ComplaintRepository complaintRepository;
  private final MemberRepository memberRepository;
  private final CurrentMemberProvider currentMemberProvider;
  private final MemberAccess memberAccess;
  private final ThresholdService thresholdService;
  private final ClassScheduleService classScheduleService;
  private final PlanCodec planCodec;
  private final ComplaintActionAssembler assembler;
  private final ActionMeasureService measureService;
  private final Clock clock;

  /** 창문 단계에 추천 분이 없을 때 결과 CO2 를 잴 때까지의 시간 */
  static final int DEFAULT_MEASURE_MINUTES = 15;

  /** 목록 기본 상태: 아직 끝나지 않은 조치 */
  static final List<ActionStatus> ACTIVE = List.of(ActionStatus.PENDING, ActionStatus.CHECKED, ActionStatus.IN_PROGRESS);

  /**
   * 조치 목록, expectedAt(없으면 createdAt) 오름차순 = 급한 것부터.
   * @param zone     null 이면 전체, "mine" 이면 내 담당 구역(member_zone 건물+층)의 방만. 담당 구역이 없으면 빈 목록
   * @param buildingId null 이 아니면 그 건물 방만 (zone 과 함께 쓸 수 있음, 홈 "창문 환기 예측 · 북악관")
   * @param statuses null/빈 값이면 PENDING·CHECKED·IN_PROGRESS
   */
  @Transactional(readOnly = true)
  public List<ActionListItem> list(String zone, Long buildingId, List<ActionStatus> statuses) {
    List<ActionStatus> filter = statuses == null || statuses.isEmpty() ? ACTIVE : statuses;
    List<Action> actions;
    if (zone == null) {
      actions = actionRepository.findListByStatusIn(filter);
    } else if ("mine".equals(zone)) {
      Member me = memberAccess.require(Role.WORKER, Role.ADMIN);
      actions = actionRepository.findListInMemberZones(me.getId(), filter);
    } else {
      throw new CustomException(GlobalErrorCode.INVALID_ZONE_FILTER);
    }
    if (buildingId != null) {
      actions = actions.stream().filter(a -> a.getRoom().getBuilding().getId().equals(buildingId)).toList();
    }
    if (actions.isEmpty()) {
      return List.of();
    }
    Thresholds t = thresholdService.current();
    LocalDateTime now = LocalDateTime.now(clock);
    Map<Long, List<ClassSchedule>> classes = classScheduleService.classesOn(
        actions.stream().map(a -> a.getRoom().getId()).distinct().toList(), now.toLocalDate());
    return actions.stream()
        .map(a -> ActionListItem.of(a, t, now, classStartsBefore(classes.get(a.getRoom().getId()), now, ActionListItem.dueAt(a))))
        .toList();
  }

  /** (지금, 예상 시각] 사이에 시작하는 수업이 있는지 (오늘 수업만, 휴강 제외) */
  static boolean classStartsBefore(List<ClassSchedule> classes, LocalDateTime now, LocalDateTime dueAt) {
    if (classes == null || !dueAt.isAfter(now)) {
      return false;
    }
    return classes.stream()
        .map(c -> now.toLocalDate().atTime(c.getStartTime()))
        .anyMatch(start -> start.isAfter(now) && !start.isAfter(dueAt));
  }

  /** [확인] PENDING → CHECKED. 이미 CHECKED 면 그대로 성공. */
  public ActionStatusResponse check(Long actionId) {
    Action action = findAction(actionId);
    if (!action.getStatus().isWaiting()) {
      throw new CustomException(GlobalErrorCode.INVALID_ACTION_STATUS);
    }
    action.check();
    return new ActionStatusResponse(action.getStatus());
  }

  /**
   * [환기 시작 / 추천대로 처리 시작] PENDING/CHECKED → IN_PROGRESS. co2_before = 최신 CO2, member_id = 처리자.
   * 민원 조치면 민원도 처리 중. 체크리스트 조치는 창문 시각을 찍지 않고 단계별로 기록하며,
   * 단계가 없는 추천(설비 고장 → 시설팀 신고)은 시작과 동시에 완료한다.
   */
  public ActionStartResponse start(Long actionId) {
    Action action = findAction(actionId);
    if (!action.getStatus().isWaiting()) {
      throw new CustomException(GlobalErrorCode.INVALID_ACTION_STATUS);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    // 로그인 전에는 데모 근로생(worker01). 데모 계정이 없으면 null
    Long memberId = currentMemberProvider.currentMemberId().orElse(null);
    action.start(latestCo2(action.getRoom().getId(), now), memberId, now);
    if (action.getComplaintId() != null) {
      complaintRepository.findById(action.getComplaintId()).ifPresent(Complaint::startProcessing);
    }
    if (action.hasPlan() && planCodec.read(action.getPlan()).isEmpty()) {
      finishPlan(action, List.of(), now);
    }
    return ActionStartResponse.of(action, memberName(memberId));
  }

  /**
   * [체크리스트] 단계 완료. IN_PROGRESS 조치만(409), plan 에 있는 단계만(400), 이미 했으면 그대로 200.
   * plan 의 모든 단계를 마치면 자동 완료 (조치·민원 DONE, 결과 CO2 측정 시각 결정).
   */
  public ComplaintActionResponse checkStep(Long actionId, ActionStepRequest req) {
    Action action = findAction(actionId);
    if (action.getStatus() != ActionStatus.IN_PROGRESS) {
      throw new CustomException(GlobalErrorCode.INVALID_ACTION_STATUS);
    }
    List<PlanStep> plan = planCodec.read(action.getPlan());
    PlanStep step = plan.stream().filter(s -> s.type() == req.getType()).findFirst()
        .orElseThrow(() -> new CustomException(GlobalErrorCode.INVALID_ACTION_STEP));
    LocalDateTime now = LocalDateTime.now(clock);
    action.checkStep(step.type(), req.getTargetTemp() != null ? req.getTargetTemp() : step.targetTemp(), now);
    if (plan.stream().allMatch(s -> action.isStepDone(s.type()))) {
      finishPlan(action, plan, now);
    }
    return assembler.toResponse(action);
  }

  /**
   * 체크리스트 조치 완료. 창문 단계가 있으면 결과 CO2 는 창문 연 시각 + 추천 분(없으면 15분)에 잰다
   * (이미 지났으면 바로). 창문 단계가 없으면 지금 값을 바로 기록한다.
   */
  private void finishPlan(Action action, List<PlanStep> plan, LocalDateTime now) {
    PlanStep window = plan.stream().filter(s -> s.type() == StepType.WINDOW_OPEN).findFirst().orElse(null);
    if (window == null) {
      action.finishPlan(now, now, latestCo2(action.getRoom().getId(), now));
    } else {
      LocalDateTime measureAt = action.getWindowOpenedAt()
          .plusMinutes(window.minutes() != null ? window.minutes() : DEFAULT_MEASURE_MINUTES);
      action.finishPlan(now, measureAt, null);
      if (!measureAt.isAfter(now)) {
        measureService.measure(action);
      }
    }
    if (action.getComplaintId() != null) {
      complaintRepository.findById(action.getComplaintId()).ifPresent(Complaint::done);
    }
  }

  /** [완료] IN_PROGRESS → DONE. actual_co2_after = 최신 CO2, 연결된 민원도 DONE */
  public ActionCompleteResponse complete(Long actionId) {
    Action action = findAction(actionId);
    if (action.getStatus() != ActionStatus.IN_PROGRESS) {
      throw new CustomException(GlobalErrorCode.INVALID_ACTION_STATUS);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    Integer actualCo2After = latestCo2(action.getRoom().getId(), now);
    action.complete(actualCo2After, now);
    if (action.getComplaintId() != null) {
      complaintRepository.findById(action.getComplaintId()).ifPresent(Complaint::done);
    }

    Boolean goalReached = goalReached(actualCo2After, action.getExpectedCo2After(),
        processorThreshold(action.getMemberId()));
    return new ActionCompleteResponse(action.getCo2Before(), actualCo2After, goalReached, action.getStatus());
  }

  /**
   * 목표 달성 여부.
   * - 완료 시점 CO2 가 없으면(센서 끊김) null
   * - expected_co2_after 가 있으면(민원 AI 추천 조치) 그 값 이하
   * - 없으면(예측 조치) 처리자 기준값 미만
   */
  static Boolean goalReached(Integer actualCo2After, Integer expectedCo2After, int processorThreshold) {
    if (actualCo2After == null) {
      return null;
    }
    if (expectedCo2After != null) {
      return actualCo2After <= expectedCo2After;
    }
    return actualCo2After < processorThreshold;
  }

  /** 처리자(환기 시작한 사람)의 CO2 기준값. 처리자가 없으면(비로그인) 기본 1000 */
  private int processorThreshold(Long memberId) {
    if (memberId == null) {
      return Thresholds.DEFAULT.co2();
    }
    return memberRepository.findById(memberId).map(Thresholds::from).orElse(Thresholds.DEFAULT).co2();
  }

  private Action findAction(Long actionId) {
    return actionRepository.findById(actionId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ACTION_NOT_FOUND));
  }

  /** 최근 10분 내 최신 CO2, 없으면 null */
  private Integer latestCo2(Long roomId, LocalDateTime now) {
    return readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream()
        .findFirst()
        .map(ReadingSnapshot::getCo2)
        .orElse(null);
  }

  private String memberName(Long memberId) {
    return memberId == null ? null : memberRepository.findById(memberId).map(Member::getName).orElse(null);
  }
}
