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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionRepository extends JpaRepository<Action, Long> {
  Optional<Action> findTopByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId, Collection<ActionStatus> statuses);
  boolean existsByRoomIdAndStatus(Long roomId, ActionStatus status);

  /**
   * 조치 목록 (전체). 예상 시각(없으면 생성 시각) 오름차순.
   * 대기 중(PENDING·CHECKED) 예측 조치인데 예상 시각이 staleBefore 보다 이전이면 뺀다 (스케줄러가 EXPIRED 로 바꾸기 전에도)
   */
  @Query("""
      select a from Action a join fetch a.room r join fetch r.building
      where a.status in :statuses and a.source in :sources
        and not (a.source = com.app.sumpyo_be.domain.action.entity.ActionSource.PREDICTION
                 and a.status in (com.app.sumpyo_be.domain.action.entity.ActionStatus.PENDING,
                                  com.app.sumpyo_be.domain.action.entity.ActionStatus.CHECKED)
                 and a.expectedAt < :staleBefore)
      order by coalesce(a.expectedAt, a.createdAt) asc, a.id asc
      """)
  List<Action> findList(@Param("statuses") Collection<ActionStatus> statuses,
      @Param("sources") Collection<ActionSource> sources, @Param("staleBefore") LocalDateTime staleBefore);

  /** 조치 목록 (내 담당 구역: member_zone 의 건물+층과 같은 방만). 나머지 조건은 findList 와 같다 */
  @Query("""
      select a from Action a join fetch a.room r join fetch r.building
      where a.status in :statuses and a.source in :sources
        and not (a.source = com.app.sumpyo_be.domain.action.entity.ActionSource.PREDICTION
                 and a.status in (com.app.sumpyo_be.domain.action.entity.ActionStatus.PENDING,
                                  com.app.sumpyo_be.domain.action.entity.ActionStatus.CHECKED)
                 and a.expectedAt < :staleBefore)
        and exists (select 1 from MemberZone z
                    where z.memberId = :memberId and z.building = r.building and z.floor = r.floor)
      order by coalesce(a.expectedAt, a.createdAt) asc, a.id asc
      """)
  List<Action> findListInMemberZones(@Param("memberId") Long memberId,
      @Param("statuses") Collection<ActionStatus> statuses, @Param("sources") Collection<ActionSource> sources,
      @Param("staleBefore") LocalDateTime staleBefore);

  /** 대기 중(PENDING·CHECKED) 예측 조치 중 예상 시각이 before 보다 이전인 것을 EXPIRED 로 */
  @Modifying(clearAutomatically = true)
  @Query("""
      update Action a set a.status = com.app.sumpyo_be.domain.action.entity.ActionStatus.EXPIRED
      where a.source = com.app.sumpyo_be.domain.action.entity.ActionSource.PREDICTION
        and a.status in (com.app.sumpyo_be.domain.action.entity.ActionStatus.PENDING,
                         com.app.sumpyo_be.domain.action.entity.ActionStatus.CHECKED)
        and a.expectedAt < :before
      """)
  int expireStalePredictions(@Param("before") LocalDateTime before);

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

  /** 이 방의 가장 최근 OPEN 조치 (IN_PROGRESS → 환기 중, WAITING → [환기 하기] 에 쓸 추천 조치) */
  Optional<Action> findTopByRoomIdAndRecommendAndStatusInOrderByCreatedAtDescIdDesc(Long roomId, Recommend recommend,
      Collection<ActionStatus> statuses);

  /** 자동 완료 대상: 환기 중(IN_PROGRESS)인 OPEN 조치. 체크리스트 조치(민원 추천)는 단계 완료로 끝나므로 뺀다 */
  @Query("""
      select a from Action a join fetch a.room
      where a.status = com.app.sumpyo_be.domain.action.entity.ActionStatus.IN_PROGRESS
        and a.recommend = com.app.sumpyo_be.domain.action.entity.Recommend.OPEN
        and a.plan is null
      """)
  List<Action> findAutoCompleteCandidates();

  /** 민원에 연결된 가장 최근 조치 */
  Optional<Action> findTopByComplaintIdOrderByCreatedAtDescIdDesc(Long complaintId);

  /** 민원에 연결된 대기 중(PENDING·CHECKED) 조치 */
  Optional<Action> findTopByComplaintIdAndStatusInOrderByCreatedAtDescIdDesc(Long complaintId,
      Collection<ActionStatus> statuses);

  /** 이 방에 창문을 열어 둔(환기 중, 아직 안 닫은) 조치가 있는지 */
  boolean existsByRoomIdAndStatusAndWindowOpenedAtIsNotNullAndWindowClosedAtIsNull(Long roomId, ActionStatus status);

  /** 결과 CO2 를 잴 때가 된 완료 조치 (since 이후 measure_at 만 → 센서가 끊겨 못 재면 결국 포기) */
  @Query("""
      select a from Action a join fetch a.room
      where a.status = com.app.sumpyo_be.domain.action.entity.ActionStatus.DONE
        and a.actualCo2After is null and a.measureAt <= :now and a.measureAt > :since
      """)
  List<Action> findMeasureDue(@Param("now") LocalDateTime now, @Param("since") LocalDateTime since);

  @Query("select distinct a.room.id from Action a where a.room.id in :roomIds and a.status = :status")
  List<Long> findRoomIdsByStatus(@Param("roomIds") Collection<Long> roomIds, @Param("status") ActionStatus status);
}
