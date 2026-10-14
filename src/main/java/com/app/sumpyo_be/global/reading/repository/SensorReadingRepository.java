package com.app.sumpyo_be.global.reading.repository;

import com.app.sumpyo_be.global.reading.entity.SensorReading;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
  Optional<SensorReading> findTopByRoomIdOrderByMeasuredAtDesc(String roomId);
  List<SensorReading> findByRoomIdAndMeasuredAtAfterOrderByMeasuredAtAsc(String roomId, LocalDateTime after);
  // [from, to) 구간 조회
  List<SensorReading> findByRoomIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
      String roomId, LocalDateTime from, LocalDateTime to);
}