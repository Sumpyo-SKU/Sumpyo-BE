package com.app.sumpyo_be.domain.complaint.controller;

import com.app.sumpyo_be.global.common.ScreenTag;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.action.dto.response.ComplaintActionResponse;
import com.app.sumpyo_be.domain.complaint.dto.response.ComplaintDetailResponse;
import com.app.sumpyo_be.domain.complaint.dto.response.ComplaintListResponse;
import com.app.sumpyo_be.domain.complaint.dto.request.ComplaintRecommendationRequest;
import com.app.sumpyo_be.domain.complaint.dto.response.RepeatedComplaintResponse;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import com.app.sumpyo_be.domain.complaint.service.ComplaintInboxService;
import com.app.sumpyo_be.domain.complaint.service.ComplaintRecommendationService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 민원 접수함 (근로생·관리자). 학생 제출(POST /api/complaints)은 ComplaintController */
@RestController
@Tag(name = "Complaint Inbox", description = "민원 접수함 (목록 · 반복 민원 · 상세 · AI 추천)")
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintInboxController {

  private final ComplaintInboxService inboxService;
  private final ComplaintRecommendationService recommendationService;

  @Operation(summary = "민원 목록 (무한 스크롤)",
      description = "권한 W, A. 접수 최신순(created_at DESC, id DESC) 무한 스크롤. 다음 페이지는 응답의 nextCursor 를 cursor 로 "
          + "(같은 시각이어도 (created_at, id) 로 이어서 중복·누락 없음). q 는 건물명·호실 번호·내용 부분 검색, "
          + "roomId 는 반복 민원 카드의 [관련 민원 확인]용. counts 는 status·cursor 를 뺀 같은 필터 기준.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_CURSOR}, auth = true, validation = true)
  @Tag(name = ScreenTag.INBOX)
  @GetMapping
  public BaseResponse<ComplaintListResponse> list(
      @Parameter(description = "탭 (비우면 전체)", example = "RECEIVED") @RequestParam(required = false) ComplaintStatus status,
      @Parameter(description = "건물 ID", example = "1") @RequestParam(required = false) Long buildingId,
      @Parameter(description = "강의실 ID", example = "6") @RequestParam(required = false) Long roomId,
      @Parameter(description = "검색어 (건물명·호실 번호·내용)", example = "618") @RequestParam(required = false) String q,
      @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) String cursor,
      @Parameter(description = "페이지 크기 (1~50)", example = "20")
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return BaseResponse.success(inboxService.list(status, buildingId, roomId, q, cursor, size));
  }

  @Operation(summary = "이번 주 반복 민원",
      description = "권한 W, A. 그 주(월~일) 민원이 5건 이상인 방, 많은 순. overThresholdDays 는 그 주 중 방 최고 CO2 가 "
          + "내 기준 이상이었던 날 수(CO2 센서 없는 방은 null). message 예: '이번 주 7건 · 센서도 매일 1,000ppm 초과'.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_WEEK}, auth = true)
  @Tag(name = ScreenTag.INBOX)
  @GetMapping("/repeated")
  public BaseResponse<List<RepeatedComplaintResponse>> repeated(
      @Parameter(description = "건물 ID", example = "1") @RequestParam(required = false) Long buildingId,
      @Parameter(description = "ISO 주 (비우면 이번 주)", example = "2026-W43") @RequestParam(required = false) String week) {
    return BaseResponse.success(inboxService.repeated(buildingId, week));
  }

  @Operation(summary = "민원 상세",
      description = "권한 W, A. 접수 시점 측정값은 내 기준값으로 판정(snapshot, 측정이 없었으면 null). "
          + "action 은 연결된 가장 최근 조치 (RECEIVED → AI 추천, IN_PROGRESS → 체크리스트, DONE → CO2 변화·목표 달성).")
  @ApiErrors(value = {GlobalErrorCode.COMPLAINT_NOT_FOUND}, auth = true)
  @Tag(name = ScreenTag.INBOX)
  @GetMapping("/{complaintId}")
  public BaseResponse<ComplaintDetailResponse> detail(
      @Parameter(description = "민원 ID", example = "15") @PathVariable Long complaintId) {
    return BaseResponse.success(inboxService.detail(complaintId));
  }

  @Operation(summary = "AI 추천 조치",
      description = "권한 W, A. 접수(RECEIVED) 민원만, 아니면 409. 대기 중인 추천이 있으면 그대로 돌려주고, "
          + "{ \"retry\": true }([다른 방법])면 이전 추천과 다른 새 추천으로 교체. "
          + "LLM 이 조치(창문 열기·닫기, 에어컨)를 고르고 시간·예상 CO2 는 서버가 계산 "
          + "(k = 1.8회/시간 × 창문 수/2, 바깥 420ppm, 목표 800ppm, 5분 단위 최대 30분). "
          + "LLM 키 없음·10초 타임아웃·검증 실패면 규칙 기반(source RULE). 추천대로 처리 시작은 POST /api/actions/{actionId}/start.")
  @ApiErrors(value = {GlobalErrorCode.COMPLAINT_NOT_FOUND, GlobalErrorCode.INVALID_COMPLAINT_STATUS}, auth = true)
  @Tag(name = ScreenTag.INBOX)
  @PostMapping("/{complaintId}/recommendation")
  public BaseResponse<ComplaintActionResponse> recommend(
      @Parameter(description = "민원 ID", example = "15") @PathVariable Long complaintId,
      @RequestBody(required = false) ComplaintRecommendationRequest request) {
    return BaseResponse.success(recommendationService.recommend(complaintId, request != null && request.wantsRetry()));
  }
}
