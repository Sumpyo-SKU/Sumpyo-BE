package com.app.sumpyo_be.domain.notification.controller;

import com.app.sumpyo_be.domain.notification.dto.NotificationListResponse;
import com.app.sumpyo_be.domain.notification.dto.NotificationReadAllResponse;
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

  @Operation(summary = "알림 목록",
      description = "권한 W. 최근 7일 알림 최신순. 알림은 서버가 1분마다 스캔해서 만들고(지금 환기·곧 환기·센서 끊김), "
          + "민원은 접수 즉시 만든다(새 민원·반복 민원). 받는 사람은 그 방의 건물·층이 담당 구역이고 해당 알림 설정이 켜진 근로생. "
          + "알림을 누르면 actionId → 조치 상세, complaintId → 민원 상세, 둘 다 없으면 roomId → 강의실 상세.")
  @GetMapping
  public BaseResponse<NotificationListResponse> list(
      @Parameter(description = "페이지 (0부터)", example = "0") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "페이지 크기 (1~50)", example = "20")
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return BaseResponse.success(notificationService.list(page, size));
  }

  @Operation(summary = "알림 읽음", description = "권한 W. 본인 알림만 (아니면 403), 없으면 404. 이미 읽었으면 그대로 200.")
  @PatchMapping("/{notificationId}/read")
  public BaseResponse<NotificationListResponse.Item> read(
      @Parameter(description = "알림 ID", example = "31") @PathVariable Long notificationId) {
    return BaseResponse.success(notificationService.read(notificationId));
  }

  @Operation(summary = "모두 읽음", description = "권한 W. 내 안 읽은 알림을 모두 읽음 처리합니다.")
  @PatchMapping("/read-all")
  public BaseResponse<NotificationReadAllResponse> readAll() {
    return BaseResponse.success(notificationService.readAll());
  }
}
