package com.app.sumpyo_be.domain.notification.dto;

import com.app.sumpyo_be.domain.notification.entity.Notification;
import com.app.sumpyo_be.domain.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/notifications */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "알림함 (최근 7일, 최신순)")
public class NotificationListResponse {

  @Schema(description = "안 읽은 알림 수 (최근 7일)", example = "2")
  private long unreadCount;

  @Schema(description = "알림 목록")
  private List<Item> content;

  @Schema(description = "다음 페이지가 있는지", example = "false")
  private boolean hasNext;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "알림 한 건. 누르면 actionId 있으면 조치 상세, complaintId 있으면 민원 상세, 없으면 강의실 상세")
  public static class Item {

    @Schema(description = "알림 ID", example = "31")
    private Long notificationId;

    @Schema(description = "종류 (VENTILATION_NOW: 지금 환기, VENTILATION_SOON: 곧 환기, REPEATED_COMPLAINT: 반복 민원, "
        + "NEW_COMPLAINT: 새 민원, SENSOR_ERROR: 센서 끊김). 아이콘 색은 프론트가 종류별로", example = "VENTILATION_NOW")
    private NotificationType type;

    @Schema(description = "제목", example = "317호 환기 필요")
    private String title;

    @Schema(description = "내용", example = "지금 1,180ppm")
    private String body;

    @Schema(description = "강의실 ID", example = "3")
    private Long roomId;

    @Schema(description = "조치 ID (없으면 null)", example = "88")
    private Long actionId;

    @Schema(description = "민원 ID (없으면 null)", example = "null")
    private Long complaintId;

    @Schema(description = "읽었는지", example = "false")
    private boolean read;

    @Schema(description = "생성 시각 (KST). '방금 / 5분 전', '오늘' 묶음은 프론트에서", example = "2026-10-21T14:10:00")
    private LocalDateTime createdAt;

    public static Item from(Notification n) {
      return Item.builder()
          .notificationId(n.getId())
          .type(n.getType())
          .title(n.getTitle())
          .body(n.getBody())
          .roomId(n.getRoomId())
          .actionId(n.getActionId())
          .complaintId(n.getComplaintId())
          .read(n.isRead())
          .createdAt(n.getCreatedAt())
          .build();
    }
  }
}
