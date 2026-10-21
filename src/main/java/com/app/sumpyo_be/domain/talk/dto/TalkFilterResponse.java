package com.app.sumpyo_be.domain.talk.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET/PUT /api/me/talk-filter 응답 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "연락 볼 구역")
public class TalkFilterResponse {

  @Schema(description = "내 건물 ID (담당 구역의 첫 번째 건물)", example = "1")
  private Long buildingId;

  @Schema(description = "내 건물 이름", example = "북악관")
  private String building;

  @ArraySchema(arraySchema = @Schema(description = "층별 필터, 층 오름차순. 빈 배열이면 건물 전체"))
  private List<Floor> floors;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "층 하나")
  public static class Floor {

    @Schema(description = "층", example = "5")
    private int floor;

    @ArraySchema(arraySchema = @Schema(description = "강의실 목록, 호수 순. 빈 배열이면 그 층 전체"))
    private List<Room> rooms;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "강의실")
  public static class Room {

    @Schema(description = "강의실 ID", example = "18")
    private Long roomId;

    @Schema(description = "호수", example = "508")
    private String number;
  }
}
