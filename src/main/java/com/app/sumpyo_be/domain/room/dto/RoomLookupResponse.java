package com.app.sumpyo_be.domain.room.dto;

import com.app.sumpyo_be.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/lookup : 학생 민원 화면의 "위치 확인" */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "호실 찾기 결과")
public class RoomLookupResponse {

  @Schema(description = "강의실 ID (민원 제출 시 roomId 로 보냄)", example = "42")
  private Long roomId;

  @Schema(description = "건물 이름", example = "북악관")
  private String building;

  @Schema(description = "호수 ('호' 없이)", example = "618")
  private String number;

  /** building 은 이미 로딩된 상태여야 한다 */
  public static RoomLookupResponse from(Room r) {
    return new RoomLookupResponse(r.getId(), r.getBuilding().getName(), r.getNumber());
  }
}
