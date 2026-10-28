package com.app.sumpyo_be.global.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS. 허용 출처는 환경 변수 CORS_ALLOWED_ORIGINS (쉼표 구분, 기본 http://localhost:5173).
 * 값은 allowedOriginPatterns 로 넣어서 정확한 주소와 와일드카드를 둘 다 쓸 수 있다
 * (예: https://sumpyo-sku.netlify.app,https://deploy-preview-*--sumpyo-sku.netlify.app,http://localhost:5173).
 * 쿠키를 쓰지 않고 토큰은 Authorization 헤더로 보내므로 allowCredentials = false.
 * - corsConfigurationSource 빈: SecurityConfig 의 http.cors() 가 쓴다 (프리플라이트 OPTIONS 는 인증 없이 통과)
 * - addCorsMappings: 같은 설정을 Spring MVC 에도 (시큐리티 필터를 끈 테스트에서도 같은 동작)
 * 공개 API(학생 민원 등)와 근로생 API 모두 같은 설정이다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "X-Demo-Login");

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").combine(corsConfiguration());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration());
        return source;
    }

    private CorsConfiguration corsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);   // 프리플라이트 결과를 1시간 캐시
        return config;
    }
}
