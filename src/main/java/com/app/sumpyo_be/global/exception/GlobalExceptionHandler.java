package com.app.sumpyo_be.global.exception;

import com.capstone.be.global.common.BaseResponse;
import com.capstone.be.global.exception.model.BaseErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리 클래스입니다.
 * develop 브랜치의 Map 기반 에러 처리 로직을 유지합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CustomException 발생 시 처리합니다.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Object>> handleCustomException(CustomException ex) {
        BaseErrorCode errorCode = ex.getErrorCode();
        log.warn("CustomException 발생: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(errorCode.getStatus().value(), errorCode.getMessage()));
    }

    /**
     * ConflictWithDataException 발생 시 처리합니다. 일반 CustomException과 달리 응답
     * body의 data에 추가 정보(예: 이미 진행 중인 방의 ID)를 실어 보냅니다.
     */
    @ExceptionHandler(ConflictWithDataException.class)
    public ResponseEntity<BaseResponse<Object>> handleConflictWithDataException(ConflictWithDataException ex) {
        BaseErrorCode errorCode = ex.getErrorCode();
        log.warn("ConflictWithDataException 발생: {} - {}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(BaseResponse.error(errorCode.getStatus().value(), errorCode.getMessage(), ex.getData()));
    }

    /**
     * 유효성 검증 실패(MethodArgumentNotValidException) 발생 시 처리합니다.
     * 각 필드별 에러 메시지를 Map에 담아 반환합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String errorMessages =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> String.format("[%s] %s", e.getField(), e.getDefaultMessage()))
                        .collect(Collectors.joining(" / "));
        log.warn("Validation 오류 발생: {}", errorMessages);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, errorMessages));
    }

    /**
     * @RequestParam/@PathVariable 등 메서드 파라미터 검증 실패 시 처리합니다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BaseResponse<Object>> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex) {
        String errorMessages = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(" / "));
        log.warn("Validation 오류 발생: {}", errorMessages);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, errorMessages));
    }

    /**
     * 필수 Authorization 헤더가 없을 때 처리합니다. (@RequestHeader("Authorization") String, required 기본값 true)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<BaseResponse<Object>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("필수 헤더 누락: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(BaseResponse.error(401, "로그인이 필요합니다."));
    }

    /**
     * 필수 @RequestParam이 아예 없을 때 처리합니다. (default 값 없는 파라미터, 예: search?keyword= 없이 호출)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Object>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("필수 파라미터 누락: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, String.format("'%s' 파라미터는 필수입니다.", ex.getParameterName())));
    }

    /**
     * 요청 본문을 읽을 수 없을 때(JSON 파싱 실패, enum에 정의되지 않은 값 등) 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("요청 본문 파싱 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, "요청 본문 형식이 올바르지 않습니다."));
    }

    /**
     * @RequestParam/@PathVariable 값이 선언된 타입(enum, 숫자 등)으로 변환되지 않을 때 처리합니다.
     * (예: sort=asdf 처럼 CommentSortType enum에 없는 값이 들어온 경우)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String message = String.format("'%s' 파라미터 값이 올바르지 않습니다: %s", ex.getName(), ex.getValue());
        log.warn("파라미터 타입 불일치: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, message));
    }

    /**
     * @Validated 클래스에서 @RequestParam/@PathVariable에 직접 붙인 제약(@NotBlank 등)이
     * AOP 메서드 검증(MethodValidationInterceptor)을 통해 실패했을 때 처리합니다.
     * (HandlerMethodValidationException과 별개 경로로도 발생할 수 있어 둘 다 처리)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String errorMessages = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(" / "));
        log.warn("Validation 오류 발생: {}", errorMessages);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(400, errorMessages));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("지원하지 않는 HTTP 메서드: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(BaseResponse.error(405, "지원하지 않는 요청 방식입니다: " + ex.getMethod()));
    }

    /**
     * 정적 리소스 미존재 시 처리합니다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("정적 리소스 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.error(404, "리소스를 찾을 수 없습니다."));
    }

    /**
     * 그 외 모든 예외 발생 시 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleException(Exception ex) {
        log.error("Server 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error(500, "예상치 못한 서버 오류가 발생했습니다."));
    }
}
