package com.app.sumpyo_be.global.member.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.member.dto.SettingsResponse;
import com.app.sumpyo_be.global.member.dto.SettingsUpdateRequest;
import com.app.sumpyo_be.global.member.dto.ZoneRequest;
import com.app.sumpyo_be.global.member.dto.ZoneResponse;
import com.app.sumpyo_be.global.member.service.MeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 담당 구역 설정(온보딩) · 내 설정 (요청/응답 예시: docs/me-api.md) */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

  private final MeService meService;

  /** 권한 W. 비어 있으면 [] → 온보딩 표시 */
  @GetMapping("/zones")
  public BaseResponse<List<ZoneResponse>> zones() {
    return BaseResponse.success(meService.getZones());
  }

  /** 권한 W. 보낸 목록으로 통째로 교체 */
  @PutMapping("/zones")
  public BaseResponse<List<ZoneResponse>> replaceZones(@RequestBody List<ZoneRequest> zones) {
    return BaseResponse.success("담당 구역을 저장했습니다.", meService.replaceZones(zones));
  }

  /** 권한 W, A */
  @GetMapping("/settings")
  public BaseResponse<SettingsResponse> settings() {
    return BaseResponse.success(meService.getSettings());
  }

  /** 권한 W, A. 보낸 값만 바뀜 */
  @PutMapping("/settings")
  public BaseResponse<SettingsResponse> updateSettings(@Valid @RequestBody SettingsUpdateRequest request) {
    return BaseResponse.success("설정을 저장했습니다.", meService.updateSettings(request));
  }
}
