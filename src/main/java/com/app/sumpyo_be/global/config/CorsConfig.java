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
 * CORS. 허용 출처는 환경 변수 CORS_ALLOWED_ORIGINS (쉼표 구분).
 * - addCorsMappings: Spring MVC 에 적용. 지금처럼 시큐리티가 없을 때 실제로 동작하는 쪽
 * - corsConfigurationSource 빈: 시큐리티를 붙이면 http.cors() 가 이 빈을 쓴다 (같은 설정)
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .combine(corsConfiguration());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로에 CORS 설정 적용
        source.registerCorsConfiguration("/**", corsConfiguration());
        return source;
    }

    private CorsConfiguration corsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        // 환경 변수에 정의된 출처만 허용
        config.setAllowedOrigins(Arrays.stream(allowedOrigins).map(String::trim).toList());
        // 허용할 HTTP Method
        config.setAllowedMethods(ALLOWED_METHODS);
        // 허용할 요청 Header
        config.setAllowedHeaders(List.of("*"));
        // 쿠키/인증 정보를 포함한 요청 허용
        config.setAllowCredentials(true);
        return config;
    }
}
