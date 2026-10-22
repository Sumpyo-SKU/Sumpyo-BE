package com.app.sumpyo_be.domain.talk.controller;

import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.talk.dto.request.TalkFilterRequest;
import com.app.sumpyo_be.domain.talk.dto.response.TalkFilterResponse;
import com.app.sumpyo_be.domain.talk.service.TalkFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 캠퍼스톡 연락 볼 구역 (목록 필터일 뿐 권한 아님). 권한 W */
@RestController
@Tag(name = "Talk", description = "캠퍼스톡 (근로생끼리 소통, 소통 범위 = 내 건물)")
@RequestMapping("/api/me/talk-filter")
@RequiredArgsConstructor
public class TalkFilterController {

  private final TalkFilterService filterService;

  @Operation(summary = "연락 볼 구역 조회",
      description = "권한 W. floors 가 비면 건물 전체, rooms 가 비면 그 층 전체. 담당 구역이 없으면 400.")
  @ApiErrors(value = {GlobalErrorCode.TALK_NO_ZONE}, auth = true)
  @GetMapping
  public BaseResponse<TalkFilterResponse> get() {
    return BaseResponse.success(filterService.get());
  }

  @Operation(summary = "연락 볼 구역 저장",
      description = "권한 W. 보낸 내용으로 통째로 교체 (한 트랜잭션). floors 빈 배열 = 건물 전체, roomIds 빈 배열 = 그 층 전체. "
          + "floor 1~maxFloor·층 중복 400, roomId 는 내 건물·그 층의 LECTURE 방이어야 함 (아니면 400), 중복은 하나로 합침.")
  @ApiErrors(value = {GlobalErrorCode.TALK_NO_ZONE, GlobalErrorCode.INVALID_TALK_FLOOR, GlobalErrorCode.DUPLICATED_TALK_FLOOR, GlobalErrorCode.INVALID_TALK_ROOM}, auth = true, validation = true)
  @PutMapping
  public BaseResponse<TalkFilterResponse> replace(@Valid @RequestBody TalkFilterRequest request) {
    return BaseResponse.success("연락 볼 구역을 저장했습니다.", filterService.replace(request));
  }
}
