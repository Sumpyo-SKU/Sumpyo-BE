package com.app.sumpyo_be.global.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 센서 기기 설정.
 * keys    : room code → 기기 키 (X-Device-Key)
 * aliases : 예전 경로 값 → room code
 *
 * TODO: ESP32 펌웨어를 /api/rooms/bukak-617/readings 로 다시 올리면
 *       aliases 와 "517" 키를 설정에서 제거할 것.
 */
@ConfigurationProperties(prefix = "device")
public record DeviceProperties(Map<String, String> keys, Map<String, String> aliases) {

  public DeviceProperties {
    keys = keys == null ? Map.of() : keys;
    aliases = aliases == null ? Map.of() : aliases;
  }

  /** 경로 값이 별칭이면 실제 room code 로, 아니면 그대로 */
  public String resolveCode(String pathValue) {
    return aliases.getOrDefault(pathValue, pathValue);
  }

  public boolean isValid(String code, String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    if (key.equals(keys.get(code))) {
      return true;
    }
    // 별칭(예: 517)으로 등록된 예전 키도 같은 room 의 키로 인정
    return aliases.entrySet().stream()
        .anyMatch(e -> e.getValue().equals(code) && key.equals(keys.get(e.getKey())));
  }
}
