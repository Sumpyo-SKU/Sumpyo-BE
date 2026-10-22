package com.app.sumpyo_be.domain.talk.dto.response;

import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/talks */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 목록")
public class TalkListResponse {

  @Schema(description = "내 건물 이름 (제목 '소통 · 북악관')", example = "북악관")
  private String building;

  @Schema(description = "적용된 연락 볼 구역 (GET /api/me/talk-filter 의 floors 와 같은 형식). 빈 배열이면 건물 전체")
  private List<TalkFilterResponse.Floor> filter;

  @Schema(description = "탭별 개수 (같은 필터 기준)")
  private Counts counts;

  @Schema(description = "글 목록, 최신순 (최대 size 개)")
  private List<Item> content;

  @Schema(description = "더 불러올 글이 있는지", example = "true")
  private boolean hasNext;

  @Schema(description = "다음 요청의 cursor (이번 목록 마지막 글의 talkId). hasNext=false 면 null", example = "42",
      nullable = true)
  private Long nextCursor;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TalkCounts", description = "탭별 개수")
  public static class Counts {

    @Schema(description = "전체", example = "12")
    private long all;

    @Schema(description = "미확인 (UNREAD)", example = "7")
    private long unread;

    @Schema(description = "확인 중 (CHECKING)", example = "3")
    private long checking;

    @Schema(description = "답변 완료 (ANSWERED)", example = "2")
    private long answered;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TalkListItem", description = "글 카드")
  public static class Item {

    @Schema(description = "캠퍼스톡 ID", example = "5")
    private Long talkId;

    @Schema(description = "대상. 강의실이 있으면 '6층 · 617호', 없으면 '1층'", example = "6층 · 617호")
    private String target;

    @Schema(description = "내용 미리보기 (한 줄, 최대 50자)", example = "지금 수업 중인가요?")
    private String preview;

    @Schema(description = "상태 (UNREAD: 미확인, CHECKING: 확인 중, ANSWERED: 답변 완료)", example = "UNREAD")
    private TalkStatus status;

    @Schema(description = "내가 보낸 글인지", example = "false")
    private boolean sentByMe;

    @Schema(description = "보낸 시각 (KST). '6분 전' 은 프론트에서 계산", example = "2026-10-21T14:02:00")
    private LocalDateTime createdAt;
  }
}
