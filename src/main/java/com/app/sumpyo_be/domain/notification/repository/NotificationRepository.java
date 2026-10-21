package com.app.sumpyo_be.domain.notification.repository;

import com.app.sumpyo_be.domain.notification.entity.Notification;
import com.app.sumpyo_be.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /**
   * 같은 (member_id, dedup_key) 가 이미 있으면 넣지 않는다 (스캔이 여러 번 돌아도, 동시에 돌아도 한 번만).
   * UNIQUE 위반 예외를 내지 않으므로 바깥 트랜잭션(민원 접수 등)이 깨지지 않는다.
   * @return 넣었으면 1, 이미 있으면 0
   */
  @Modifying
  @Query(value = """
      insert into notification (member_id, type, room_id, action_id, complaint_id, title, body, dedup_key, created_at)
      values (:memberId, :type, :roomId, :actionId, :complaintId, :title, :body, :dedupKey, :createdAt)
      on conflict (member_id, dedup_key) do nothing
      """, nativeQuery = true)
  int insertIfAbsent(@Param("memberId") Long memberId, @Param("type") String type, @Param("roomId") Long roomId,
      @Param("actionId") Long actionId, @Param("complaintId") Long complaintId, @Param("title") String title,
      @Param("body") String body, @Param("dedupKey") String dedupKey, @Param("createdAt") LocalDateTime createdAt);

  /** 최신순 (since 이후만) */
  Slice<Notification> findByMemberIdAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime since, Pageable pageable);

  long countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime since);

  /** 같은 방 같은 종류 알림을 since 이후 받았는지 (CO2 가 기준 근처에서 오르내릴 때 반복 방지) */
  boolean existsByMemberIdAndRoomIdAndTypeAndCreatedAtGreaterThanEqual(Long memberId, Long roomId,
      NotificationType type, LocalDateTime since);

  @Modifying(clearAutomatically = true)
  @Query("update Notification n set n.readAt = :now where n.memberId = :memberId and n.readAt is null")
  int readAll(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
