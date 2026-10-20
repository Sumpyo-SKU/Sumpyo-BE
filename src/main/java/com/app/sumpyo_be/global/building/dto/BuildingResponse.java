package com.app.sumpyo_be.global.building.dto;

import com.app.sumpyo_be.global.building.entity.Building;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/buildings 항목 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "건물")
public class BuildingResponse {

  @Schema(description = "건물 ID", example = "1")
  private Long buildingId;

  @Schema(description = "건물 이름", example = "북악관")
  private String name;

  @Schema(description = "최고 층", example = "7")
  private Integer maxFloor;

  public static BuildingResponse from(Building b) {
    return new BuildingResponse(b.getId(), b.getName(), b.getMaxFloor());
  }
}
