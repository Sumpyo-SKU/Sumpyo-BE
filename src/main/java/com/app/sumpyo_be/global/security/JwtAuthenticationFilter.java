package com.app.sumpyo_be.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 토큰 → SecurityContext 에 MemberPrincipal.
 * 토큰이 없으면 그냥 통과 (공개 API, 데모 모드는 CurrentMemberProvider 가 처리).
 * 토큰이 있는데 만료·위조면 401 (공개 API 여도 — 프론트가 저장된 토큰을 지우게).
 * 로그인·가입(/api/auth/**)은 오래된 토큰이 붙어 와도 무시한다.
 * 빈으로 등록하지 않는다 (서블릿 필터로 한 번 더 붙지 않게, SecurityConfig 에서 직접 생성).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  static final String BEARER = "Bearer ";

  private final JwtTokenProvider tokenProvider;
  private final SecurityErrorWriter errorWriter;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SecurityErrorWriter errorWriter) {
    this.tokenProvider = tokenProvider;
    this.errorWriter = errorWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/api/auth/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || header.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<MemberPrincipal> principal = header.startsWith(BEARER)
        ? tokenProvider.parse(header.substring(BEARER.length()).strip())
        : Optional.empty();
    if (principal.isEmpty()) {
      errorWriter.invalidToken(response);
      return;
    }
    MemberPrincipal p = principal.get();
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role()))));
    chain.doFilter(request, response);
  }
}
