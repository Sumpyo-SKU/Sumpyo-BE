package com.app.sumpyo_be.global.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/me/zones 항목. 건물 표시 순서 → 층 오름차순 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "내 담당 구역")
public class ZoneResponse {

  @Schema(description = "건물 ID", example = "1")
  private Long buildingId;

  @Schema(description = "건물 이름", example = "북악관")
  private String buildingName;

  @Schema(description = "담당 층 목록 (오름차순)", example = "[5]")
  private List<Integer> floors;
}
