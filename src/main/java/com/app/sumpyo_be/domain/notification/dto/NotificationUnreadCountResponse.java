package com.app.sumpyo_be.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/notifications/unread-count : 알림 종 배지 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "안 읽은 알림 수")
public class NotificationUnreadCountResponse {

  @Schema(description = "안 읽은 알림 수 (최근 7일)", example = "2")
  private long unreadCount;
}
