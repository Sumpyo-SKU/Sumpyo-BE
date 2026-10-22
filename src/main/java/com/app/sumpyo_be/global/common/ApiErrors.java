package com.app.sumpyo_be.global.common;

import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger 에러 응답 문서화. 적은 에러 코드의 HTTP 상태·메시지로 응답 예시를 만든다 (SwaggerConfig).
 * GlobalErrorCode 를 그대로 쓰므로 문서와 실제 응답 문구가 어긋나지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiErrors {

  GlobalErrorCode[] value() default {};

  /** 근로생·관리자 API: 401 로그인 필요(데모 계정 없음·잘못된 X-Demo-Login), 403 권한 없음 을 붙인다 */
  boolean auth() default false;

  /** 요청 검증 실패 400 (본문·쿼리 파라미터) 을 붙인다 */
  boolean validation() default false;
}
