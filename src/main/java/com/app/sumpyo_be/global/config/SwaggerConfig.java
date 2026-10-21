package com.app.sumpyo_be.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import com.app.sumpyo_be.domain.member.service.CurrentMemberProvider;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// http://localhost:8080/swagger-ui/index.html
@Configuration
public class SwaggerConfig {

    private static final String JWT_SCHEME_NAME = "bearerAuth";

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl(contextPath);
        localServer.setDescription("Local Server");

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(JWT_SCHEME_NAME);

        Components components = new Components()
                .addSecuritySchemes(JWT_SCHEME_NAME, new SecurityScheme()
                        .name(JWT_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .addServersItem(localServer)
                .info(new Info().title("SumPyo API 명세서").version("1.0").description("Swagger"))
                .addSecurityItem(securityRequirement)
                .components(components);
    }

    @Bean
    public GroupedOpenApi customGroupedOpenApi(OperationCustomizer demoLoginHeader) {
        return GroupedOpenApi.builder().group("api").pathsToMatch("/**")
                .addOperationCustomizer(demoLoginHeader).build();
    }

    /** 데모 모드(DEMO_HEADER_LOGIN=true)면 모든 API 에 X-Demo-Login 헤더 입력칸을 붙인다 */
    @Bean
    public OperationCustomizer demoLoginHeader(@Value("${sumpyo.demo.header-login:false}") boolean enabled) {
        return (operation, handlerMethod) -> {
            if (enabled) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(CurrentMemberProvider.DEMO_LOGIN_HEADER)
                        .required(false)
                        .description("데모 계정 전환 (비우면 worker01, 예: worker02)")
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }
}
