package com.app.sumpyo_be.global.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "device")
public record DeviceProperties(Map<String, String> keys) {
  public boolean isValid(String roomId, String key) {
    return keys != null && key != null && key.equals(keys.get(roomId));
  }
}