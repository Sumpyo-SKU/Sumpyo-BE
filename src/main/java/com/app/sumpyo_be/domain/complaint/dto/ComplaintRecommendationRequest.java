package com.app.sumpyo_be.domain.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/complaints/{complaintId}/recommendation. 본문은 {} 또는 생략 가능 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 추천 요청")
public class ComplaintRecommendationRequest {

  @Schema(description = "[다른 방법] 이면 true → 대기 중인 추천을 이전과 다른 새 추천으로 교체", example = "false")
  private Boolean retry;

  public boolean wantsRetry() {
    return Boolean.TRUE.equals(retry);
  }
}
