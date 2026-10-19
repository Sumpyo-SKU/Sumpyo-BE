package com.app.sumpyo_be.global.reading.dto;

import java.time.LocalDateTime;

/** 방별 최신 측정값 (평면도/카드/상세 공통) */
public record ReadingSnapshot(Long roomId, Integer co2, Integer temp, Integer humidity,
                              LocalDateTime measuredAt) {}
