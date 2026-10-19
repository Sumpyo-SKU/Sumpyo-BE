package com.app.sumpyo_be.global.building.controller;

import com.app.sumpyo_be.global.building.dto.BuildingResponse;
import com.app.sumpyo_be.global.building.dto.FloorMapResponse;
import com.app.sumpyo_be.global.building.service.BuildingService;
import com.app.sumpyo_be.global.common.BaseResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 캠퍼스 현황 탭 (요청/응답 예시: docs/campus-api.md) */
@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {

  private final BuildingService buildingService;

  /** 건물 드롭다운 */
  @GetMapping
  public BaseResponse<List<BuildingResponse>> buildings() {
    return BaseResponse.success(buildingService.getBuildings());
  }

  /** 층별 평면도. 방이 없는 층이면 rooms 가 빈 배열 */
  @GetMapping("/{buildingId}/floors/{floor}")
  public BaseResponse<FloorMapResponse> floorMap(@PathVariable Long buildingId, @PathVariable int floor) {
    return BaseResponse.success(buildingService.getFloorMap(buildingId, floor));
  }
}
