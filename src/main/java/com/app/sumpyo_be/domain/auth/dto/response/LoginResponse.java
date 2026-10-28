package com.app.sumpyo_be.domain.auth.dto.response;

import com.app.sumpyo_be.domain.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/auth/login, /api/auth/signup (가입하면 바로 로그인 상태) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "로그인 결과 (액세스 토큰)")
public class LoginResponse {

  @Schema(description = "액세스 토큰 (JWT). 요청마다 Authorization: Bearer {accessToken}", example = "eyJhbGciOiJIUzI1NiJ9...")
  private String accessToken;

  @Schema(description = "토큰 종류", example = "Bearer")
  private String tokenType;

  @Schema(description = "유효기간 (초, 7일). 만료되면 다시 로그인 (리프레시 토큰 없음)", example = "604800")
  private long expiresIn;

  @Schema(description = "회원 ID", example = "3")
  private Long memberId;

  @Schema(description = "이름", example = "김근로")
  private String name;

  @Schema(description = "역할 (WORKER: 근로생, ADMIN: 관리자)", example = "WORKER")
  private Role role;

  @Schema(description = "담당 구역이 있는지. false 면 온보딩(담당 구역 설정)으로", example = "true")
  private boolean hasZones;
}
