package com.app.sumpyo_be.global.common;

import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 무한 스크롤 커서 = 마지막 항목의 (생성 시각, ID) 를 "시각|ID" 로 이어 URL-safe Base64 (민원 접수함, 알림함).
 * 다음 페이지는 (시각, ID) < (커서) 복합 조건이라 같은 시각이어도 중복·누락이 없다.
 */
public record TimeIdCursor(LocalDateTime createdAt, Long id) {

  public String encode() {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  /** null·빈 값이면 null(첫 페이지), 형식이 틀리면 400 */
  public static TimeIdCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException();
      }
      return new TimeIdCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
    }
  }
}
