package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.RoomType;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.talk.dto.TalkFilterRequest;
import com.app.sumpyo_be.domain.talk.dto.TalkFilterResponse;
import com.app.sumpyo_be.domain.talk.entity.MemberTalkFilter;
import com.app.sumpyo_be.domain.talk.repository.MemberTalkFilterRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET·PUT /api/me/talk-filter : 캠퍼스톡 연락 볼 구역 (여러 개) */
@Service
@RequiredArgsConstructor
@Transactional
public class TalkFilterService {

  private final MemberAccess memberAccess;
  private final TalkAccess talkAccess;
  private final MemberTalkFilterRepository filterRepository;
  private final RoomRepository roomRepository;

  @Transactional(readOnly = true)
  public TalkFilterResponse get() {
    Member me = memberAccess.require(Role.WORKER);
    Building building = talkAccess.myBuilding(me);
    return toResponse(building, effectiveFilter(me, building));
  }

  /** 보낸 내용으로 통째로 교체. 검증에 실패하면 기존 필터는 그대로 */
  public TalkFilterResponse replace(TalkFilterRequest req) {
    Member me = memberAccess.require(Role.WORKER);
    Building building = talkAccess.myBuilding(me);
    List<TalkFilterRequest.Floor> floors = req.getFloors() == null ? List.of() : req.getFloors();

    Set<Integer> seenFloors = new HashSet<>();
    Set<Long> allRoomIds = new HashSet<>();
    for (TalkFilterRequest.Floor f : floors) {
      talkAccess.requireFloor(building, f.getFloor());
      if (!seenFloors.add(f.getFloor())) {
        throw new CustomException(GlobalErrorCode.DUPLICATED_TALK_FLOOR);
      }
      if (f.getRoomIds() != null) {
        allRoomIds.addAll(f.getRoomIds());
      }
    }
    Map<Long, Room> rooms = roomRepository.findAllById(allRoomIds).stream()
        .collect(Collectors.toMap(Room::getId, Function.identity()));

    List<MemberTalkFilter> rows = new ArrayList<>();
    for (TalkFilterRequest.Floor f : floors) {
      Set<Long> roomIds = f.getRoomIds() == null ? Set.of() : new LinkedHashSet<>(f.getRoomIds());
      if (roomIds.isEmpty()) {
        rows.add(MemberTalkFilter.of(me.getId(), f.getFloor(), null));
        continue;
      }
      for (Long roomId : roomIds) {
        Room room = rooms.get(roomId);
        if (room == null || room.getType() != RoomType.LECTURE
            || !TalkAccess.isOnFloor(room, building, f.getFloor())) {
          throw new CustomException(GlobalErrorCode.INVALID_TALK_ROOM);
        }
        rows.add(MemberTalkFilter.of(me.getId(), f.getFloor(), room));
      }
    }

    filterRepository.deleteByMemberId(me.getId());
    filterRepository.saveAll(rows);
    return toResponse(building, effectiveFilter(me, building));
  }

  /**
   * 목록·응답에 쓰는 필터 (층 → 강의실, 둘 다 정렬). 비면 건물 전체, 강의실이 비면 그 층 전체.
   * 담당 구역을 다른 건물로 바꿔 지금 건물에 안 맞게 된 줄은 무시한다
   * (그 층의 강의실 줄이 전부 안 맞으면 그 층도 뺀다 → 층 전체로 넓어지지 않게).
   */
  @Transactional(readOnly = true)
  public Map<Integer, List<Room>> effectiveFilter(Member me, Building building) {
    Map<Integer, List<Room>> result = new TreeMap<>();
    Set<Integer> wholeFloors = new HashSet<>();
    for (MemberTalkFilter row : filterRepository.findWithRoomByMemberId(me.getId())) {
      if (!building.hasFloor(row.getFloor())) {
        continue;
      }
      if (row.getRoom() == null) {
        wholeFloors.add(row.getFloor());
        result.put(row.getFloor(), new ArrayList<>());
      } else if (TalkAccess.isOnFloor(row.getRoom(), building, row.getFloor()) && !wholeFloors.contains(row.getFloor())) {
        result.computeIfAbsent(row.getFloor(), k -> new ArrayList<>()).add(row.getRoom());
      }
    }
    result.values().forEach(list -> list.sort(Comparator.comparing(Room::getNumber)));
    return result;
  }

  public static List<TalkFilterResponse.Floor> toFloors(Map<Integer, List<Room>> filter) {
    return filter.entrySet().stream()
        .map(e -> new TalkFilterResponse.Floor(e.getKey(), e.getValue().stream()
            .map(r -> new TalkFilterResponse.Room(r.getId(), r.getNumber()))
            .toList()))
        .toList();
  }

  private static TalkFilterResponse toResponse(Building building, Map<Integer, List<Room>> filter) {
    return TalkFilterResponse.builder()
        .buildingId(building.getId())
        .building(building.getName())
        .floors(toFloors(filter))
        .build();
  }
}
