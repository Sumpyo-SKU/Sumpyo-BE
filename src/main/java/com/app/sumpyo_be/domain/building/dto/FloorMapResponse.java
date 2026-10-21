package com.app.sumpyo_be.domain.building.dto;

import com.app.sumpyo_be.domain.air.RoomStatus;
import com.app.sumpyo_be.domain.room.entity.MapSide;
import com.app.sumpyo_be.domain.room.entity.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/buildings/{buildingId}/floors/{floor} */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "층 평면도")
public class FloorMapResponse {

  @Schema(description = "건물 이름", example = "북악관")
  private String building;

  @Schema(description = "층", example = "6")
  private int floor;

  @Schema(description = "경보 강의실 수 (OVER + PREDICTED)", example = "2")
  private long alertCount;

  @Schema(description = "강의실 목록. TOP → BOTTOM → 배치 미정 순, 줄 안에서는 order 순 (null 은 맨 뒤)")
  private List<MapRoom> rooms;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "평면도의 강의실 한 칸")
  public static class MapRoom {

    @Schema(description = "강의실 ID", example = "7")
    private Long roomId;

    @Schema(description = "호수", example = "617")
    private String number;

    @Schema(description = "강의실 종류", example = "LECTURE")
    private RoomType type;

    @Schema(description = "평면도 줄 (TOP: 위, BOTTOM: 아래, null: 배치 미정)", example = "TOP")
    private MapSide side;

    @Schema(description = "줄 안에서의 순서 (null 이면 맨 뒤)", example = "3")
    private Integer order;

    @Schema(description = "현재 CO2 (ppm). CO2 센서가 없거나 끊겼으면 null", example = "1050")
    private Integer co2;

    @Schema(description = "상태 (OVER: 기준 초과, PREDICTED: 30분 내 초과 예정, NORMAL: 정상, NO_SENSOR: 센서 없음/끊김)",
        example = "OVER")
    private RoomStatus status;

    @Schema(description = "시뮬레이터 데이터 여부 (sensor_level = SIM)", example = "false")
    private boolean simulated;
  }
}
