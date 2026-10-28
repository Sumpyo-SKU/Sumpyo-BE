package com.app.sumpyo_be.global.config;

import com.app.sumpyo_be.global.security.JwtAuthenticationFilter;
import com.app.sumpyo_be.global.security.JwtTokenProvider;
import com.app.sumpyo_be.global.security.MemberPrincipal;
import com.app.sumpyo_be.global.security.SecurityErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Spring Security (stateless, JWT). 세션·폼 로그인·CSRF 없음, 로그아웃은 프론트가 토큰을 지우면 끝.
 * - 공개: 로그인·가입, 학생 민원 제출·호실 찾기, 건물 목록, 센서 측정값 저장(기기 키로 따로 검증), 헬스체크, Swagger
 * - 그 외(근로생 API): 토큰이 있으면 그 회원. 토큰이 없으면 데모 모드(DEMO_HEADER_LOGIN=true)일 때만 통과
 *   (CurrentMemberProvider 가 worker01 / X-Demo-Login), 아니면 401
 * - 역할(W/A) 검사는 지금처럼 서비스의 MemberAccess 가 한다
 */
@Configuration
public class SecurityConfig {

    static final String[] PUBLIC_ANY = {
        "/api/auth/**", "/api/health", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error",
        "/actuator/health/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
            SecurityErrorWriter errorWriter, @Value("${sumpyo.demo.header-login:false}") boolean demoHeaderLogin)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())   // CorsConfig.corsConfigurationSource (Authorization 포함 모든 헤더 허용)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_ANY).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/complaints").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rooms/lookup", "/api/buildings").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/rooms/*/readings").permitAll()
                .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                    authentication.get().getPrincipal() instanceof MemberPrincipal || demoHeaderLogin)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(errorWriter.loginRequired())
                .accessDeniedHandler(errorWriter.accessDenied()))
            .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, errorWriter), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
