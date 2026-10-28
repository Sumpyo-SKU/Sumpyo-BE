package com.app.sumpyo_be.domain.auth.dto.request;

import com.app.sumpyo_be.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/auth/login */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "로그인 요청")
public class LoginRequest {

  @Schema(description = "아이디", example = "worker01", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  private String loginId;

  @Schema(description = "비밀번호", example = "abc12345", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  private String password;
}
