package com.app.sumpyo_be.global.security;

/** 토큰으로 확인한 로그인 회원 (SecurityContext 의 principal). role 은 WORKER·ADMIN */
public record MemberPrincipal(Long memberId, String role) {
}
