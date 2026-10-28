package com.app.sumpyo_be.global.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Swagger 화면별 태그. 컨트롤러 메서드에 리소스 태그(클래스 @Tag)와 함께 @Tag(name = ScreenTag.HOME) 처럼 하나 더 붙인다.
 * SwaggerConfig 가 이 순서대로 태그 목록 맨 위에 둔다.
 */
public final class ScreenTag {

  private ScreenTag() {}

  public static final String AUTH = "화면: 로그인·회원가입";
  public static final String HOME = "화면: 홈";
  public static final String CAMPUS = "화면: 캠퍼스 현황";
  public static final String INBOX = "화면: 접수함(민원)";
  public static final String TALK = "화면: 캠퍼스톡";
  public static final String SETTINGS = "화면: 설정";
  public static final String NOTIFICATION = "화면: 알림";
  public static final String STUDENT = "화면: 학생 민원(QR)";

  /** 태그 → 설명, 표시 순서 */
  public static final Map<String, String> ORDERED = new LinkedHashMap<>();

  static {
    ORDERED.put(AUTH, "로그인, 회원가입(아이디 중복 확인), 앱 시작 시 토큰 확인. 로그아웃은 프론트가 토큰 삭제");
    ORDERED.put(HOME, "방 선택, [지금] 쾌적도·CO2·온습도·바깥 공기, [수업 중 예보] 카드·그래프·창문 환기 예측");
    ORDERED.put(CAMPUS, "건물 선택, 층 평면도, 강의실 바텀시트, 조치 확인·환기 시작");
    ORDERED.put(INBOX, "민원 목록·반복 민원·상세, AI 추천, 처리 시작·체크리스트");
    ORDERED.put(TALK, "근로생 간 메시지, 추천 답변, 연락 볼 구역");
    ORDERED.put(SETTINGS, "환기 기준값·알림 on/off, 담당 구역, 민원 QR 발급");
    ORDERED.put(NOTIFICATION, "환기·민원·센서 알림함");
    ORDERED.put(STUDENT, "학생이 QR 로 여는 민원 제출 화면 (로그인 없음)");
  }
}
