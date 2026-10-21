package com.app.sumpyo_be.domain.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/{roomId}/qr/info */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "민원 QR 정보")
public class ComplaintQrInfoResponse {

  @Schema(description = "강의실 ID", example = "7")
  private Long roomId;

  @Schema(description = "강의실 이름 (건물 + 호수)", example = "북악관 617호")
  private String room;

  @Schema(description = "QR 에 담긴 주소 (학생 민원 화면)", example = "https://sumpyo.app/complaint?code=bukak-617")
  private String url;
}
