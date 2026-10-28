package com.app.sumpyo_be.domain.member.dto.response;

import com.app.sumpyo_be.domain.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/me : 앱 시작 시 저장된 토큰이 아직 유효한지 확인 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "내 정보")
public class MeResponse {

  @Schema(description = "회원 ID", example = "3")
  private Long memberId;

  @Schema(description = "아이디", example = "worker01")
  private String loginId;

  @Schema(description = "이름", example = "김근로")
  private String name;

  @Schema(description = "역할 (WORKER: 근로생, ADMIN: 관리자)", example = "WORKER")
  private Role role;

  @Schema(description = "담당 구역이 있는지. false 면 온보딩(담당 구역 설정)으로", example = "true")
  private boolean hasZones;

  @Schema(description = "근로 구역 표시 ('북악관 · 6층', 여러 층이면 '북악관 · 5·6층', 여러 건물이면 첫 건물). 없으면 null",
      example = "북악관 · 6층")
  private String zone;
}
