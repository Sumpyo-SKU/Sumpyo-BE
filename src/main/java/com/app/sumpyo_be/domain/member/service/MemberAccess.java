package com.app.sumpyo_be.domain.member.service;

import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 명세서의 "권한" 칸 (W: WORKER, A: ADMIN) 확인.
 * 로그인 사용자가 없으면 401, 역할이 맞지 않으면 403.
 * TODO: 로그인 + Spring Security 를 붙이면 @PreAuthorize 등으로 옮길 수 있음
 */
@Component
@RequiredArgsConstructor
public class MemberAccess {

  private final CurrentMemberProvider currentMemberProvider;
  private final MemberRepository memberRepository;

  public Member require(Role... allowed) {
    Member member = currentMemberProvider.currentMemberId()
        .flatMap(memberRepository::findById)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.LOGIN_REQUIRED));
    if (!Arrays.asList(allowed).contains(member.getRole())) {
      throw new CustomException(GlobalErrorCode.ACCESS_DENIED);
    }
    return member;
  }
}
