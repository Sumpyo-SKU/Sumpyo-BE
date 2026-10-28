package com.app.sumpyo_be.domain.auth.controller;

import com.app.sumpyo_be.domain.auth.dto.request.LoginRequest;
import com.app.sumpyo_be.domain.auth.dto.request.SignupRequest;
import com.app.sumpyo_be.domain.auth.dto.response.LoginIdCheckResponse;
import com.app.sumpyo_be.domain.auth.dto.response.LoginResponse;
import com.app.sumpyo_be.domain.auth.service.AuthService;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.common.BaseResponse;
import com.app.sumpyo_be.global.common.PublicApi;
import com.app.sumpyo_be.global.common.ScreenTag;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·회원가입 (공개). 로그아웃 API 는 없다: 프론트가 저장한 토큰을 지우면 끝 (stateless) */
@RestController
@PublicApi
@Tag(name = "Auth", description = "로그인 · 회원가입 (로그아웃은 프론트가 토큰 삭제)")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "아이디 중복 확인",
      description = "공개. 형식(영문 소문자로 시작, 영문 소문자·숫자 4~20자)이 틀리면 400. "
          + "available true → '*사용 가능한 아이디입니다.', false → 사용 중 문구.")
  @ApiErrors({GlobalErrorCode.INVALID_LOGIN_ID})
  @Tag(name = ScreenTag.AUTH)
  @GetMapping("/check-login-id")
  public BaseResponse<LoginIdCheckResponse> checkLoginId(
      @Parameter(description = "아이디", example = "worker03") @RequestParam String loginId) {
    return BaseResponse.success(authService.checkLoginId(loginId));
  }

  @Operation(summary = "회원가입",
      description = "공개. name 1~30자, loginId 영문 소문자로 시작 4~20자, password 영문·숫자 포함 8~64자, "
          + "passwordConfirm 일치. 빈 칸이 있으면 400 '모든 정보를 입력해 주세요.'. 이미 있는 아이디 409. "
          + "가입하면 바로 로그인 상태(로그인과 같은 응답, 근로생 · 기본 설정값, 담당 구역 없음 → hasZones false).")
  @ApiErrors(value = {GlobalErrorCode.INVALID_LOGIN_ID, GlobalErrorCode.PASSWORD_CONFIRM_MISMATCH,
      GlobalErrorCode.DUPLICATED_LOGIN_ID}, validation = true)
  @Tag(name = ScreenTag.AUTH)
  @PostMapping("/signup")
  public BaseResponse<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
    return BaseResponse.success("회원가입이 완료되었습니다.", authService.signup(request));
  }

  @Operation(summary = "로그인",
      description = "공개. 성공하면 accessToken(JWT, 7일). 이후 요청에 Authorization: Bearer {accessToken}. "
          + "아이디가 없든 비밀번호가 틀리든 같은 401. 같은 IP 에서 10분 안에 10번 넘게 실패하면 429. "
          + "hasZones false 면 온보딩(담당 구역 설정)으로. 로그아웃은 프론트가 토큰을 지운다.")
  @ApiErrors(value = {GlobalErrorCode.INVALID_CREDENTIALS, GlobalErrorCode.TOO_MANY_LOGIN_ATTEMPTS}, validation = true)
  @Tag(name = ScreenTag.AUTH)
  @PostMapping("/login")
  public BaseResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    // Caddy 뒤: server.forward-headers-strategy=framework 라 remoteAddr 이 실제 클라이언트 IP
    return BaseResponse.success("로그인되었습니다.", authService.login(request, http.getRemoteAddr()));
  }
}
