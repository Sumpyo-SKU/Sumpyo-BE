package com.app.sumpyo_be.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 앱 안 알림. 생성은 NotificationRepository.insertIfAbsent (dedup_key 로 멱등) */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  // 누르면 이동할 곳. 조회만 하므로 ID 로만 둔다
  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "action_id")
  private Long actionId;

  @Column(name = "complaint_id")
  private Long complaintId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(length = 200)
  private String body;

  @Column(name = "dedup_key", nullable = false, length = 100)
  private String dedupKey;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public boolean isRead() {
    return readAt != null;
  }

  public boolean isOwnedBy(Long memberId) {
    return this.memberId.equals(memberId);
  }

  /** 이미 읽었으면 그대로 둔다 */
  public void read(LocalDateTime now) {
    if (readAt == null) {
      readAt = now;
    }
  }
}
