package com.app.sumpyo_be.global.exception;

import com.app.sumpyo_be.global.exception.model.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {
    INVALID_INPUT_VALUE("G001", "유효하지 않은 입력입니다.", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("G002", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INTERNAL_SERVER_ERROR("G003", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCESS_DENIED("G004", "해당 리소스에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("G005", "유효하지 않거나 만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_DEVICE_KEY("G006", "유효하지 않은 기기 키입니다.", HttpStatus.UNAUTHORIZED),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}