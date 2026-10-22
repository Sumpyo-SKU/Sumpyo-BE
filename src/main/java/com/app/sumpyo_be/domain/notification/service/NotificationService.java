package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationListResponse;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationReadAllResponse;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.app.sumpyo_be.domain.notification.entity.Notification;
import com.app.sumpyo_be.domain.notification.repository.NotificationRepository;
import com.app.sumpyo_be.global.common.TimeIdCursor;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

  /**
   * 최신순 (created_at DESC, id DESC) 커서 무한 스크롤. size + 1 개를 읽어 다음이 있는지 판단한다.
   * @param cursor 이전 응답의 nextCursor. null 이면 처음부터
   */
  @Transactional(readOnly = true)
  public NotificationListResponse list(String cursor, int size) {
    Member me = memberAccess.require(Role.WORKER);
    TimeIdCursor after = TimeIdCursor.decode(cursor);
    LocalDateTime since = LocalDateTime.now(clock).minus(VISIBLE_FOR);
    PageRequest limit = PageRequest.of(0, size + 1);
    List<Notification> fetched = after == null
        ? notificationRepository.findFirstPage(me.getId(), since, limit)
        : notificationRepository.findPageAfter(me.getId(), since, after.createdAt(), after.id(), limit);
    boolean hasNext = fetched.size() > size;
    List<Notification> page = hasNext ? fetched.subList(0, size) : fetched;
    Notification last = hasNext ? page.getLast() : null;
    return NotificationListResponse.builder()
        .unreadCount(notificationRepository.countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(me.getId(), since))
        .content(page.stream().map(NotificationListResponse.Item::from).toList())
        .nextCursor(last == null ? null : new TimeIdCursor(last.getCreatedAt(), last.getId()).encode())
        .hasNext(hasNext)
        .build();
  }

  /** 알림 종 배지용. 개수 쿼리 하나만 */
  @Transactional(readOnly = true)
  public NotificationUnreadCountResponse unreadCount() {
    Member me = memberAccess.require(Role.WORKER);
    return new NotificationUnreadCountResponse(notificationRepository
        .countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(me.getId(), LocalDateTime.now(clock).minus(VISIBLE_FOR)));
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
