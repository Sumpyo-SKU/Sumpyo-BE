package com.app.sumpyo_be.domain.action.controller;

import com.app.sumpyo_be.global.common.ScreenTag;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.action.dto.response.ActionCompleteResponse;
import com.app.sumpyo_be.domain.action.dto.response.ActionListItem;
import com.app.sumpyo_be.domain.action.dto.response.ActionStartResponse;
import com.app.sumpyo_be.domain.action.dto.response.ActionStatusResponse;
import com.app.sumpyo_be.domain.action.dto.request.ActionStepRequest;
import com.app.sumpyo_be.domain.action.dto.response.ComplaintActionResponse;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.service.ActionService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조치 목록 · [확인] / [환기 시작] / [완료] */
@RestController
@Tag(name = "Action", description = "환기 조치 (목록 · 확인 · 시작 · 완료)")
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

  private final ActionService actionService;

  /**
   * 조치 목록. zone=mine 이면 내 담당 구역만, status 는 쉼표로 여러 개 (기본 PENDING,CHECKED,IN_PROGRESS)
   * 예: GET /api/actions?zone=mine&status=PENDING,CHECKED
   */
  @Operation(summary = "조치 목록", description = "expectedAt 오름차순(급한 것부터). zone=mine 이면 내 담당 구역만, buildingId 면 그 건물만, "
      + "status 는 쉼표로 여러 개(기본 PENDING,CHECKED,IN_PROGRESS). "
      + "홈 '창문 환기 예측' 카드: 지났거나 10분 이내면 timeLabel '지금'·relative null·metricLabel 'CO2', "
      + "아니면 timeLabel HH:mm·relative '30분 뒤'·metricLabel 'CO2 예상'. "
      + "title: CLOSE '창문을 닫아주세요', OPEN 이고 그 시각 전에 수업이 시작하면 '수업 전에 열어두세요', 그 외 '창문 열어주세요'. "
      + "reason: CO2 'N분 뒤 기준 초과 예정'(지났으면 '기준 초과'), 비 예보 CLOSE '1시간 뒤 비 예상', 민원 '학생 민원 접수'.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_ZONE_FILTER}, auth = true, validation = true)
  @Tag(name = ScreenTag.HOME)
  @GetMapping
  public BaseResponse<List<ActionListItem>> list(
      @Parameter(description = "구역 필터. 비우면 전체, mine 이면 내 담당 구역", example = "mine")
      @RequestParam(required = false) String zone,
      @Parameter(description = "건물 필터 (zone 과 함께 쓸 수 있음). 홈 '창문 환기 예측 · 북악관' 카드", example = "1")
      @RequestParam(required = false) Long buildingId,
      @Parameter(description = "상태 필터 (쉼표로 여러 개)", example = "PENDING,CHECKED")
      @RequestParam(required = false) List<ActionStatus> status) {
    return BaseResponse.success(actionService.list(zone, buildingId, status));
  }

  @Operation(summary = "조치 확인", description = "PENDING → CHECKED. 이미 CHECKED 면 그대로 성공, 그 외 상태는 409.")
  @ApiErrors({GlobalErrorCode.ACTION_NOT_FOUND, GlobalErrorCode.INVALID_ACTION_STATUS})
  @Tag(name = ScreenTag.CAMPUS)
  @PatchMapping("/{actionId}/check")
  public BaseResponse<ActionStatusResponse> check(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("확인 처리되었습니다.", actionService.check(actionId));
  }

  @Operation(summary = "환기 시작 / 추천대로 처리 시작",
      description = "PENDING/CHECKED → IN_PROGRESS. 시작 시점 CO2 와 처리자를 기록합니다. 민원 조치면 민원도 IN_PROGRESS. "
          + "민원 추천 조치는 창문 시각을 찍지 않고 체크리스트(PATCH /steps)로 기록하며, 단계가 없는 추천(시설팀 신고)은 바로 완료됩니다.")
  @ApiErrors({GlobalErrorCode.ACTION_NOT_FOUND, GlobalErrorCode.INVALID_ACTION_STATUS})
  @Tag(name = ScreenTag.CAMPUS)
  @Tag(name = ScreenTag.INBOX)
  @PostMapping("/{actionId}/start")
  public BaseResponse<ActionStartResponse> start(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("환기를 시작했습니다.", actionService.start(actionId));
  }

  @Operation(summary = "체크리스트 단계 완료",
      description = "민원 추천 조치의 체크리스트. IN_PROGRESS 조치만(아니면 409), plan 에 있는 단계만(아니면 400), "
          + "이미 체크했으면 그대로 200. WINDOW_OPEN → window_opened_at, WINDOW_CLOSE → window_closed_at, "
          + "AC_ON → ac_on_at + 희망 온도. 모든 단계를 마치면 자동 완료(조치·민원 DONE). "
          + "결과 CO2 는 창문 단계가 있으면 measureAt(창문 연 시각 + 추천 분)에 서버가 재고, 그 전까지 actualCo2After·goalReached 는 null.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_ACTION_STEP, GlobalErrorCode.ACTION_NOT_FOUND, GlobalErrorCode.INVALID_ACTION_STATUS}, validation = true)
  @Tag(name = ScreenTag.INBOX)
  @PatchMapping("/{actionId}/steps")
  public BaseResponse<ComplaintActionResponse> checkStep(
      @Parameter(description = "조치 ID", example = "120") @PathVariable Long actionId,
      @Valid @RequestBody ActionStepRequest request) {
    return BaseResponse.success(actionService.checkStep(actionId, request));
  }

  @Operation(summary = "조치 완료", description = "예측 조치용. IN_PROGRESS → DONE. 완료 시점 CO2 로 목표 달성 여부를 계산하고, 연결된 민원도 완료 처리합니다.")
  @ApiErrors({GlobalErrorCode.ACTION_NOT_FOUND, GlobalErrorCode.INVALID_ACTION_STATUS})
  @PostMapping("/{actionId}/complete")
  public BaseResponse<ActionCompleteResponse> complete(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("조치를 완료했습니다.", actionService.complete(actionId));
  }
}
