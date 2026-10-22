package com.app.sumpyo_be.domain.prediction.repository;

import com.app.sumpyo_be.domain.prediction.dto.projection.PredictionPoint;
import com.app.sumpyo_be.domain.prediction.entity.Prediction;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

  /**
   * (from, to] 구간의 예측값. 같은 (room, target_at)을 여러 번 예측했으면 created_at 이 가장 늦은 것만.
   * 여러 방을 쿼리 한 번으로 조회한다 (평면도 N+1 방지).
   */
  @Query("""
      select new com.app.sumpyo_be.domain.prediction.dto.projection.PredictionPoint(p.room.id, p.targetAt, p.co2)
      from Prediction p
      where p.room.id in :roomIds
        and p.targetAt > :from and p.targetAt <= :to
        and p.createdAt = (select max(p2.createdAt) from Prediction p2
                           where p2.room = p.room and p2.targetAt = p.targetAt)
      order by p.room.id, p.targetAt
      """)
  List<PredictionPoint> findLatestPoints(@Param("roomIds") Collection<Long> roomIds,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  /** 그래프용 [from, to) 예측. 같은 target_at 이 여러 개면 created_at 최신 것만 */
  @Query("""
      select p from Prediction p
      where p.room.id = :roomId
        and p.targetAt >= :from and p.targetAt < :to
        and p.createdAt = (select max(p2.createdAt) from Prediction p2
                           where p2.room = p.room and p2.targetAt = p.targetAt)
      order by p.targetAt
      """)
  List<Prediction> findLatestForChart(@Param("roomId") Long roomId,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
