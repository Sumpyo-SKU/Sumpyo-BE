package com.app.sumpyo_be.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PUT /api/me/zones 본문의 항목: { "buildingId": 1, "floors": [5] } (검증은 MeService) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "담당 구역 (건물 하나 + 층 목록)")
public class ZoneRequest {

  @Schema(description = "건물 ID", example = "1")
  private Long buildingId;

  @Schema(description = "담당 층 목록 (1~건물 최고 층, 하나 이상, 중복 불가)", example = "[5]")
  private List<Integer> floors;
}
