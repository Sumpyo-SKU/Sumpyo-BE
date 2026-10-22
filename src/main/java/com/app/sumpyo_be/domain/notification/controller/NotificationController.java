package com.app.sumpyo_be.domain.notification.controller;

import com.app.sumpyo_be.global.common.ScreenTag;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationListResponse;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationReadAllResponse;
import com.app.sumpyo_be.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.app.sumpyo_be.domain.notification.service.NotificationService;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 앱 안 알림함 (휴대폰 푸시 아님) */
@RestController
@Tag(name = "Notification", description = "환기 알림함 (근로생)")
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @Operation(summary = "알림 목록 (무한 스크롤)",
      description = "권한 W. 최근 7일 알림 최신순, 커서 무한 스크롤 (응답의 nextCursor 를 다음 요청의 cursor 로). 알림은 서버가 1분마다 스캔해서 만들고(지금 환기·곧 환기·센서 끊김), "
          + "민원은 접수 즉시 만든다(새 민원·반복 민원). 받는 사람은 그 방의 건물·층이 담당 구역이고 해당 알림 설정이 켜진 근로생. "
          + "알림을 누르면 actionId → 조치 상세, complaintId → 민원 상세, 둘 다 없으면 roomId → 강의실 상세.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_CURSOR}, auth = true, validation = true)
  @Tag(name = ScreenTag.NOTIFICATION)
  @GetMapping
  public BaseResponse<NotificationListResponse> list(
      @Parameter(description = "이전 응답의 nextCursor (비우면 처음부터)") @RequestParam(required = false) String cursor,
      @Parameter(description = "한 번에 가져올 개수 (1~50)", example = "20")
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return BaseResponse.success(notificationService.list(cursor, size));
  }

  @Operation(summary = "안 읽은 알림 수", description = "권한 W. 알림 종 배지용 (최근 7일 안 읽은 알림 수만).")
  @ApiErrors(auth = true)
  @Tag(name = ScreenTag.HOME)
  @Tag(name = ScreenTag.NOTIFICATION)
  @GetMapping("/unread-count")
  public BaseResponse<NotificationUnreadCountResponse> unreadCount() {
    return BaseResponse.success(notificationService.unreadCount());
  }

  @Operation(summary = "알림 읽음", description = "권한 W. 본인 알림만 (아니면 403), 없으면 404. 이미 읽었으면 그대로 200.")
  @ApiErrors(value = {GlobalErrorCode.NOTIFICATION_ACCESS_DENIED, GlobalErrorCode.NOTIFICATION_NOT_FOUND}, auth = true)
  @Tag(name = ScreenTag.NOTIFICATION)
  @PatchMapping("/{notificationId}/read")
  public BaseResponse<NotificationListResponse.Item> read(
      @Parameter(description = "알림 ID", example = "31") @PathVariable Long notificationId) {
    return BaseResponse.success(notificationService.read(notificationId));
  }

  @Operation(summary = "모두 읽음", description = "권한 W. 내 안 읽은 알림을 모두 읽음 처리합니다.")
  @ApiErrors(auth = true)
  @Tag(name = ScreenTag.NOTIFICATION)
  @PatchMapping("/read-all")
  public BaseResponse<NotificationReadAllResponse> readAll() {
    return BaseResponse.success(notificationService.readAll());
  }
}
