package com.app.sumpyo_be.global.talk.suggestion;

import java.time.LocalTime;

/**
 * 추천 답변 재료.
 * @param roomNumber   대상 강의실 호수 (글의 강의실 → 없으면 본문의 "617호"). 못 찾으면 null
 * @param inClass      대상 강의실에 지금 수업이 있는지 (오늘 휴강 반영). roomNumber 가 null 이면 의미 없음
 * @param courseName   지금 수업 과목명 (inClass 일 때만)
 * @param classEndTime 지금 수업 끝나는 시각 (inClass 일 때만)
 * @param co2          대상 강의실 최근 10분 CO2. CO2 센서(FULL·SIM)가 없거나 측정이 없으면 null
 */
public record SuggestionContext(
    String building,
    int floor,
    String content,
    String roomNumber,
    boolean inClass,
    String courseName,
    LocalTime classEndTime,
    Integer co2
) {
  public boolean hasRoom() {
    return roomNumber != null;
  }
}
