package com.app.sumpyo_be.global.action.controller;

import com.app.sumpyo_be.global.action.dto.ActionCompleteResponse;
import com.app.sumpyo_be.global.action.dto.ActionStartResponse;
import com.app.sumpyo_be.global.action.dto.ActionStatusResponse;
import com.app.sumpyo_be.global.action.service.ActionService;
import com.app.sumpyo_be.global.common.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 조치 [확인] / [환기 시작] / [완료] (요청/응답 예시: docs/campus-api.md) */
@RestController
@RequestMapping("/api/actions/{actionId}")
@RequiredArgsConstructor
public class ActionController {

  private final ActionService actionService;

  @PatchMapping("/check")
  public BaseResponse<ActionStatusResponse> check(@PathVariable Long actionId) {
    return BaseResponse.success("확인 처리되었습니다.", actionService.check(actionId));
  }

  @PostMapping("/start")
  public BaseResponse<ActionStartResponse> start(@PathVariable Long actionId) {
    return BaseResponse.success("환기를 시작했습니다.", actionService.start(actionId));
  }

  @PostMapping("/complete")
  public BaseResponse<ActionCompleteResponse> complete(@PathVariable Long actionId) {
    return BaseResponse.success("조치를 완료했습니다.", actionService.complete(actionId));
  }
}
