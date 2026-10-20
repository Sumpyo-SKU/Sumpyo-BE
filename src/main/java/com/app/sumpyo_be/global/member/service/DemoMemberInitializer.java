package com.app.sumpyo_be.global.member.service;

import com.app.sumpyo_be.global.member.entity.Member;
import com.app.sumpyo_be.global.member.entity.Role;
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 구현 전 임시: 앱 시작 시 데모 근로생 계정이 없으면 만든다.
 * 비밀번호는 환경변수 DEMO_PASSWORD 를 BCrypt 로 저장 (코드/SQL 에 평문 금지). 없으면 계정을 만들지 않는다.
 * TODO: 회원가입/관리자 계정 발급이 생기면 제거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoMemberInitializer implements ApplicationRunner {

  public static final String DEMO_LOGIN_ID = "worker01";
  private static final String DEMO_NAME = "김근로";

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${sumpyo.demo.password:}")
  private String demoPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (memberRepository.existsByLoginId(DEMO_LOGIN_ID)) {
      return;
    }
    if (demoPassword == null || demoPassword.isBlank()) {
      log.warn("DEMO_PASSWORD 가 없어 데모 계정({})을 만들지 않았습니다. /api/me/* 는 401 을 돌려줍니다.", DEMO_LOGIN_ID);
      return;
    }
    memberRepository.save(Member.create(DEMO_LOGIN_ID, passwordEncoder.encode(demoPassword), DEMO_NAME, Role.WORKER));
    log.info("데모 근로생 계정({})을 만들었습니다.", DEMO_LOGIN_ID);
  }
}
