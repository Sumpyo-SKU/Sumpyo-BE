package com.app.sumpyo_be.domain.action.repository;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import com.app.sumpyo_be.domain.room.entity.Room;
import java.time.LocalDateTime;
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

  /** 기준 초과 예상 시각이 (from, to] 안인 조치 (곧 환기 알림) */
  @Query("""
      select a from Action a join fetch a.room r join fetch r.building
      where a.source = :source and a.status = :status and a.expectedAt > :from and a.expectedAt <= :to
      """)
  List<Action> findExpectedBetween(@Param("source") ActionSource source, @Param("status") ActionStatus status,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  /** 창문이 열린 방: 환기 중(IN_PROGRESS)이고 아직 창문을 닫지 않은 조치가 있는 방 */
  @Query("""
      select distinct r from Action a join a.room r
      where a.status = com.app.sumpyo_be.domain.action.entity.ActionStatus.IN_PROGRESS and a.windowClosedAt is null
      """)
  List<Room> findRoomsWithOpenWindow();

  boolean existsByRoomIdAndRecommendAndStatus(Long roomId, Recommend recommend, ActionStatus status);

  @Query("select distinct a.room.id from Action a where a.room.id in :roomIds and a.status = :status")
  List<Long> findRoomIdsByStatus(@Param("roomIds") Collection<Long> roomIds, @Param("status") ActionStatus status);
}
