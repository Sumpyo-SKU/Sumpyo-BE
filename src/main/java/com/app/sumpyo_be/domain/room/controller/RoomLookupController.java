package com.app.sumpyo_be.domain.room.controller;

import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.domain.room.dto.RoomLookupResponse;
import com.app.sumpyo_be.domain.room.service.RoomLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 학생 민원 화면의 "위치 확인". RoomController(/api/rooms/{roomId})와 분리: 고정 경로 lookup 이 우선 매칭된다 */
@RestController
@Tag(name = "Complaint", description = "학생 민원 (QR, 로그인 없음)")
@RequiredArgsConstructor
public class RoomLookupController {

  private final RoomLookupService roomLookupService;

  @Operation(summary = "호실 찾기 (공개)",
      description = "QR 링크는 code 로, 위치 선택 시트는 buildingId + number 로 호출합니다. "
          + "number 는 앞뒤 공백과 뒤의 '호'를 무시합니다(\"618호\" → 618). "
          + "없으면 404 \"호실을 찾을 수 없어요\" (강의실 데이터가 없는 건물도 404).")
  @GetMapping("/api/rooms/lookup")
  public BaseResponse<RoomLookupResponse> lookup(
      @Parameter(description = "강의실 코드 (QR 링크)", example = "bukak-618")
      @RequestParam(required = false) String code,
      @Parameter(description = "건물 ID (위치 선택 시트)", example = "1")
      @RequestParam(required = false) Long buildingId,
      @Parameter(description = "호실 번호 (위치 선택 시트)", example = "618호")
      @RequestParam(required = false) String number) {
    return BaseResponse.success(roomLookupService.lookup(code, buildingId, number));
  }
}
