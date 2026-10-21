package com.app.sumpyo_be.domain.room.repository;

import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {
  Optional<Room> findByCode(String code);

  List<Room> findByBuildingIdAndFloor(Long buildingId, Integer floor);

  List<Room> findBySensorLevel(SensorLevel sensorLevel);

  @Query("select r from Room r join fetch r.building where r.sensorLevel in :levels")
  List<Room> findWithBuildingBySensorLevelIn(@Param("levels") Collection<SensorLevel> levels);

  @Query("select r from Room r join fetch r.building where r.id = :id")
  Optional<Room> findWithBuildingById(@Param("id") Long id);

  @Query("select r from Room r join fetch r.building where r.code = :code")
  Optional<Room> findWithBuildingByCode(@Param("code") String code);

  @Query("select r from Room r join fetch r.building b where b.id = :buildingId and r.number = :number")
  Optional<Room> findWithBuildingByBuildingIdAndNumber(@Param("buildingId") Long buildingId,
      @Param("number") String number);
}
