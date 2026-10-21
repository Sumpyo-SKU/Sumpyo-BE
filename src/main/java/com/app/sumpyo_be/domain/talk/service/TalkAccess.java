package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.repository.MemberZoneRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 캠퍼스톡 공통 규칙: 소통 범위 = 내 건물 (담당 구역의 첫 번째 건물) */
@Component
@RequiredArgsConstructor
public class TalkAccess {

  private final MemberZoneRepository zoneRepository;
  private final RoomRepository roomRepository;

  /** 담당 구역(건물 표시 순서 → 층)의 첫 번째 건물. 담당 구역이 없으면 400 */
  public Building myBuilding(Member me) {
    return zoneRepository.findWithBuildingByMemberId(me.getId()).stream()
        .findFirst()
        .map(z -> z.getBuilding())
        .orElseThrow(() -> new CustomException(GlobalErrorCode.TALK_NO_ZONE));
  }

  public void requireFloor(Building building, Integer floor) {
    if (floor == null || !building.hasFloor(floor)) {
      throw new CustomException(GlobalErrorCode.INVALID_TALK_FLOOR);
    }
  }

  /** 내 건물·그 층의 방. 아니면 400 */
  public Room requireRoomOnFloor(Building building, int floor, Long roomId) {
    Room room = roomRepository.findWithBuildingById(roomId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.INVALID_TALK_ROOM));
    if (!isOnFloor(room, building, floor)) {
      throw new CustomException(GlobalErrorCode.INVALID_TALK_ROOM);
    }
    return room;
  }

  static boolean isOnFloor(Room room, Building building, int floor) {
    return room.getBuilding().getId().equals(building.getId()) && room.getFloor() == floor;
  }
}
