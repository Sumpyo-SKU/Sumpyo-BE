package com.app.sumpyo_be.global.building.dto;

import com.app.sumpyo_be.global.building.entity.Building;

/** GET /api/buildings 항목 */
public record BuildingResponse(Long buildingId, String name, Integer maxFloor) {

  public static BuildingResponse from(Building b) {
    return new BuildingResponse(b.getId(), b.getName(), b.getMaxFloor());
  }
}
