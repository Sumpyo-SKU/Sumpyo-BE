package com.app.sumpyo_be.global.talk.dto;

import com.app.sumpyo_be.global.talk.entity.TalkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 답변 결과")
public class TalkReplyResponse {

  @Schema(description = "답변 ID", example = "9")
  private Long replyId;

  @Schema(description = "답변 후 캠퍼스톡 상태", example = "ANSWERED")
  private TalkStatus talkStatus;
}
