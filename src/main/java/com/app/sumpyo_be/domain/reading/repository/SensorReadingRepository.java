package com.app.sumpyo_be.domain.reading.repository;

import com.app.sumpyo_be.domain.reading.dto.LastMeasured;
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

  /** 방마다 마지막 측정 시각 (기간 제한 없음). 측정이 한 번도 없는 방은 결과에 없다 */
  @Query("""
      select new com.app.sumpyo_be.domain.reading.dto.LastMeasured(r.room.id, max(r.measuredAt))
      from SensorReading r
      where r.room.id in :roomIds
      group by r.room.id
      """)
  List<LastMeasured> findLastMeasuredAt(@Param("roomIds") Collection<Long> roomIds);

  /** [from, to] 구간의 가장 늦은 측정 (조치 결과 CO2) */
  Optional<SensorReading> findFirstByRoomIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(Long roomId,
      LocalDateTime from, LocalDateTime to);

  /** [from, to) 날짜별 최고 CO2 (KST 날짜, measured_at 이 KST). 행: [날짜, 최고 CO2] */
  @Query(value = """
      select cast(measured_at as date) as day, max(co2) as max_co2
      from sensor_reading
      where room_id = :roomId and measured_at >= :from and measured_at < :to and co2 is not null
      group by cast(measured_at as date)
      """, nativeQuery = true)
  List<Object[]> findDailyMaxCo2(@Param("roomId") Long roomId, @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  /** CO2 가 기준 미만이었던 마지막 측정 시각. 한 번도 없으면 null */
  @Query("select max(r.measuredAt) from SensorReading r where r.room.id = :roomId and r.co2 < :threshold")
  LocalDateTime findLastBelow(@Param("roomId") Long roomId, @Param("threshold") int threshold);
}
