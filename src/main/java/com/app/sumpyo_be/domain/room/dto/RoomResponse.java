package com.app.sumpyo_be.domain.room.dto;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Co2Level;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/{roomId} : 평면도 바텀시트·강의실 상세 공용 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강의실 상세")
public class RoomResponse {

  @Schema(description = "강의실 ID", example = "7")
  private Long roomId;

  @Schema(description = "건물 이름", example = "북악관")
  private String building;

  @Schema(description = "호수", example = "617")
  private String number;

  @Schema(description = "층", example = "6")
  private int floor;

  @Schema(description = "시뮬레이터 데이터 여부 (sensor_level = SIM)", example = "false")
  private boolean simulated;

  @Schema(description = "현재 CO2 (ppm). CO2 센서가 없거나 끊겼으면 null", example = "1050")
  private Integer co2;

  @Schema(description = "현재 온도 (°C)", example = "24")
  private Integer temp;

  @Schema(description = "현재 습도 (%)", example = "45")
  private Integer humidity;

  @Schema(description = AirQualityJudge.COMFORT_SCORE_DESCRIPTION, example = "74")
  private Integer comfortScore;

  @Schema(description = "CO2 수준 (NORMAL: 정상, CAUTION: 주의, CRITICAL: 위험)", example = "CAUTION")
  private Co2Level co2Level;

  @Schema(description = "기준보다 높은 CO2 양 (ppm). 기준 미만이면 0", example = "50")
  private Integer co2OverBy;

  @Schema(description = "온도 상태 문구", example = "적정")
  private String tempLabel;

  @Schema(description = "습도 상태 문구", example = "조금 습해요")
  private String humidityLabel;

  @Schema(description = "환기 진행 중 여부 (IN_PROGRESS 조치가 있으면 true)", example = "false")
  private boolean ventilating;

  @Schema(description = "대기 중인 조치 (PENDING/CHECKED 중 최신). 없으면 null")
  private PendingAction pendingAction;

  @Schema(description = "지금 진행 중인 수업. 없으면 null")
  private CurrentClass currentClass;

  @Schema(description = "오늘 다음 수업. 없으면 null")
  private NextClass nextClass;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "대기 중인 조치")
  public static class PendingAction {

    @Schema(description = "조치 ID", example = "88")
    private Long actionId;

    @Schema(description = "안내 문구", example = "약 20분 뒤 기준 초과 예상")
    private String message;
  }

  // 인원(room.capacity, class_schedule.enrolled)은 화면에서 빠져 내려주지 않는다. 컬럼은 남겨둠.
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "진행 중인 수업")
  public static class CurrentClass {

    @Schema(description = "과목명", example = "자료구조")
    private String courseName;

    @Schema(description = "시작 시각 (HH:mm)", example = "13:30")
    private String start;

    @Schema(description = "종료 시각 (HH:mm)", example = "14:45")
    private String end;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "다음 수업")
  public static class NextClass {

    @Schema(description = "과목명", example = "운영체제")
    private String courseName;

    @Schema(description = "시작 시각 (HH:mm)", example = "15:00")
    private String start;

    @Schema(description = "종료 시각 (HH:mm)", example = "16:15")
    private String end;

    @Schema(description = "쉬는 시간 팁. 환기가 필요할 때(OVER/PREDICTED)만, 아니면 null", example = "쉬는 시간에 창문 환기 추천")
    private String tip;
  }
}
