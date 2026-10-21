package com.app.sumpyo_be.domain.outdoor.repository;

import com.app.sumpyo_be.domain.outdoor.entity.OutdoorObservation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutdoorObservationRepository extends JpaRepository<OutdoorObservation, Long> {

  Optional<OutdoorObservation> findTopByOrderByObservedAtDesc();

  /** observed_at 이 같으면 갱신. 이번에 못 받은 값(null)은 기존 값을 지우지 않는다 */
  @Transactional
  @Modifying
  @Query(value = """
      insert into outdoor_observation (observed_at, temp, humidity, pm10)
      values (:observedAt, :temp, :humidity, :pm10)
      on conflict (observed_at) do update
      set temp = coalesce(excluded.temp, outdoor_observation.temp),
          humidity = coalesce(excluded.humidity, outdoor_observation.humidity),
          pm10 = coalesce(excluded.pm10, outdoor_observation.pm10)
      """, nativeQuery = true)
  int upsert(@Param("observedAt") LocalDateTime observedAt, @Param("temp") BigDecimal temp,
      @Param("humidity") Integer humidity, @Param("pm10") Integer pm10);
}
