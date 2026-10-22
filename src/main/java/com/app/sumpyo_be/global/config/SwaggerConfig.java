package com.app.sumpyo_be.global.config;

import com.app.sumpyo_be.domain.member.service.CurrentMemberProvider;
import com.app.sumpyo_be.global.common.ApiErrors;
import com.app.sumpyo_be.global.common.PublicApi;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

// http://localhost:8080/swagger-ui/index.html
@Configuration
public class SwaggerConfig {

    static final String DESCRIPTION = """
            숨표 근로생 앱 API.

            **인증**: 현재 로그인 없음. 근로생 API 는 데모 계정(worker01)으로 동작하고, \
            데모 모드(DEMO_HEADER_LOGIN=true)에서는 `X-Demo-Login: worker02` 헤더로 다른 데모 계정으로 바꿀 수 있다. \
            학생 민원 제출·호실 찾기·센서 측정값 저장은 공개 API 라 헤더가 없다. (Bearer 토큰 미사용)

            **응답**: 모든 응답은 `{ success, code, message, data }`. 시각은 KST `yyyy-MM-dd'T'HH:mm:ss` (초까지, 소수점 없음).

            **목록**: 민원·캠퍼스톡·알림은 모두 커서 무한 스크롤. 응답의 `nextCursor` 를 다음 요청의 `cursor` 로 보내고, \
            `hasNext` 가 false 면(nextCursor null) 끝. 페이지 번호 방식은 없음.
            """;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        Server server = new Server();
        server.setUrl(contextPath);
        server.setDescription("운영 서버");
        return new OpenAPI()
                .addServersItem(server)
                .info(new Info().title("SumPyo API 명세서").version("1.0").description(DESCRIPTION));
    }

    @Bean
    public GroupedOpenApi customGroupedOpenApi(OperationCustomizer demoLoginHeader, OperationCustomizer errorResponses) {
        return GroupedOpenApi.builder().group("api").pathsToMatch("/**")
                .addOperationCustomizer(demoLoginHeader)
                .addOperationCustomizer(errorResponses)
                .build();
    }

    /** 데모 모드(DEMO_HEADER_LOGIN=true)면 근로생 API 에만 X-Demo-Login 헤더 입력칸 (공개 API 제외) */
    @Bean
    public OperationCustomizer demoLoginHeader(@Value("${sumpyo.demo.header-login:false}") boolean enabled) {
        return (operation, handlerMethod) -> {
            if (enabled && !isPublic(handlerMethod)) {
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

    /** @ApiErrors 의 에러 코드 → HTTP 상태별 응답 + 코드별 예시 ({ success:false, code, message, data:null }) */
    @Bean
    public OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            ApiErrors errors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (errors == null) {
                return operation;
            }
            Set<GlobalErrorCode> codes = new LinkedHashSet<>();
            if (errors.validation()) {
                codes.add(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
            codes.addAll(List.of(errors.value()));
            if (errors.auth()) {
                codes.add(GlobalErrorCode.LOGIN_REQUIRED);
                codes.add(GlobalErrorCode.ACCESS_DENIED);
            }
            Map<Integer, List<GlobalErrorCode>> byStatus = new LinkedHashMap<>();
            codes.forEach(c -> byStatus.computeIfAbsent(c.getStatus().value(), k -> new ArrayList<>()).add(c));

            ApiResponses responses = operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
            byStatus.forEach((status, list) -> {
                MediaType json = new MediaType();
                list.forEach(c -> json.addExamples(c.getCode() + " " + c.name(), new Example()
                        .summary(c.getMessage())
                        .value(Map.of("success", false, "code", status, "message", c.getMessage()))));
                responses.addApiResponse(String.valueOf(status), new ApiResponse()
                        .description(String.join(" / ", list.stream().map(GlobalErrorCode::getMessage).toList()))
                        .content(new Content().addMediaType("application/json", json)));
            });
            operation.setResponses(responses);
            return operation;
        };
    }

    private static boolean isPublic(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
    }
}
