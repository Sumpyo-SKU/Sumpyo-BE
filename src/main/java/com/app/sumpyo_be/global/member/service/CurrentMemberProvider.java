package com.app.sumpyo_be.global.member.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** 로그인 사용자 조회 창구. 로그인이 붙으면 여기만 바꾸면 된다. */
@Component
public class CurrentMemberProvider {

  // TODO: 로그인 구현 후 SecurityContext(또는 토큰)에서 member id 를 꺼내도록 변경. 지금은 항상 비로그인.
  public Optional<Long> currentMemberId() {
    return Optional.empty();
  }
}
