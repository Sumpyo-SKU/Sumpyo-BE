package com.app.sumpyo_be.domain.member.service;

import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 로그인 사용자 조회 창구. 로그인이 붙으면 여기만 바꾸면 된다. */
@Component
public class CurrentMemberProvider {

  /** 데모 모드에서 계정 전환용 헤더 (예: X-Demo-Login: worker02) */
  public static final String DEMO_LOGIN_HEADER = "X-Demo-Login";

  private final MemberRepository memberRepository;
  private final boolean demoHeaderEnabled;
  /** loginId → member id. 계정이 아직 없으면 넣지 않는다 (생성 후 다시 찾음) */
  private final Map<String, Long> demoMemberIds = new ConcurrentHashMap<>();

  public CurrentMemberProvider(MemberRepository memberRepository,
      @Value("${sumpyo.demo.header-login:false}") boolean demoHeaderEnabled) {
    this.memberRepository = memberRepository;
    this.demoHeaderEnabled = demoHeaderEnabled;
  }

  // TODO: 로그인(POST /api/auth/login) 구현 후 SecurityContext(또는 토큰)에서 member id 를 꺼내도록 변경.
  //       지금은 데모 근로생으로 처리한다: 기본 worker01, 데모 모드에서 X-Demo-Login 헤더로 다른 데모 계정 선택.
  public Optional<Long> currentMemberId() {
    String loginId = DemoMemberInitializer.DEMO_LOGIN_ID;
    String header = demoHeaderEnabled ? demoLoginHeader() : null;
    if (header != null && !header.isBlank()) {
      loginId = header.trim();
      // 데모 계정만 고를 수 있다 (실제 회원으로 바꿔치기 방지). 그 외 값이면 비로그인 → 401
      if (!DemoMemberInitializer.DEMO_ACCOUNTS.containsKey(loginId)) {
        return Optional.empty();
      }
    }
    Long id = demoMemberIds.get(loginId);
    if (id == null) {
      id = memberRepository.findByLoginId(loginId).map(Member::getId).orElse(null);
      if (id != null) {
        demoMemberIds.put(loginId, id);
      }
    }
    return Optional.ofNullable(id);
  }

  /** 요청 스레드가 아니면(스케줄러 등) null */
  private static String demoLoginHeader() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletRequest request = attrs.getRequest();
      return request.getHeader(DEMO_LOGIN_HEADER);
    }
    return null;
  }
}
