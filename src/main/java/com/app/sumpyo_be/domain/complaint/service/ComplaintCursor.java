package com.app.sumpyo_be.domain.complaint.service;

import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/** 접수함 무한 스크롤 커서 = 마지막 항목의 (createdAt, complaintId) 를 "시각|ID" 로 이어 URL-safe Base64 */
public record ComplaintCursor(LocalDateTime createdAt, Long id) {

  public String encode() {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  /** null·빈 값이면 null(첫 페이지), 형식이 틀리면 400 */
  public static ComplaintCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException();
      }
      return new ComplaintCursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw new CustomException(GlobalErrorCode.INVALID_COMPLAINT_CURSOR);
    }
  }
}
