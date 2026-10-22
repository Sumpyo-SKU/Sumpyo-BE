package com.app.sumpyo_be.domain.complaint.dto;

import com.app.sumpyo_be.domain.action.dto.ComplaintActionResponse;
import com.app.sumpyo_be.domain.air.Co2Level;
import com.app.sumpyo_be.domain.air.ComfortLevel;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/complaints/{complaintId} */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "민원 상세. 하단은 status 에 따라: RECEIVED → AI 추천, IN_PROGRESS → 체크리스트, DONE → CO2 변화·목표 달성")
public class ComplaintDetailResponse {

  @Schema(description = "민원 ID", example = "15")
  private Long complaintId;

  @Schema(description = "강의실 (건물 + 호수)", example = "북악관 618호")
  private String room;

  @Schema(description = "강의실 ID", example = "6")
  private Long roomId;

  @Schema(description = "민원 분류 이름 (보낸 순서)", example = "[\"답답해요\"]")
  private List<String> categoryLabels;

  @Schema(description = "민원 내용 (없으면 null)", example = "5교시만 되면 너무 답답하고 졸려요")
  private String content;

  @Schema(description = "처리 상태 (RECEIVED: 접수, IN_PROGRESS: 처리 중, DONE: 완료)", example = "RECEIVED")
  private ComplaintStatus status;

  @Schema(description = "접수 시각 (KST)", example = "2026-10-21T14:00:00")
  private LocalDateTime createdAt;

  @Schema(description = "접수 시점 측정값과 판정 (조회하는 근로생 기준값). 접수 때 측정이 없었으면 null")
  private Snapshot snapshot;

  @Schema(description = "연결된 가장 최근 조치. 아직 추천 전이면 null")
  private ComplaintActionResponse action;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "접수 시점 측정값")
  public static class Snapshot {

    @Schema(description = "CO2 (ppm)", example = "1250")
    private Integer co2;

    @Schema(description = "CO2 수준 (NORMAL: 정상, CAUTION: 주의 = 기준×0.9 이상, CRITICAL: 심각 = 기준×1.2 이상)",
        example = "CRITICAL")
    private Co2Level co2Level;

    @Schema(description = "기준보다 얼마나 높은지 (ppm, 기준 미만이면 0)", example = "250")
    private Integer co2OverBy;

    @Schema(description = "쾌적도 점수 (0~100)", example = "38")
    private Integer comfortScore;

    @Schema(description = "쾌적도 등급 (GOOD 70↑, NORMAL 41~69, BAD 40↓)", example = "BAD")
    private ComfortLevel comfortLevel;

    @Schema(description = "온도 (°C, 소수 첫째 자리)", example = "24.1")
    private Double temp;

    @Schema(description = "온도 라벨", example = "살짝 더워요")
    private String tempLabel;

    @Schema(description = "습도 (%)", example = "61")
    private Integer humidity;

    @Schema(description = "습도 라벨", example = "조금 습해요")
    private String humidityLabel;
  }
}
