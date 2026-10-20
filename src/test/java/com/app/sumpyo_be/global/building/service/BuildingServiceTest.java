package com.app.sumpyo_be.global.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.global.air.RoomStatus;
import com.app.sumpyo_be.global.air.Thresholds;
import com.app.sumpyo_be.global.building.dto.FloorMapResponse;
import com.app.sumpyo_be.global.building.dto.FloorMapResponse.MapRoom;
import com.app.sumpyo_be.global.building.entity.Building;
import com.app.sumpyo_be.global.building.repository.BuildingRepository;
import com.app.sumpyo_be.global.member.service.ThresholdService;
import com.app.sumpyo_be.global.room.entity.MapSide;
import com.app.sumpyo_be.global.room.entity.Room;
import com.app.sumpyo_be.global.room.entity.SensorLevel;
import com.app.sumpyo_be.global.room.repository.RoomRepository;
import com.app.sumpyo_be.global.room.service.RoomCondition;
import com.app.sumpyo_be.global.room.service.RoomConditionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildingServiceTest {

  private static Room room(long id, String number, int order, SensorLevel level) {
    Room r = mock(Room.class);
    when(r.getId()).thenReturn(id);
    when(r.getNumber()).thenReturn(number);
    when(r.getMapSide()).thenReturn(MapSide.TOP);
    when(r.getMapOrder()).thenReturn(order);
    when(r.getSensorLevel()).thenReturn(level);
    return r;
  }

  @Test
  void simulated는_sensor_level이_SIM인_방만_true() {
    BuildingRepository buildingRepository = mock(BuildingRepository.class);
    RoomRepository roomRepository = mock(RoomRepository.class);
    RoomConditionService conditionService = mock(RoomConditionService.class);
    ThresholdService thresholdService = mock(ThresholdService.class);
    Building building = mock(Building.class);
    when(building.getName()).thenReturn("북악관");
    when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));

    Room sim = room(12, "607", 1, SensorLevel.SIM);
    Room real = room(7, "617", 2, SensorLevel.FULL);
    Room restroom = room(4, "화장실1", 3, SensorLevel.NONE);
    when(roomRepository.findByBuildingIdAndFloor(1L, 6)).thenReturn(List.of(sim, real, restroom));
    when(thresholdService.current()).thenReturn(Thresholds.DEFAULT);
    when(conditionService.evaluate(anyCollection(), any(), any())).thenReturn(Map.of(
        12L, new RoomCondition(null, RoomStatus.PREDICTED, 20),
        7L, new RoomCondition(null, RoomStatus.OVER, null),
        4L, new RoomCondition(null, RoomStatus.NO_SENSOR, null)));

    BuildingService service = new BuildingService(buildingRepository, roomRepository, conditionService,
        thresholdService, Clock.fixed(Instant.parse("2026-10-19T06:00:00Z"), ZoneId.of("Asia/Seoul")));
    FloorMapResponse map = service.getFloorMap(1L, 6);

    assertThat(map.getRooms()).extracting(MapRoom::getNumber, MapRoom::isSimulated)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("607", true),
            org.assertj.core.groups.Tuple.tuple("617", false),
            org.assertj.core.groups.Tuple.tuple("화장실1", false));
    assertThat(map.getAlertCount()).isEqualTo(2);
  }
}
