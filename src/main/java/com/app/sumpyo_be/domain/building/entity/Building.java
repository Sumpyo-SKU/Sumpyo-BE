package com.app.sumpyo_be.domain.building.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "building")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30, unique = true)
  private String name;

  @Column(name = "max_floor")
  private Integer maxFloor;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;   // 건물 선택 화면 순서

  /** 1층 ~ max_floor 안의 층인지. max_floor 가 비어 있으면 1층 이상이면 허용 */
  public boolean hasFloor(int floor) {
    return floor >= 1 && (maxFloor == null || floor <= maxFloor);
  }
}
