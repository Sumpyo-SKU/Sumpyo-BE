package com.app.sumpyo_be.global.member.service;

import com.app.sumpyo_be.global.building.repository.BuildingRepository;
import com.app.sumpyo_be.global.member.entity.Member;
import com.app.sumpyo_be.global.member.entity.MemberZone;
import com.app.sumpyo_be.global.member.entity.Role;
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import com.app.sumpyo_be.global.member.repository.MemberZoneRepository;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** 데모 계정 (loginId → 이름, 처음 만들 때의 담당 구역). 담당 구역은 계정을 만들 때만 넣는다 */
  record DemoAccount(String name, String zoneBuilding, Integer zoneFloor) {}

  static final Map<String, DemoAccount> DEMO_ACCOUNTS = new LinkedHashMap<>();
  static {
    DEMO_ACCOUNTS.put(DEMO_LOGIN_ID, new DemoAccount("김근로", null, null));
    DEMO_ACCOUNTS.put("worker02", new DemoAccount("이근로", "북악관", 6));
  }

  private final MemberRepository memberRepository;
  private final MemberZoneRepository zoneRepository;
  private final BuildingRepository buildingRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${sumpyo.demo.password:}")
  private String demoPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    DEMO_ACCOUNTS.forEach(this::createIfAbsent);
  }

  private void createIfAbsent(String loginId, DemoAccount account) {
    if (memberRepository.existsByLoginId(loginId)) {
      return;
    }
    if (demoPassword == null || demoPassword.isBlank()) {
      log.warn("DEMO_PASSWORD 가 없어 데모 계정({})을 만들지 않았습니다. /api/me/* 는 401 을 돌려줍니다.", loginId);
      return;
    }
    Member member = memberRepository.save(
        Member.create(loginId, passwordEncoder.encode(demoPassword), account.name(), Role.WORKER));
    if (account.zoneBuilding() != null) {
      buildingRepository.findByName(account.zoneBuilding()).filter(b -> b.hasFloor(account.zoneFloor()))
          .ifPresent(b -> zoneRepository.save(MemberZone.of(member.getId(), b, account.zoneFloor())));
    }
    log.info("데모 근로생 계정({})을 만들었습니다.", loginId);
  }
}
