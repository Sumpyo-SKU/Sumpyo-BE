package com.app.sumpyo_be.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /api/notifications/read-all */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "모두 읽음 처리 결과")
public class NotificationReadAllResponse {

  @Schema(description = "이번에 읽음 처리한 알림 수", example = "2")
  private int readCount;
}
