package com.app.sumpyo_be.global.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "building_id", nullable = false)
  private Building building;

  @Column(nullable = false, length = 30, unique = true)
  private String code;        // API 경로용 (예: bukak-617)

  @Column(nullable = false, length = 10)
  private String number;

  @Column(nullable = false)
  private Integer floor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private RoomType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "map_side", length = 6)
  private MapSide mapSide;

  @Column(name = "map_order")
  private Integer mapOrder;

  private Integer capacity;

  @Enumerated(EnumType.STRING)
  @Column(name = "sensor_level", nullable = false, length = 5)
  private SensorLevel sensorLevel;
}
