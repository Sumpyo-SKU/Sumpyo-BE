package com.app.sumpyo_be.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(title = "BaseResponse DTO", description = "공통 API 응답")
public class BaseResponse<T> {

    @Schema(description = "요청 성공 여부", example = "true")
    private boolean success;

    @Schema(description = "HTTP 상태 코드", example = "200")
    private int code;

    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리 되었습니다.")
    private String message;

    @Schema(description = "응답 데이터")
    private T data;

    public static<T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, 200, "요청이 성공적으로 처리되었습니다.", data);
    }
    public static<T> BaseResponse<T> success(String message, T data) {
        return new BaseResponse<>(true, 200, message, data);
    }
    public static<T> BaseResponse<T> success(int code, String message, T data) {
        return new BaseResponse<>(true, code, message, data);
    }
    public static<T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(false, code, message, null);
    }

    /**
     * data까지 실어 보내는 에러 응답. 예: "이미 진행 중인 훈련방이 있습니다"(409)에 그 기존
     * 방의 ID를 같이 내려줘서, 프론트가 안내 후 바로 그 방으로 이동시킬 수 있게 한다.
     * (global.exception.ConflictWithDataException 참고)
     */
    public static<T> BaseResponse<T> error(int code, String message, T data) {
        return new BaseResponse<>(false, code, message, data);
    }

}
