package com.app.sumpyo_be.domain.member.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.member.dto.SettingsResponse;
import com.app.sumpyo_be.domain.member.dto.SettingsUpdateRequest;
import com.app.sumpyo_be.domain.member.dto.ZoneRequest;
import com.app.sumpyo_be.domain.member.dto.ZoneResponse;
import com.app.sumpyo_be.domain.member.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 담당 구역 설정(온보딩) · 내 설정 */
@RestController
@Tag(name = "Me", description = "내 담당 구역(온보딩) · 내 설정")
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

  private final MeService meService;

  /** 권한 W. 비어 있으면 [] → 온보딩 표시 */
  @Operation(summary = "내 담당 구역 조회", description = "권한 W. 담당 구역이 없으면 [] 이고, 이때 프론트는 온보딩을 띄웁니다.")
  @GetMapping("/zones")
  public BaseResponse<List<ZoneResponse>> zones() {
    return BaseResponse.success(meService.getZones());
  }

  /** 권한 W. 보낸 목록으로 통째로 교체 */
  @Operation(summary = "내 담당 구역 저장", description = "권한 W. 보낸 목록으로 통째로 교체합니다. 없는 건물 404, 층 범위 밖·빈 층 목록·중복 400.")
  @PutMapping("/zones")
  public BaseResponse<List<ZoneResponse>> replaceZones(@RequestBody List<ZoneRequest> zones) {
    return BaseResponse.success("담당 구역을 저장했습니다.", meService.replaceZones(zones));
  }

  /** 권한 W, A */
  @Operation(summary = "내 설정 조회", description = "권한 W, A. cautionFrom/criticalFrom 은 기준값에서 계산한 값입니다.")
  @GetMapping("/settings")
  public BaseResponse<SettingsResponse> settings() {
    return BaseResponse.success(meService.getSettings());
  }

  /** 권한 W, A. 보낸 값만 바뀜 */
  @Operation(summary = "내 설정 변경", description = "권한 W, A. 보낸 값만 바뀝니다. co2Threshold 600~1500, alertLeadMinutes 10·15·20, 최솟값 < 최댓값. "
      + "알림 종류별 on/off: notifyVentilation(끄면 alertEnabled 와 상관없이 환기 알림 없음), notifyComplaint, notifySensorError. "
      + "기본값 되돌리기는 기본값을 그대로 PUT.")
  @PutMapping("/settings")
  public BaseResponse<SettingsResponse> updateSettings(@Valid @RequestBody SettingsUpdateRequest request) {
    return BaseResponse.success("설정을 저장했습니다.", meService.updateSettings(request));
  }
}
