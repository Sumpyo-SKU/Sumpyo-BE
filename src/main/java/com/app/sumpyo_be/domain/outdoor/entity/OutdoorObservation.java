package com.app.sumpyo_be.domain.outdoor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 바깥 공기 관측 (기상청 초단기실황 + 에어코리아). 저장은 OutdoorObservationRepository.upsert */
@Entity
@Table(name = "outdoor_observation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutdoorObservation {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "observed_at", nullable = false, unique = true)
  private LocalDateTime observedAt;

  @Column(precision = 4, scale = 1)
  private BigDecimal temp;

  private Integer humidity;

  private Integer pm10;
}
