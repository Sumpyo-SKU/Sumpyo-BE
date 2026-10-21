package com.app.sumpyo_be.domain.reading.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 방의 마지막 측정 시각 (센서 끊김 판단용, 내부용) */
@Getter
@AllArgsConstructor
public class LastMeasured {

  private Long roomId;
  private LocalDateTime measuredAt;
}
