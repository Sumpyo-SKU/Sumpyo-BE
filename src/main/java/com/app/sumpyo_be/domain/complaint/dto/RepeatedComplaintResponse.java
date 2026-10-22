package com.app.sumpyo_be.domain.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/complaints/repeated : "이번주 반복 민원" 카드 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "이번 주 반복 민원 방")
public class RepeatedComplaintResponse {

  @Schema(description = "강의실 ID ([관련 민원 확인] → GET /api/complaints?roomId=)", example = "6")
  private Long roomId;

  @Schema(description = "강의실 (건물 + 호수)", example = "북악관 618호")
  private String room;

  @Schema(description = "이번 주(월~일) 민원 수", example = "7")
  private long count;

  @Schema(description = "이번 주 중 그 방 최고 CO2 가 기준 이상이었던 날 수. CO2 센서가 없는 방은 null", example = "5")
  private Integer overThresholdDays;

  @Schema(description = "카드 문구 (지난 평일 모두 초과면 '센서도 매일 …', 일부면 '센서도 N일 …', 센서 없음·초과 없음이면 건수만)",
      example = "이번 주 7건 · 센서도 매일 1,000ppm 초과")
  private String message;
}
