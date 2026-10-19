package com.app.sumpyo_be.global.action.service;

import com.app.sumpyo_be.global.action.dto.ActionCompleteResponse;
import com.app.sumpyo_be.global.action.dto.ActionListItem;
import com.app.sumpyo_be.global.action.dto.ActionStartResponse;
import com.app.sumpyo_be.global.action.dto.ActionStatusResponse;
import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import com.app.sumpyo_be.global.action.repository.ActionRepository;
import com.app.sumpyo_be.global.air.AirQualityJudge;
import com.app.sumpyo_be.global.complaint.entity.Complaint;
import com.app.sumpyo_be.global.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.member.entity.Member;
import com.app.sumpyo_be.global.member.entity.Role;
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import com.app.sumpyo_be.global.member.service.CurrentMemberProvider;
import com.app.sumpyo_be.global.member.service.MemberAccess;
import com.app.sumpyo_be.global.member.service.ThresholdService;
import com.app.sumpyo_be.global.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.global.reading.repository.SensorReadingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
  private final Clock clock;

  /** 목록 기본 상태: 아직 끝나지 않은 조치 */
  static final List<ActionStatus> ACTIVE = List.of(ActionStatus.PENDING, ActionStatus.CHECKED, ActionStatus.IN_PROGRESS);

  /**
   * 조치 목록, expectedAt(없으면 createdAt) 오름차순 = 급한 것부터.
   * @param zone     null 이면 전체, "mine" 이면 내 담당 구역(member_zone 건물+층)의 방만. 담당 구역이 없으면 빈 목록
   * @param statuses null/빈 값이면 PENDING·CHECKED·IN_PROGRESS
   */
  @Transactional(readOnly = true)
  public List<ActionListItem> list(String zone, List<ActionStatus> statuses) {
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
    int fallbackThreshold = thresholdService.current().co2();
    LocalDateTime now = LocalDateTime.now(clock);
    return actions.stream().map(a -> ActionListItem.of(a, fallbackThreshold, now)).toList();
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

  /** [환기 시작] PENDING/CHECKED → IN_PROGRESS. co2_before = 최신 CO2, member_id = 처리자 */
  public ActionStartResponse start(Long actionId) {
    Action action = findAction(actionId);
    if (!action.getStatus().isWaiting()) {
      throw new CustomException(GlobalErrorCode.INVALID_ACTION_STATUS);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    // 로그인 전에는 데모 근로생(worker01). 데모 계정이 없으면 null
    Long memberId = currentMemberProvider.currentMemberId().orElse(null);
    action.start(latestCo2(action.getRoom().getId(), now), memberId, now);
    return ActionStartResponse.of(action, memberName(memberId));
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

    // 조치 생성 당시 기준값이 있으면 그것, 없으면 현재 사용자 기준값
    int threshold = action.getThreshold() != null ? action.getThreshold() : thresholdService.current().co2();
    boolean goalReached = actualCo2After != null && actualCo2After < threshold;
    return new ActionCompleteResponse(action.getCo2Before(), actualCo2After, goalReached, action.getStatus());
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
        .map(ReadingSnapshot::co2)
        .orElse(null);
  }

  private String memberName(Long memberId) {
    return memberId == null ? null : memberRepository.findById(memberId).map(Member::getName).orElse(null);
  }
}
