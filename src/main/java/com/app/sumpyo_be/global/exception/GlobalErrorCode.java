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
    READING_NOT_FOUND("G007", "측정 데이터가 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_TIME_RANGE("G008", "조회 구간이 올바르지 않습니다. (from < to, 최대 31일)", HttpStatus.BAD_REQUEST),
    ROOM_NOT_FOUND("G009", "존재하지 않는 공간입니다.", HttpStatus.NOT_FOUND),
    BUILDING_NOT_FOUND("G010", "존재하지 않는 건물입니다.", HttpStatus.NOT_FOUND),
    ACTION_NOT_FOUND("G011", "존재하지 않는 조치입니다.", HttpStatus.NOT_FOUND),
    INVALID_ACTION_STATUS("G012", "현재 상태에서는 처리할 수 없는 조치입니다.", HttpStatus.CONFLICT),
    LOGIN_REQUIRED("G013", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),
    INVALID_ZONE_FLOOR("G014", "층은 1층부터 건물 최고 층 사이에서 하나 이상 골라야 합니다.", HttpStatus.BAD_REQUEST),
    DUPLICATED_ZONE("G015", "같은 건물이나 층을 중복해서 보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_RANGE_SETTING("G016", "최솟값은 최댓값보다 작아야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_ALERT_LEAD_MINUTES("G017", "알림 시점은 10·15·20분 중 하나여야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_ZONE_FILTER("G018", "zone 은 mine 만 쓸 수 있습니다.", HttpStatus.BAD_REQUEST),
    // 학생 민원(QR) — 학생이 직접 보는 문구라 짧고 부드럽게
    ROOM_LOOKUP_NOT_FOUND("G019", "호실을 찾을 수 없어요", HttpStatus.NOT_FOUND),
    INVALID_ROOM_LOOKUP("G020", "건물과 호실을 다시 확인해주세요", HttpStatus.BAD_REQUEST),
    COMPLAINT_TOO_SOON("G021", "잠시 후 다시 알려주세요", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_COMPLAINT_CONTENT("G022", "민원 상세는 500자까지 쓸 수 있어요", HttpStatus.BAD_REQUEST),
    COMPLAINT_UNAVAILABLE("G023", "지금은 민원을 받을 수 없어요. 잠시 후 다시 시도해주세요", HttpStatus.SERVICE_UNAVAILABLE),
    // 캠퍼스톡
    TALK_NO_ZONE("G024", "담당 구역을 먼저 설정해주세요.", HttpStatus.BAD_REQUEST),
    INVALID_TALK_FLOOR("G025", "층은 1층부터 건물 최고 층 사이여야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_TALK_ROOM("G026", "강의실은 내 건물의 선택한 층에 있어야 합니다.", HttpStatus.BAD_REQUEST),
    DUPLICATED_TALK_FLOOR("G027", "같은 층을 중복해서 보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_TALK_CONTENT("G028", "내용은 1~300자로 입력해주세요.", HttpStatus.BAD_REQUEST),
    TALK_NOT_FOUND("G029", "존재하지 않는 캠퍼스톡입니다.", HttpStatus.NOT_FOUND),
    TALK_ACCESS_DENIED("G030", "내 건물의 캠퍼스톡만 볼 수 있습니다.", HttpStatus.FORBIDDEN),
    TALK_SELF_REPLY_DENIED("G031", "내가 보낸 캠퍼스톡에는 답할 수 없습니다.", HttpStatus.FORBIDDEN),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}