package com.app.sumpyo_be.domain.auth.dto.request;

import com.app.sumpyo_be.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/auth/signup. 형식 규칙은 화면 문구와 같다 (문구 표시는 프론트) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "회원가입 요청")
public class SignupRequest {

  @Schema(description = "이름 (1~30자)", example = "박근로", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  @Size(max = 30, message = "이름은 30자 이하로 입력해 주세요.")
  private String name;

  @Schema(description = "아이디 (영문 소문자로 시작, 영문 소문자·숫자 4~20자)", example = "worker03",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  @Pattern(regexp = AuthService.LOGIN_ID_REGEX, message = "아이디는 영문 소문자로 시작하는 영문 소문자·숫자 4~20자여야 합니다.")
  private String loginId;

  @Schema(description = "비밀번호 (영문·숫자 각각 1개 이상 포함 8~64자)", example = "abc12345",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  @Pattern(regexp = AuthService.PASSWORD_REGEX, message = AuthService.PASSWORD_MESSAGE)
  private String password;

  @Schema(description = "비밀번호 확인 (password 와 같아야 함)", example = "abc12345",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = AuthService.BLANK_MESSAGE)
  private String passwordConfirm;
}
