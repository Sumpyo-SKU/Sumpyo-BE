package com.app.sumpyo_be.domain.reading.repository;

import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import com.app.sumpyo_be.domain.room.entity.Room;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
  Optional<SensorReading> findTopByRoomOrderByMeasuredAtDesc(Room room);
  // [from, to) 구간 조회
  List<SensorReading> findByRoomAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
      Room room, LocalDateTime from, LocalDateTime to);

  /**
   * 여러 방의 최신 측정값을 쿼리 한 번으로 조회 (평면도 N+1 방지).
   * 최신 측정이 since 보다 오래된 방은 결과에 없다 → 센서 끊김.
   * idx_sensor_reading_room_measured (room_id, measured_at) 인덱스로 방마다 max 를 찾는다.
   */
  @Query("""
      select new com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot(
          r.room.id, r.co2, r.temp, r.humidity, r.measuredAt)
      from SensorReading r
      where r.room.id in :roomIds
        and r.measuredAt >= :since
        and r.measuredAt = (select max(r2.measuredAt) from SensorReading r2 where r2.room = r.room)
      """)
  List<ReadingSnapshot> findLatestSince(@Param("roomIds") Collection<Long> roomIds,
      @Param("since") LocalDateTime since);
}
