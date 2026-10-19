package com.app.sumpyo_be.global.action.controller;

import com.app.sumpyo_be.global.action.dto.ActionCompleteResponse;
import com.app.sumpyo_be.global.action.dto.ActionListItem;
import com.app.sumpyo_be.global.action.dto.ActionStartResponse;
import com.app.sumpyo_be.global.action.dto.ActionStatusResponse;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import com.app.sumpyo_be.global.action.service.ActionService;
import com.app.sumpyo_be.global.common.BaseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조치 목록 · [확인] / [환기 시작] / [완료] (요청/응답 예시: docs/campus-api.md, docs/me-api.md) */
@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

  private final ActionService actionService;

  /**
   * 조치 목록. zone=mine 이면 내 담당 구역만, status 는 쉼표로 여러 개 (기본 PENDING,CHECKED,IN_PROGRESS)
   * 예: GET /api/actions?zone=mine&status=PENDING,CHECKED
   */
  @GetMapping
  public BaseResponse<List<ActionListItem>> list(@RequestParam(required = false) String zone,
      @RequestParam(required = false) List<ActionStatus> status) {
    return BaseResponse.success(actionService.list(zone, status));
  }

  @PatchMapping("/{actionId}/check")
  public BaseResponse<ActionStatusResponse> check(@PathVariable Long actionId) {
    return BaseResponse.success("확인 처리되었습니다.", actionService.check(actionId));
  }

  @PostMapping("/{actionId}/start")
  public BaseResponse<ActionStartResponse> start(@PathVariable Long actionId) {
    return BaseResponse.success("환기를 시작했습니다.", actionService.start(actionId));
  }

  @PostMapping("/{actionId}/complete")
  public BaseResponse<ActionCompleteResponse> complete(@PathVariable Long actionId) {
    return BaseResponse.success("조치를 완료했습니다.", actionService.complete(actionId));
  }
}
