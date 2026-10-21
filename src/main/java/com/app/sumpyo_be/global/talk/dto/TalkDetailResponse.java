package com.app.sumpyo_be.global.talk.dto;

import com.app.sumpyo_be.global.talk.entity.TalkStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/talks/{talkId} */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 상세")
public class TalkDetailResponse {

  @Schema(description = "캠퍼스톡 ID", example = "5")
  private Long talkId;

  @Schema(description = "건물 이름 (헤더 '북악관 1층')", example = "북악관")
  private String building;

  @Schema(description = "대상. 강의실이 있으면 '6층 · 617호', 없으면 '1층'", example = "1층")
  private String target;

  @Schema(description = "내용", example = "북악관 1층에 계신 분 있나요?")
  private String content;

  @Schema(description = "상태 (UNREAD: 미확인, CHECKING: 확인 중, ANSWERED: 답변 완료)", example = "CHECKING")
  private TalkStatus status;

  @Schema(description = "내가 보낸 글인지", example = "false")
  private boolean sentByMe;

  @Schema(description = "보낸 시각 (KST)", example = "2026-10-21T14:02:00")
  private LocalDateTime createdAt;

  @ArraySchema(arraySchema = @Schema(description = "추천 답변 칩 (최대 3개). 내가 보낸 글이면 빈 배열"),
      schema = @Schema(example = "지금 북악관 1층에 있어요"))
  private List<String> suggestions;

  @Schema(description = "답변 목록, 오래된 순")
  private List<Reply> replies;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "답변")
  public static class Reply {

    @Schema(description = "답변 ID", example = "9")
    private Long replyId;

    @Schema(description = "내용", example = "617호 지금 수업 중이에요")
    private String content;

    @Schema(description = "답한 근로생 이름", example = "김근로")
    private String author;

    // boolean 으로 두면 Lombok getter 가 isIsSuggested/isSuggested 가 돼 JSON 이름이 "suggested" 로 바뀐다
    @Schema(description = "추천 답변을 골라 보냈는지", example = "true")
    private Boolean isSuggested;

    @Schema(description = "답한 시각 (KST)", example = "2026-10-21T14:05:00")
    private LocalDateTime createdAt;
  }
}
