package com.app.sumpyo_be.global.member.service;

import com.app.sumpyo_be.global.air.Thresholds;
import com.app.sumpyo_be.global.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThresholdService {

  private final CurrentMemberProvider currentMemberProvider;
  private final MemberRepository memberRepository;

  /** 로그인 사용자 설정값, 비로그인이면 기본값 (1000 / 20–26 / 40–60) */
  public Thresholds current() {
    return currentMemberProvider.currentMemberId()
        .flatMap(memberRepository::findById)
        .map(Thresholds::from)
        .orElse(Thresholds.DEFAULT);
  }
}
