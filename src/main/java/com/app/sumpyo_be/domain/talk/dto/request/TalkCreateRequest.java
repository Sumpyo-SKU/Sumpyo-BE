package com.app.sumpyo_be.domain.talk.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/talks. 건물은 내 건물로 자동. content 길이(앞뒤 공백 제거 후 1~300자)는 서비스에서 검사 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "캠퍼스톡 보내기")
public class TalkCreateRequest {

  @Schema(description = "연락 보낼 층 (1~건물 최고 층)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "연락 보낼 층을 골라주세요.")
  private Integer floor;

  @Schema(description = "강의실 ID (선택, 내 건물·그 층의 방)", example = "7", nullable = true)
  private Long roomId;

  @Schema(description = "특이사항 (1~300자)", example = "북악관 1층에 계신 분 있나요?",
      requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 300)
  private String content;
}
