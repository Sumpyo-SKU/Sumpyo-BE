package com.app.sumpyo_be.domain.member.service;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.domain.building.repository.BuildingRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.dto.SettingsResponse;
import com.app.sumpyo_be.domain.member.dto.SettingsUpdateRequest;
import com.app.sumpyo_be.domain.member.dto.ZoneRequest;
import com.app.sumpyo_be.domain.member.dto.ZoneResponse;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.MemberZone;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.repository.MemberZoneRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 담당 구역 · 내 설정 (/api/me/*) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

  static final Set<Integer> ALERT_LEAD_MINUTES = Set.of(10, 15, 20);

  private final MemberAccess memberAccess;
  private final MemberZoneRepository zoneRepository;
  private final BuildingRepository buildingRepository;

  // ---------------- 담당 구역 (권한 W) ----------------

  /** 비어 있으면 빈 배열 → 프론트는 이걸로 온보딩 표시 여부를 판단 */
  public List<ZoneResponse> getZones() {
    Member me = memberAccess.require(Role.WORKER);
    return toResponses(zoneRepository.findWithBuildingByMemberId(me.getId()));
  }

  /** 보낸 목록으로 통째로 교체 (기존 삭제 후 INSERT, 한 트랜잭션). 빈 배열이면 전부 삭제 */
  @Transactional
  public List<ZoneResponse> replaceZones(List<ZoneRequest> request) {
    Member me = memberAccess.require(Role.WORKER);
    if (request == null || request.stream().anyMatch(Objects::isNull)) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }
    Map<Long, Building> buildings = buildingRepository.findAllById(request.stream()
            .map(ZoneRequest::getBuildingId).filter(Objects::nonNull).toList()).stream()
        .collect(Collectors.toMap(Building::getId, Function.identity()));

    List<MemberZone> zones = new ArrayList<>();
    Set<Long> seenBuildings = new HashSet<>();
    for (ZoneRequest z : request) {
      if (z.getBuildingId() == null) {
        throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
      }
      if (!seenBuildings.add(z.getBuildingId())) {
        throw new CustomException(GlobalErrorCode.DUPLICATED_ZONE);
      }
      Building building = buildings.get(z.getBuildingId());
      if (building == null) {
        throw new CustomException(GlobalErrorCode.BUILDING_NOT_FOUND);
      }
      if (z.getFloors() == null || z.getFloors().isEmpty()
          || z.getFloors().stream().anyMatch(f -> f == null || !building.hasFloor(f))) {
        throw new CustomException(GlobalErrorCode.INVALID_ZONE_FLOOR);
      }
      if (new HashSet<>(z.getFloors()).size() != z.getFloors().size()) {
        throw new CustomException(GlobalErrorCode.DUPLICATED_ZONE);
      }
      z.getFloors().forEach(f -> zones.add(MemberZone.of(me.getId(), building, f)));
    }

    zoneRepository.deleteByMemberId(me.getId());
    zoneRepository.saveAll(zones);
    return toResponses(zones);
  }

  private static List<ZoneResponse> toResponses(List<MemberZone> zones) {
    Map<Building, List<Integer>> byBuilding = new LinkedHashMap<>();
    zones.stream()
        .sorted(Comparator.comparing((MemberZone z) -> z.getBuilding().getDisplayOrder())
            .thenComparing(MemberZone::getFloor))
        .forEach(z -> byBuilding.computeIfAbsent(z.getBuilding(), b -> new ArrayList<>()).add(z.getFloor()));
    return byBuilding.entrySet().stream()
        .map(e -> new ZoneResponse(e.getKey().getId(), e.getKey().getName(), e.getValue()))
        .toList();
  }

  // ---------------- 설정 (권한 W, A) ----------------

  public SettingsResponse getSettings() {
    return SettingsResponse.from(memberAccess.require(Role.WORKER, Role.ADMIN));
  }

  /** 보낸 값만 바꾸고, 바뀐 뒤의 값으로 min < max 를 검사한다 */
  @Transactional
  public SettingsResponse updateSettings(SettingsUpdateRequest req) {
    Member me = memberAccess.require(Role.WORKER, Role.ADMIN);
    if (req.getAlertLeadMinutes() != null && !ALERT_LEAD_MINUTES.contains(req.getAlertLeadMinutes())) {
      throw new CustomException(GlobalErrorCode.INVALID_ALERT_LEAD_MINUTES);
    }
    SettingsResponse current = SettingsResponse.from(me);
    int tempMin = req.getTempMin() != null ? req.getTempMin() : current.getTempMin();
    int tempMax = req.getTempMax() != null ? req.getTempMax() : current.getTempMax();
    int humidityMin = req.getHumidityMin() != null ? req.getHumidityMin() : current.getHumidityMin();
    int humidityMax = req.getHumidityMax() != null ? req.getHumidityMax() : current.getHumidityMax();
    if (tempMin >= tempMax || humidityMin >= humidityMax) {
      throw new CustomException(GlobalErrorCode.INVALID_RANGE_SETTING);
    }
    me.updateSettings(req.getCo2Threshold(), req.getTempMin(), req.getTempMax(), req.getHumidityMin(), req.getHumidityMax(),
        req.getAlertEnabled(), req.getAlertLeadMinutes());
    return SettingsResponse.from(me);
  }
}
