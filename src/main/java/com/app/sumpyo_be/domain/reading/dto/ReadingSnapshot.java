package com.app.sumpyo_be.domain.reading.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 방별 최신 측정값 (평면도/카드/상세 공통). 서비스 내부용 JPQL 조회 결과라 API 로는 나가지 않는다. */
@Getter
@AllArgsConstructor
public class ReadingSnapshot {

  private Long roomId;
  private Integer co2;
  private Integer temp;
  private Integer humidity;
  private LocalDateTime measuredAt;
}
