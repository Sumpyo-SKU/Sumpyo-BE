package com.app.sumpyo_be.global.action.service;

import com.app.sumpyo_be.global.action.dto.ActionCompleteResponse;
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
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import com.app.sumpyo_be.global.member.service.CurrentMemberProvider;
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
  private final ThresholdService thresholdService;
  private final Clock clock;

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
    // TODO: 로그인 구현 전이라 null. CurrentMemberProvider 가 로그인 사용자를 돌려주면 자동으로 채워진다.
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
