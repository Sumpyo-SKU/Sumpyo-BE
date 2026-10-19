package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionSource;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import java.util.List;

/** POST /api/actions/{actionId}/start */
public record ActionStartResponse(
    Long actionId,
    Long roomId,
    ActionSource source,
    Long complaintId,
    String summary,
    Integer co2Before,
    Integer expectedCo2After,
    Integer actualCo2After,
    List<ActionStep> steps,   // 기록된 단계만 시간 순 (start 직후엔 WINDOW_OPEN 1개)
    ActionStatus status,
    String handledBy      // 처리자 이름, 비로그인이면 null
) {
  public static ActionStartResponse of(Action a, String handledBy) {
    return new ActionStartResponse(a.getId(), a.getRoom().getId(), a.getSource(), a.getComplaintId(),
        a.getSummary(), a.getCo2Before(), a.getExpectedCo2After(), a.getActualCo2After(),
        ActionStep.from(a), a.getStatus(), handledBy);
  }
}
