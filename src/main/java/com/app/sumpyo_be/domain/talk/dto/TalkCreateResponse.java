package com.app.sumpyo_be.domain.talk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 보내기 결과")
public class TalkCreateResponse {

  @Schema(description = "캠퍼스톡 ID", example = "5")
  private Long talkId;
}
