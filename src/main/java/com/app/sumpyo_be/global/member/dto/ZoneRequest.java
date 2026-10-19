package com.app.sumpyo_be.global.member.dto;

import java.util.List;

/** PUT /api/me/zones 본문의 항목: { "buildingId": 1, "floors": [5] } (검증은 MeService) */
public record ZoneRequest(Long buildingId, List<Integer> floors) {}
