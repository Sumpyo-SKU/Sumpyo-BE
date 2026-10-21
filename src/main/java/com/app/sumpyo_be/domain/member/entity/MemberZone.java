package com.app.sumpyo_be.domain.member.entity;

import com.app.sumpyo_be.domain.building.entity.Building;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 근로생 담당 구역 (건물 + 층) */
@Entity
@Table(name = "member_zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberZone {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "building_id", nullable = false)
  private Building building;

  @Column(nullable = false)
  private Integer floor;

  public static MemberZone of(Long memberId, Building building, int floor) {
    MemberZone z = new MemberZone();
    z.memberId = memberId;
    z.building = building;
    z.floor = floor;
    return z;
  }
}
