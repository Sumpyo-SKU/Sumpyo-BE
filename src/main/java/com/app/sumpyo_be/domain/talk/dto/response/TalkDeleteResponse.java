package com.app.sumpyo_be.domain.talk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DELETE /api/talks/{talkId} */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 삭제 결과")
public class TalkDeleteResponse {

  @Schema(description = "삭제한 캠퍼스톡 ID", example = "5")
  private Long talkId;
}
