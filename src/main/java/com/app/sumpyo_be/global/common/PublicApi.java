package com.app.sumpyo_be.global.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 로그인과 무관한 공개 API (학생 민원·센서·헬스체크). Swagger 에 X-Demo-Login 헤더를 붙이지 않는다 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
