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
import java.util.List;
import java.util.stream.Collectors;
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

  /**
   * 근로 구역 표시: "북악관 · 6층", 여러 층이면 "북악관 · 5·6층". 여러 건물이면 첫 건물만 (내 정보, 캠퍼스톡 글쓴이).
   * @param zones 건물 표시 순서 → 층 으로 정렬돼 있고 building 이 로딩된 상태. 비면 null
   */
  public static String label(List<MemberZone> zones) {
    if (zones.isEmpty()) {
      return null;
    }
    Building first = zones.getFirst().getBuilding();
    String floors = zones.stream().filter(z -> z.getBuilding().getId().equals(first.getId()))
        .map(z -> String.valueOf(z.getFloor())).collect(Collectors.joining("·"));
    return first.getName() + " · " + floors + "층";
  }

  /**
   * 한 건물 안의 담당 층 표시: "6층", 여러 층이면 "5·6층" (캠퍼스톡 답변 작성자 "김근로 · 6층").
   * @param zones 한 회원의 구역, 층 순. 그 건물 구역이 없으면 null
   */
  public static String floorLabel(List<MemberZone> zones, Long buildingId) {
    List<String> floors = zones.stream().filter(z -> z.getBuilding().getId().equals(buildingId))
        .map(z -> String.valueOf(z.getFloor())).toList();
    return floors.isEmpty() ? null : String.join("·", floors) + "층";
  }

  public static MemberZone of(Long memberId, Building building, int floor) {
    MemberZone z = new MemberZone();
    z.memberId = memberId;
    z.building = building;
    z.floor = floor;
    return z;
  }
}
