package com.app.sumpyo_be.domain.talk.dto.response;

import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
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

  @Schema(description = "글쓴이")
  private Sender sender;

  @Schema(description = "답변 목록, 오래된 순")
  private List<Reply> replies;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TalkSender", description = "글쓴이")
  public static class Sender {

    @Schema(description = "이름", example = "김근로")
    private String name;

    @Schema(description = "글쓴이 근로 구역 ('북악관 · 6층', 여러 층이면 '북악관 · 5·6층', 여러 건물이면 첫 건물). 없으면 null",
        example = "북악관 · 6층")
    private String zone;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TalkReply", description = "답변")
  public static class Reply {

    @Schema(description = "답변 ID", example = "9")
    private Long replyId;

    @Schema(description = "내용", example = "617호 지금 수업 중이에요")
    private String content;

    @Schema(description = "답한 근로생 '이름 · 담당 층' (그 글 건물 기준, 여러 층이면 '김근로 · 5·6층'). "
        + "그 건물 담당 구역이 없으면 이름만", example = "김근로 · 6층")
    private String author;

    // boolean 으로 두면 Lombok getter 가 isIsSuggested/isSuggested 가 돼 JSON 이름이 "suggested" 로 바뀐다
    @Schema(description = "추천 답변을 골라 보냈는지", example = "true")
    private Boolean isSuggested;

    @Schema(description = "내가 쓴 답변인지 (삭제 버튼 표시)", example = "false")
    private boolean mine;

    @Schema(description = "삭제된 답변인지. true 면 content 는 '삭제된 답변입니다'", example = "false")
    private boolean deleted;

    @Schema(description = "답한 시각 (KST)", example = "2026-10-21T14:05:00")
    private LocalDateTime createdAt;
  }
}
