package com.app.sumpyo_be.global.common;

import io.swagger.v3.oas.annotations.media.ExampleObject;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger 200 응답 예시 여러 개. springdoc 이 반환 타입으로 만든 스키마는 그대로 두고 예시만 더한다 (SwaggerConfig).
 * @ApiResponse(content = @Content(examples = ...)) 로 넣으면 스키마가 빠지므로 이걸 쓴다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiSuccessExamples {

  ExampleObject[] value();
}
