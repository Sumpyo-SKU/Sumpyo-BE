package com.app.sumpyo_be.domain.outdoor.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 바깥 공기 외부 API (구현: DataGoKrOutdoorClient). 실패하면 예외를 던진다. 테스트에서는 mock */
public interface OutdoorApiClient {

  /** 기상청 초단기실황. 그 기준 시각 자료가 아직 없으면 empty */
  Optional<Nowcast> nowcast(LocalDateTime baseAt);

  /** 기상청 초단기예보 (예보 시각별 강수형태). 자료가 없으면 빈 목록 */
  List<ForecastSlot> forecast(LocalDateTime baseAt);

  /** 에어코리아 측정소별 실시간 PM10. 측정값이 없으면("-") empty */
  Optional<Integer> pm10();

  /** @param temp 기온 T1H (℃), @param humidity 습도 REH (%) */
  record Nowcast(BigDecimal temp, Integer humidity) {}

  /** @param pty 강수형태 (0 없음, 1 비, 2 비/눈, 3 눈, 5 빗방울, 6 빗방울눈날림, 7 눈날림) */
  record ForecastSlot(LocalDateTime at, int pty) {
    public boolean isRain() {
      return pty != 0;
    }
  }
}
