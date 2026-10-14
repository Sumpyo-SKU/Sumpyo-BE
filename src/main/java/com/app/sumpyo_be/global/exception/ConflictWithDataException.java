package com.app.sumpyo_be.global.exception;

import com.app.sumpyo_be.global.exception.model.BaseErrorCode;
import lombok.Getter;

/**
 * 일반 CustomException과 달리, 409 응답 본문(data)에 추가 정보를 실어 보내야 할 때 쓴다.
 * 예: "이미 진행 중인 훈련방이 있습니다" 에러에 그 기존 방의 ID를 같이 내려줘서, 프론트가
 * "이어서 하시겠어요?" 안내 후 바로 그 방으로 이동시킬 수 있게 한다. GlobalExceptionHandler에
 * 전용 처리기가 있다.
 */
@Getter
public class ConflictWithDataException extends RuntimeException {

    private final BaseErrorCode errorCode;
    private final Object data;

    public ConflictWithDataException(BaseErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
}
