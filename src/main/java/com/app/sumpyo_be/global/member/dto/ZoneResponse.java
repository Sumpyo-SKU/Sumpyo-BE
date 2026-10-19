package com.app.sumpyo_be.global.member.dto;

import java.util.List;

/** GET /api/me/zones 항목. 건물 표시 순서 → 층 오름차순 */
public record ZoneResponse(Long buildingId, String buildingName, List<Integer> floors) {}
