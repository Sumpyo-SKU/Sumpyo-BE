package com.app.sumpyo_be.global.reading.repository;

import com.app.sumpyo_be.global.reading.entity.SensorReading;
import com.app.sumpyo_be.global.room.entity.Room;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
  Optional<SensorReading> findTopByRoomOrderByMeasuredAtDesc(Room room);
  List<SensorReading> findByRoomAndMeasuredAtAfterOrderByMeasuredAtAsc(Room room, LocalDateTime after);
  // [from, to) 구간 조회
  List<SensorReading> findByRoomAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
      Room room, LocalDateTime from, LocalDateTime to);
}
