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
    // 만료·위조된 토큰. 프론트는 LOGIN_REQUIRED 와 똑같이 토큰을 지우고 로그인 화면으로
    INVALID_TOKEN("G005", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),
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
    // 민원 QR
    QR_NOT_LECTURE("G032", "강의실만 민원 QR 을 발급할 수 있습니다.", HttpStatus.BAD_REQUEST),
    // 알림함
    NOTIFICATION_NOT_FOUND("G033", "존재하지 않는 알림입니다.", HttpStatus.NOT_FOUND),
    NOTIFICATION_ACCESS_DENIED("G034", "내 알림만 읽음 처리할 수 있습니다.", HttpStatus.FORBIDDEN),
    // 민원 접수함
    COMPLAINT_NOT_FOUND("G035", "존재하지 않는 민원입니다.", HttpStatus.NOT_FOUND),
    INVALID_COMPLAINT_STATUS("G036", "접수 상태의 민원만 추천을 받을 수 있습니다.", HttpStatus.CONFLICT),
    INVALID_ACTION_STEP("G037", "이 조치의 체크리스트에 없는 단계입니다.", HttpStatus.BAD_REQUEST),
    INVALID_CURSOR("G038", "목록 커서가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_WEEK("G039", "week 는 2026-W43 형식이어야 합니다.", HttpStatus.BAD_REQUEST),
    // 캠퍼스톡 삭제
    TALK_DELETE_DENIED("G040", "내가 보낸 캠퍼스톡만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN),
    TALK_REPLY_NOT_FOUND("G041", "존재하지 않는 답변입니다.", HttpStatus.NOT_FOUND),
    TALK_REPLY_DELETE_DENIED("G042", "내가 쓴 답변만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN),
    // 로그인·회원가입
    INVALID_CREDENTIALS("G043", "아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    DUPLICATED_LOGIN_ID("G044", "이미 사용 중인 아이디입니다.", HttpStatus.CONFLICT),
    INVALID_LOGIN_ID("G045", "아이디는 영문 소문자로 시작하는 영문 소문자·숫자 4~20자여야 합니다.", HttpStatus.BAD_REQUEST),
    PASSWORD_CONFIRM_MISMATCH("G046", "비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    TOO_MANY_LOGIN_ATTEMPTS("G047", "로그인 시도가 너무 많아요. 10분 뒤 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    // 조치 다른 방법 (G048 은 다른 브랜치에서 사용 중)
    ACTION_NOT_REPLANNABLE("G049", "진행 중이거나 끝난 조치는 다른 방법을 받을 수 없어요", HttpStatus.CONFLICT),
    ;

    private final String code;
    private final String message;
    private final HttpStatus status;
}