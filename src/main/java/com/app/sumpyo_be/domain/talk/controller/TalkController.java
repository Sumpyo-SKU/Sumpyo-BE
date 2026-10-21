package com.app.sumpyo_be.domain.talk.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.talk.dto.TalkCreateRequest;
import com.app.sumpyo_be.domain.talk.dto.TalkCreateResponse;
import com.app.sumpyo_be.domain.talk.dto.TalkDetailResponse;
import com.app.sumpyo_be.domain.talk.dto.TalkListResponse;
import com.app.sumpyo_be.domain.talk.dto.TalkReplyRequest;
import com.app.sumpyo_be.domain.talk.dto.TalkReplyResponse;
import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import com.app.sumpyo_be.domain.talk.service.TalkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 캠퍼스톡 (근로생끼리 소통). 권한 W */
@RestController
@Tag(name = "Talk", description = "캠퍼스톡 (근로생끼리 소통, 소통 범위 = 내 건물)")
@RequestMapping("/api/talks")
@RequiredArgsConstructor
public class TalkController {

  private final TalkService talkService;

  @Operation(summary = "캠퍼스톡 목록 (무한 스크롤)",
      description = "권한 W. 내 건물(담당 구역의 첫 번째 건물) 글 중 연락 볼 구역에 맞는 것, 최신순. "
          + "필터: 없으면 건물 전체 / 층만이면 그 층 모든 글 / 층+강의실이면 그 강의실로 보낸 글만 (층마다 OR). "
          + "처음엔 cursor 없이, 다음부터는 응답의 nextCursor 를 cursor 로 보낸다 (hasNext=false 면 끝). "
          + "counts 는 같은 필터 기준 전체 개수. 담당 구역이 없으면 400.")
  @GetMapping
  public BaseResponse<TalkListResponse> list(
      @Parameter(description = "탭 (비우면 전체)", example = "UNREAD")
      @RequestParam(required = false) TalkStatus status,
      @Parameter(description = "이전 응답의 nextCursor. 첫 요청은 비움", example = "42")
      @RequestParam(required = false) Long cursor,
      @Parameter(description = "한 번에 가져올 개수 (1~50)", example = "20")
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return BaseResponse.success(talkService.list(status, cursor, size));
  }

  @Operation(summary = "캠퍼스톡 보내기",
      description = "권한 W. 건물은 내 건물로 자동. floor 필수(1~maxFloor), roomId 선택(내 건물·그 층의 방), content 1~300자. 저장 후 바로 응답하고 추천 답변은 커밋 후 비동기로 만든다.")
  @PostMapping
  public BaseResponse<TalkCreateResponse> create(@Valid @RequestBody TalkCreateRequest request) {
    return BaseResponse.success("캠퍼스톡을 보냈습니다.", talkService.create(request));
  }

  @Operation(summary = "캠퍼스톡 상세",
      description = "권한 W. 내 건물 글만 (아니면 403). 보낸 사람이 아닌 근로생이 처음 열면 UNREAD → CHECKING, "
          + "추천 답변은 글 작성 후 비동기로 LLM(실패하면 규칙 기반)이 만들어 저장. 상세 조회는 LLM 을 부르지 않는다: "
          + "저장된 지 30분 이내면 그대로, 아직 없으면 규칙 기반으로 즉시(저장은 비동기 결과가), "
          + "30분 지났으면 규칙 기반으로 다시 만들어 저장. 보낸 사람에겐 suggestions 가 빈 배열.")
  @GetMapping("/{talkId}")
  public BaseResponse<TalkDetailResponse> detail(
      @Parameter(description = "캠퍼스톡 ID", example = "5") @PathVariable Long talkId) {
    return BaseResponse.success(talkService.detail(talkId));
  }

  @Operation(summary = "캠퍼스톡 답변",
      description = "권한 W. 내 건물 글이고 보낸 사람 본인이 아니어야 함 (아니면 403). content 1~300자. 답변이 달리면 ANSWERED.")
  @PostMapping("/{talkId}/replies")
  public BaseResponse<TalkReplyResponse> reply(
      @Parameter(description = "캠퍼스톡 ID", example = "5") @PathVariable Long talkId,
      @RequestBody TalkReplyRequest request) {
    return BaseResponse.success("답변을 보냈습니다.", talkService.reply(talkId, request));
  }
}
