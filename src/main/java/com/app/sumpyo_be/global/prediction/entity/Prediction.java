package com.app.sumpyo_be.global.prediction.entity;

import com.app.sumpyo_be.global.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prediction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prediction {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "target_at", nullable = false)
  private LocalDateTime targetAt;

  private Integer co2;

  @Column(precision = 4, scale = 1)
  private BigDecimal temp;

  private Integer humidity;

  @Column(name = "model_version", length = 20)
  private String modelVersion;
}
