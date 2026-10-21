package com.app.sumpyo_be.domain.outdoor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 바깥 공기 수집 설정 (공공데이터포털).
 * @param apiKey  DATA_GO_KR_KEY (일반 인증키 Decoding). 비어 있으면 수집하지 않는다
 * @param nx      WEATHER_NX 기상청 격자 X
 * @param ny      WEATHER_NY 기상청 격자 Y
 * @param station AIR_STATION_NAME 에어코리아 측정소 이름
 */
@ConfigurationProperties(prefix = "sumpyo.outdoor")
public record OutdoorProperties(String apiKey, String nx, String ny, String station) {

  public boolean isReady() {
    return notBlank(apiKey) && notBlank(nx) && notBlank(ny) && notBlank(station);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
