package com.app.sumpyo_be.global.member.service;

import com.app.sumpyo_be.global.member.entity.Member;
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 로그인 사용자 조회 창구. 로그인이 붙으면 여기만 바꾸면 된다. */
@Component
@RequiredArgsConstructor
public class CurrentMemberProvider {

  private final MemberRepository memberRepository;

  private volatile Long demoMemberId;

  // TODO: 로그인(POST /api/auth/login) 구현 후 SecurityContext(또는 토큰)에서 member id 를 꺼내도록 변경.
  //       지금은 모든 요청을 데모 근로생(worker01)으로 처리한다.
  public Optional<Long> currentMemberId() {
    Long id = demoMemberId;
    if (id == null) {
      id = memberRepository.findByLoginId(DemoMemberInitializer.DEMO_LOGIN_ID).map(Member::getId).orElse(null);
      demoMemberId = id;
    }
    return Optional.ofNullable(id);
  }
}
