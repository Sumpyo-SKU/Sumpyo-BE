package com.app.sumpyo_be.domain.action.controller;

import com.app.sumpyo_be.domain.action.dto.ActionCompleteResponse;
import com.app.sumpyo_be.domain.action.dto.ActionListItem;
import com.app.sumpyo_be.domain.action.dto.ActionStartResponse;
import com.app.sumpyo_be.domain.action.dto.ActionStatusResponse;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.service.ActionService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  @Operation(summary = "조치 목록", description = "expectedAt 오름차순(급한 것부터). zone=mine 이면 내 담당 구역만, status 는 쉼표로 여러 개(기본 PENDING,CHECKED,IN_PROGRESS).")
  @GetMapping
  public BaseResponse<List<ActionListItem>> list(
      @Parameter(description = "구역 필터. 비우면 전체, mine 이면 내 담당 구역", example = "mine")
      @RequestParam(required = false) String zone,
      @Parameter(description = "상태 필터 (쉼표로 여러 개)", example = "PENDING,CHECKED")
      @RequestParam(required = false) List<ActionStatus> status) {
    return BaseResponse.success(actionService.list(zone, status));
  }

  @Operation(summary = "조치 확인", description = "PENDING → CHECKED. 이미 CHECKED 면 그대로 성공, 그 외 상태는 409.")
  @PatchMapping("/{actionId}/check")
  public BaseResponse<ActionStatusResponse> check(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("확인 처리되었습니다.", actionService.check(actionId));
  }

  @Operation(summary = "환기 시작", description = "PENDING/CHECKED → IN_PROGRESS. 시작 시점 CO2 와 처리자를 기록합니다.")
  @PostMapping("/{actionId}/start")
  public BaseResponse<ActionStartResponse> start(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("환기를 시작했습니다.", actionService.start(actionId));
  }

  @Operation(summary = "조치 완료", description = "IN_PROGRESS → DONE. 완료 시점 CO2 로 목표 달성 여부를 계산하고, 연결된 민원도 완료 처리합니다.")
  @PostMapping("/{actionId}/complete")
  public BaseResponse<ActionCompleteResponse> complete(@Parameter(description = "조치 ID", example = "88") @PathVariable Long actionId) {
    return BaseResponse.success("조치를 완료했습니다.", actionService.complete(actionId));
  }
}
