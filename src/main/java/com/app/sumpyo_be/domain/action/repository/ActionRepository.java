package com.app.sumpyo_be.domain.action.repository;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionRepository extends JpaRepository<Action, Long> {
  Optional<Action> findTopByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId, Collection<ActionStatus> statuses);
  boolean existsByRoomIdAndStatus(Long roomId, ActionStatus status);

  /** 조치 목록 (전체). 예상 시각(없으면 생성 시각) 오름차순 */
  @Query("""
      select a from Action a join fetch a.room r join fetch r.building
      where a.status in :statuses
      order by coalesce(a.expectedAt, a.createdAt) asc, a.id asc
      """)
  List<Action> findListByStatusIn(@Param("statuses") Collection<ActionStatus> statuses);

  /** 조치 목록 (내 담당 구역: member_zone 의 건물+층과 같은 방만) */
  @Query("""
      select a from Action a join fetch a.room r join fetch r.building
      where a.status in :statuses
        and exists (select 1 from MemberZone z
                    where z.memberId = :memberId and z.building = r.building and z.floor = r.floor)
      order by coalesce(a.expectedAt, a.createdAt) asc, a.id asc
      """)
  List<Action> findListInMemberZones(@Param("memberId") Long memberId,
      @Param("statuses") Collection<ActionStatus> statuses);

  @Query("select distinct a.room.id from Action a where a.room.id in :roomIds and a.status = :status")
  List<Long> findRoomIdsByStatus(@Param("roomIds") Collection<Long> roomIds, @Param("status") ActionStatus status);
}
