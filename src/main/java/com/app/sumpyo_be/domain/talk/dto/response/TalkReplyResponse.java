package com.app.sumpyo_be.domain.talk.dto.response;

import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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

  @Schema(description = "답한 시각 (KST)", example = "2026-10-21T14:05:00")
  private LocalDateTime createdAt;
}
