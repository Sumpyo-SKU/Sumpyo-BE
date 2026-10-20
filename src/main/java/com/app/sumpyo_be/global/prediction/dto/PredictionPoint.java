package com.app.sumpyo_be.global.prediction.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 예측 CO2 한 점. 같은 target_at 을 여러 번 예측했으면 가장 최근 예측만 쓴다.
 * 서비스 내부용 JPQL 조회 결과라 API 로는 나가지 않는다.
 */
@Getter
@AllArgsConstructor
public class PredictionPoint {

  private Long roomId;
  private LocalDateTime targetAt;
  private Integer co2;
}
