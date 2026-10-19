package com.app.sumpyo_be.global.room.dto;

import com.app.sumpyo_be.global.air.Co2Level;

/** GET /api/rooms/{roomId} : 평면도 바텀시트·강의실 상세 공용 */
public record RoomResponse(
    Long roomId,
    String building,
    String number,
    int floor,
    Integer co2,              // CO2 센서 없음/끊김이면 null
    Integer temp,
    Integer humidity,
    Integer comfortScore,     // 0~100
    Co2Level co2Level,
    Integer co2OverBy,        // 기준보다 높은 만큼, 기준 미만이면 0
    String tempLabel,         // "적정" / "살짝 더워요" ...
    String humidityLabel,     // "적정" / "조금 습해요" ...
    boolean ventilating,      // IN_PROGRESS 조치가 있으면 true ("환기 진행중" 배지)
    PendingAction pendingAction,
    CurrentClass currentClass,
    NextClass nextClass
) {

  /** PENDING/CHECKED 중 최신 조치 */
  public record PendingAction(Long actionId, String message) {}

  public record CurrentClass(String courseName, String start, String end, Integer capacity, Integer enrolled) {}

  public record NextClass(String courseName, String start, String end, String tip) {}
}
