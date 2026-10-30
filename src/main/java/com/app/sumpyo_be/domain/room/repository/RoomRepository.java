package com.app.sumpyo_be.domain.room.repository;

import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.RoomType;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
  Optional<Room> findByCode(String code);

  List<Room> findByBuildingIdAndFloor(Long buildingId, Integer floor);

  List<Room> findBySensorLevel(SensorLevel sensorLevel);

  List<Room> findBySensorLevelAndType(SensorLevel sensorLevel, RoomType type);

  /** 담당 구역(건물+층)의 센서 있는 강의실. 건물 표시 순서 → 층 → 호수 */
  @Query("""
      select r from Room r join fetch r.building b
      where r.type = com.app.sumpyo_be.domain.room.entity.RoomType.LECTURE
        and r.sensorLevel in (com.app.sumpyo_be.domain.room.entity.SensorLevel.FULL,
                              com.app.sumpyo_be.domain.room.entity.SensorLevel.SIM)
        and exists (select 1 from MemberZone z
                    where z.memberId = :memberId and z.building = b and z.floor = r.floor)
      order by b.displayOrder, r.floor, r.number
      """)
  List<Room> findSensorLecturesInMemberZones(@Param("memberId") Long memberId);

  @Query("select r from Room r join fetch r.building where r.sensorLevel in :levels")
  List<Room> findWithBuildingBySensorLevelIn(@Param("levels") Collection<SensorLevel> levels);

  @Query("select r from Room r join fetch r.building where r.id = :id")
  Optional<Room> findWithBuildingById(@Param("id") Long id);

  /** 방 행 잠금 (select ... for update). [환기 하기] 를 연달아 눌러도 같은 방 조치가 두 개 생기지 않게 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id = :id")
  Optional<Room> findByIdForUpdate(@Param("id") Long id);

  @Query("select r from Room r join fetch r.building where r.code = :code")
  Optional<Room> findWithBuildingByCode(@Param("code") String code);

  /** 건물 + 호수로 강의실 찾기. 화장실1 처럼 층마다 같은 이름이 있는 방은 대상이 아니다 (강의실 호수는 건물 안에서 유일) */
  @Query("""
      select r from Room r join fetch r.building b
      where b.id = :buildingId and r.number = :number
        and r.type = com.app.sumpyo_be.domain.room.entity.RoomType.LECTURE
      """)
  Optional<Room> findLectureWithBuildingByBuildingIdAndNumber(@Param("buildingId") Long buildingId,
      @Param("number") String number);
}
