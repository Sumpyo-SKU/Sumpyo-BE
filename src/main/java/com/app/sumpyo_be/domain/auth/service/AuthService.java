package com.app.sumpyo_be.domain.auth.service;

import com.app.sumpyo_be.domain.auth.dto.request.LoginRequest;
import com.app.sumpyo_be.domain.auth.dto.request.SignupRequest;
import com.app.sumpyo_be.domain.auth.dto.response.LoginIdCheckResponse;
import com.app.sumpyo_be.domain.auth.dto.response.LoginResponse;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import com.app.sumpyo_be.domain.member.repository.MemberZoneRepository;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.global.security.JwtTokenProvider;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·회원가입 (공개 API). 비밀번호는 BCrypt, 토큰은 JWT. 비밀번호·토큰은 로그에 남기지 않는다 (실패 로그엔 loginId 만).
 * 데모 계정(worker01·worker02)도 DEMO_PASSWORD 로 똑같이 로그인된다 (DemoMemberInitializer 가 BCrypt 로 저장).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  /** 영문 소문자로 시작, 영문 소문자·숫자 4~20자 */
  public static final String LOGIN_ID_REGEX = "^[a-z][a-z0-9]{3,19}$";
  /** 영문·숫자 각각 1개 이상 포함 8~64자 */
  public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
  public static final String PASSWORD_MESSAGE = "비밀번호 형식 오류 (영어, 숫자 포함 8글자 이상)";
  public static final String BLANK_MESSAGE = "모든 정보를 입력해 주세요.";
  public static final String TOKEN_TYPE = "Bearer";
  private static final Pattern LOGIN_ID = Pattern.compile(LOGIN_ID_REGEX);

  private final MemberRepository memberRepository;
  private final MemberZoneRepository zoneRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider tokenProvider;
  private final LoginAttemptLimiter limiter;

  /** 형식이 틀리면 400. 대소문자는 그대로 비교 (아이디는 소문자만 허용) */
  @Transactional(readOnly = true)
  public LoginIdCheckResponse checkLoginId(String loginId) {
    String id = requireLoginIdFormat(loginId);
    return new LoginIdCheckResponse(id, !memberRepository.existsByLoginId(id));
  }

  /** 가입하면 바로 로그인 상태 (토큰 발급). 중복 확인 뒤 다른 사람이 먼저 가입했으면 409 (DB UNIQUE 로도 막는다) */
  @Transactional
  public LoginResponse signup(SignupRequest req) {
    String loginId = requireLoginIdFormat(req.getLoginId());
    if (!req.getPassword().equals(req.getPasswordConfirm())) {
      throw new CustomException(GlobalErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }
    if (memberRepository.existsByLoginId(loginId)) {
      throw new CustomException(GlobalErrorCode.DUPLICATED_LOGIN_ID);
    }
    Member member;
    try {
      member = memberRepository.saveAndFlush(
          Member.create(loginId, passwordEncoder.encode(req.getPassword()), req.getName().strip(), Role.WORKER));
    } catch (DataIntegrityViolationException e) {
      throw new CustomException(GlobalErrorCode.DUPLICATED_LOGIN_ID);
    }
    log.info("회원가입: loginId={}", loginId);
    return loginResponse(member);
  }

  /**
   * 아이디가 없든 비밀번호가 틀리든 같은 401 (어느 쪽이 틀렸는지 알려주지 않는다).
   * 같은 IP 에서 10분 안에 10번 넘게 실패하면 429.
   */
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest req, String ip) {
    if (limiter.isBlocked(ip)) {
      log.warn("로그인 시도 제한: loginId={}", req.getLoginId());
      throw new CustomException(GlobalErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
    }
    Optional<Member> member = memberRepository.findByLoginId(req.getLoginId().strip());
    if (member.isEmpty() || !passwordEncoder.matches(req.getPassword(), member.get().getPassword())) {
      limiter.recordFailure(ip);
      log.warn("로그인 실패: loginId={}", req.getLoginId());
      throw new CustomException(GlobalErrorCode.INVALID_CREDENTIALS);
    }
    limiter.reset(ip);
    return loginResponse(member.get());
  }

  private LoginResponse loginResponse(Member m) {
    return LoginResponse.builder()
        .accessToken(tokenProvider.issue(m.getId(), m.getRole().name()))
        .tokenType(TOKEN_TYPE)
        .expiresIn(tokenProvider.validitySeconds())
        .memberId(m.getId())
        .name(m.getName())
        .role(m.getRole())
        .hasZones(zoneRepository.existsByMemberId(m.getId()))
        .build();
  }

  private static String requireLoginIdFormat(String loginId) {
    String id = loginId == null ? "" : loginId.strip();
    if (!LOGIN_ID.matcher(id).matches()) {
      throw new CustomException(GlobalErrorCode.INVALID_LOGIN_ID);
    }
    return id;
  }
}
