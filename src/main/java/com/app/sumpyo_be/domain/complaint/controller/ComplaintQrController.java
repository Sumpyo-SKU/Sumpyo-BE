package com.app.sumpyo_be.domain.complaint.controller;

import com.app.sumpyo_be.domain.complaint.dto.ComplaintQrInfoResponse;
import com.app.sumpyo_be.domain.complaint.service.ComplaintQrService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 강의실 민원 QR 발급. 방 선택은 GET /api/buildings, GET /api/buildings/{id}/floors/{floor} 를 쓴다 */
@RestController
@Tag(name = "Complaint QR", description = "강의실 민원 QR 발급 (근로생·관리자)")
@RequestMapping("/api/rooms/{roomId}/qr")
@RequiredArgsConstructor
public class ComplaintQrController {

  private final ComplaintQrService qrService;

  @Operation(summary = "민원 QR 이미지",
      description = "권한 W, A. 512px PNG, qr-{code}.png 로 내려받기. QR 내용은 {FRONT_BASE_URL}/complaint?code={room.code}. "
          + "강의실(LECTURE)만 발급, 아니면 400. 없는 방 404.",
      responses = @ApiResponse(responseCode = "200", description = "PNG 이미지",
          content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE,
              schema = @Schema(type = "string", format = "binary"))))
  @GetMapping
  public ResponseEntity<byte[]> image(@Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId) {
    ComplaintQrService.QrImage qr = qrService.image(roomId);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("qr-" + qr.code() + ".png").build().toString())
        .body(qr.png());
  }

  @Operation(summary = "민원 QR 정보",
      description = "권한 W, A. 화면에 보여줄 강의실 이름과 QR 에 담긴 주소. 강의실(LECTURE)만, 아니면 400. 없는 방 404.")
  @GetMapping("/info")
  public BaseResponse<ComplaintQrInfoResponse> info(
      @Parameter(description = "강의실 ID", example = "7") @PathVariable Long roomId) {
    return BaseResponse.success(qrService.info(roomId));
  }
}
