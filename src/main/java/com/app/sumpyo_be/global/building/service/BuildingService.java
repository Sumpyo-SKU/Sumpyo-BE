package com.app.sumpyo_be.global.building.service;

import com.app.sumpyo_be.global.air.AirQualityJudge;
import com.app.sumpyo_be.global.air.Thresholds;
import com.app.sumpyo_be.global.building.dto.BuildingResponse;
import com.app.sumpyo_be.global.building.dto.FloorMapResponse;
import com.app.sumpyo_be.global.building.dto.FloorMapResponse.MapRoom;
import com.app.sumpyo_be.global.building.repository.BuildingRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.member.service.ThresholdService;
import com.app.sumpyo_be.global.building.entity.Building;
import com.app.sumpyo_be.global.room.entity.MapSide;
import com.app.sumpyo_be.global.room.entity.Room;
import com.app.sumpyo_be.global.room.repository.RoomRepository;
import com.app.sumpyo_be.global.room.service.RoomCondition;
import com.app.sumpyo_be.global.room.service.RoomConditionService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

  /** 평면도 칸 순서: TOP 줄 → BOTTOM 줄 → 배치 미정(null), 줄 안에서는 map_order → 호수 */
  private static final Comparator<Room> MAP_ORDER = Comparator
      .comparing(Room::getMapSide, Comparator.nullsLast(Comparator.<MapSide>naturalOrder()))
      .thenComparing(Room::getMapOrder, Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(Room::getNumber);

  private final BuildingRepository buildingRepository;
  private final RoomRepository roomRepository;
  private final RoomConditionService roomConditionService;
  private final ThresholdService thresholdService;
  private final Clock clock;

  public List<BuildingResponse> getBuildings() {
    return buildingRepository.findAll(Sort.by("displayOrder")).stream().map(BuildingResponse::from).toList();
  }

  /** 쿼리 4번 고정: 건물, 층의 방들, 방별 최신 측정값, 방별 30분 예측 */
  public FloorMapResponse getFloorMap(Long buildingId, int floor) {
    Building building = buildingRepository.findById(buildingId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.BUILDING_NOT_FOUND));
    List<Room> rooms = roomRepository.findByBuildingIdAndFloor(buildingId, floor).stream()
        .sorted(MAP_ORDER)
        .toList();

    Thresholds thresholds = thresholdService.current();
    Map<Long, RoomCondition> conditions =
        roomConditionService.evaluate(rooms, thresholds, LocalDateTime.now(clock));

    List<MapRoom> mapRooms = rooms.stream()
        .map(r -> {
          RoomCondition c = conditions.get(r.getId());
          return new MapRoom(r.getId(), r.getNumber(), r.getType(),
              r.getMapSide(), r.getMapOrder(), c.co2(), c.status(), r.getSensorLevel().isSimulated());
        })
        .toList();
    long alertCount = mapRooms.stream().filter(r -> AirQualityJudge.isAlert(r.getStatus())).count();
    return new FloorMapResponse(building.getName(), floor, alertCount, mapRooms);
  }
}
