package com.app.sumpyo_be.domain.talk.dto.response;

import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DELETE /api/talks/{talkId}/replies/{replyId} */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "답변 삭제 결과")
public class TalkReplyDeleteResponse {

  @Schema(description = "삭제한 답변 ID", example = "9")
  private Long replyId;

  @Schema(description = "삭제 후 글 상태 (남은 답변이 없으면 CHECKING 으로 되돌아감)", example = "CHECKING")
  private TalkStatus talkStatus;
}
