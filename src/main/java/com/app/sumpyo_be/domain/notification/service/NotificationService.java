package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.notification.dto.NotificationListResponse;
import com.app.sumpyo_be.domain.notification.dto.NotificationReadAllResponse;
import com.app.sumpyo_be.domain.notification.entity.Notification;
import com.app.sumpyo_be.domain.notification.repository.NotificationRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함 (권한 W). 7일 지난 알림은 조회·안 읽은 수에서 뺀다 (삭제는 안 함) */
@Service
@RequiredArgsConstructor
public class NotificationService {

  static final Duration VISIBLE_FOR = Duration.ofDays(7);

  private final MemberAccess memberAccess;
  private final NotificationRepository notificationRepository;
  private final Clock clock;

  /** 최신순. page 는 0부터 */
  @Transactional(readOnly = true)
  public NotificationListResponse list(int page, int size) {
    Member me = memberAccess.require(Role.WORKER);
    LocalDateTime since = LocalDateTime.now(clock).minus(VISIBLE_FOR);
    Slice<Notification> slice = notificationRepository.findByMemberIdAndCreatedAtGreaterThanEqual(me.getId(), since,
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    return NotificationListResponse.builder()
        .unreadCount(notificationRepository.countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(me.getId(), since))
        .content(slice.getContent().stream().map(NotificationListResponse.Item::from).toList())
        .hasNext(slice.hasNext())
        .build();
  }

  /** 본인 것만 (아니면 403). 이미 읽었으면 그대로 */
  @Transactional
  public NotificationListResponse.Item read(Long notificationId) {
    Member me = memberAccess.require(Role.WORKER);
    Notification n = notificationRepository.findById(notificationId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.NOTIFICATION_NOT_FOUND));
    if (!n.isOwnedBy(me.getId())) {
      throw new CustomException(GlobalErrorCode.NOTIFICATION_ACCESS_DENIED);
    }
    n.read(LocalDateTime.now(clock));
    return NotificationListResponse.Item.from(n);
  }

  @Transactional
  public NotificationReadAllResponse readAll() {
    Member me = memberAccess.require(Role.WORKER);
    return new NotificationReadAllResponse(notificationRepository.readAll(me.getId(), LocalDateTime.now(clock)));
  }
}
