package com.app.sumpyo_be.domain.building.controller;

import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.building.dto.response.BuildingResponse;
import com.app.sumpyo_be.domain.building.dto.response.FloorMapResponse;
import com.app.sumpyo_be.domain.building.service.BuildingService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 캠퍼스 현황 탭 */
@RestController
@Tag(name = "Building", description = "캠퍼스 현황 (건물·층 평면도)")
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
public class BuildingController {

  private final BuildingService buildingService;

  /** 건물 드롭다운 */
  @Operation(summary = "건물 목록", description = "화면 표시 순서(display_order)대로 건물 12개를 반환합니다.")
  @GetMapping
  public BaseResponse<List<BuildingResponse>> buildings() {
    return BaseResponse.success(buildingService.getBuildings());
  }

  /** 층별 평면도. 방이 없는 층이면 rooms 가 빈 배열 */
  @Operation(summary = "층 평면도", description = "층의 강의실별 CO2·상태와 경보 강의실 수를 한 번에 반환합니다. 방이 없는 층이면 rooms 가 빈 배열입니다.")
  @ApiErrors({GlobalErrorCode.BUILDING_NOT_FOUND})
  @GetMapping("/{buildingId}/floors/{floor}")
  public BaseResponse<FloorMapResponse> floorMap(
      @Parameter(description = "건물 ID", example = "1") @PathVariable Long buildingId,
      @Parameter(description = "층", example = "6") @PathVariable int floor) {
    return BaseResponse.success(buildingService.getFloorMap(buildingId, floor));
  }
}
