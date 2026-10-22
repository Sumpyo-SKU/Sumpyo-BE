package com.app.sumpyo_be.domain.member.dto.response;

import com.app.sumpyo_be.domain.room.entity.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/me/rooms : 홈 상단 방 선택 드롭다운 항목 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "내 담당 구역의 센서 있는 강의실")
public class MyRoomResponse {

  @Schema(description = "강의실 ID", example = "7")
  private Long roomId;

  @Schema(description = "건물 ID", example = "1")
  private Long buildingId;

  @Schema(description = "드롭다운 표시 이름 (건물 · 호수)", example = "북악관 · 617호")
  private String label;

  @Schema(description = "시뮬레이터 값인지 (sensor_level SIM)", example = "false")
  private boolean simulated;

  /** building 은 이미 로딩된 상태여야 한다 */
  public static MyRoomResponse from(Room r) {
    return new MyRoomResponse(r.getId(), r.getBuilding().getId(),
        r.getBuilding().getName() + " · " + r.getNumber() + "호", r.getSensorLevel().isSimulated());
  }
}
