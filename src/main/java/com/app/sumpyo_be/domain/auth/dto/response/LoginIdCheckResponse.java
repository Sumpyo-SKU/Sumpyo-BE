package com.app.sumpyo_be.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/auth/check-login-id */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "아이디 중복 확인 결과")
public class LoginIdCheckResponse {

  @Schema(description = "확인한 아이디", example = "worker03")
  private String loginId;

  @Schema(description = "사용 가능하면 true ('*사용 가능한 아이디입니다.'), 이미 있으면 false", example = "true")
  private boolean available;
}
