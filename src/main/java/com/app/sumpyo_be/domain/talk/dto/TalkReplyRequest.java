package com.app.sumpyo_be.domain.talk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/talks/{talkId}/replies. content 길이(앞뒤 공백 제거 후 1~300자)는 서비스에서 검사 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 답변")
public class TalkReplyRequest {

  @Schema(description = "답변 (1~300자)", example = "617호 지금 수업 중이에요",
      requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 300)
  private String content;

  // JSON "isSuggested" 로 받으려고 Boolean (boolean 이면 "suggested" 로 매핑된다)
  @Schema(description = "추천 답변 칩을 골라 보냈는지 (없으면 false)", example = "true")
  private Boolean isSuggested;
}
