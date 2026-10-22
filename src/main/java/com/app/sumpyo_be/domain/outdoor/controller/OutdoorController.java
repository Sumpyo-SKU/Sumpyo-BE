package com.app.sumpyo_be.domain.outdoor.controller;

import com.app.sumpyo_be.global.common.ScreenTag;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.outdoor.dto.response.OutdoorLatestResponse;
import com.app.sumpyo_be.domain.outdoor.service.OutdoorService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Outdoor", description = "바깥 공기 (홈 [지금] 탭)")
@RequestMapping("/api/outdoor")
@RequiredArgsConstructor
public class OutdoorController {

  private final OutdoorService outdoorService;

  @Operation(summary = "바깥 공기 최신값",
      description = "권한 W, A. 매시 10분·40분에 공공데이터포털(기상청 초단기실황 기온·습도, 에어코리아 PM10)에서 모은 값 중 가장 최근. "
          + "아직 모은 값이 없으면 data: null.")
  @ApiErrors(auth = true)
  @Tag(name = ScreenTag.HOME)
  @GetMapping("/latest")
  public BaseResponse<OutdoorLatestResponse> latest() {
    return BaseResponse.success(outdoorService.latest());
  }
}
