package com.app.sumpyo_be.domain.complaint.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 익명 신고자 식별 해시. client_hash = SHA-256(clientId + salt) 16진수 64자.
 * 원래 clientId 는 어디에도 저장하지 않는다.
 * salt(COMPLAINT_SALT)가 없으면 앱은 정상으로 뜨고 민원 접수만 503 으로 막는다.
 */
@Slf4j
@Component
public class ClientHasher {

  private final String salt;

  public ClientHasher(@Value("${sumpyo.complaint.salt:}") String salt) {
    this.salt = salt;
    if (!isReady()) {
      log.warn("COMPLAINT_SALT 가 없어 학생 민원 접수(POST /api/complaints)가 503 을 돌려줍니다.");
    }
  }

  public boolean isReady() {
    return salt != null && !salt.isBlank();
  }

  public String hash(String clientId) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((clientId + salt).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);   // 모든 JRE 에 필수로 들어 있음
    }
  }
}
