package com.app.sumpyo_be.domain.room.controller;

/**
 * GET /api/rooms/{roomId} Swagger 응답 예시 (환기 상태별 × 진행 중인 환기 유무).
 * 기준: CO2 1000, 미리 알림 10분, 온도 20~26, 습도 40~60. RoomResponse 필드를 바꾸면 같이 고칠 것.
 */
final class RoomApiExamples {

  private RoomApiExamples() {}

  static final String OVER = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 1150, "temp": 24, "humidity": 45, "comfortScore": 67, "comfortLevel": "NORMAL",
        "co2Level": "CAUTION", "co2OverBy": 150, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": false,
        "co2Status": "OVER", "overThreshold": 150, "minutesToExceed": null, "ventilateInMinutes": null,
        "ventilatingAction": null,
        "pendingAction": {"actionId": 88, "message": "기준 초과 · 환기 전"},
        "currentClass": {"courseName": "자료구조", "start": "13:30", "end": "14:45"},
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": "쉬는 시간에 창문 환기 추천"}
      }}""";

  static final String OVER_VENTILATING = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 1080, "temp": 24, "humidity": 45, "comfortScore": 71, "comfortLevel": "GOOD",
        "co2Level": "CAUTION", "co2OverBy": 80, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": true,
        "co2Status": "OVER", "overThreshold": 80, "minutesToExceed": null, "ventilateInMinutes": null,
        "ventilatingAction": {"actionId": 88, "startedAt": "2026-10-21T13:50:00"},
        "pendingAction": null,
        "currentClass": {"courseName": "자료구조", "start": "13:30", "end": "14:45"},
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": "쉬는 시간에 창문 환기 추천"}
      }}""";

  static final String PREDICTED = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 920, "temp": 24, "humidity": 45, "comfortScore": 81, "comfortLevel": "GOOD",
        "co2Level": "CAUTION", "co2OverBy": 0, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": false,
        "co2Status": "PREDICTED", "overThreshold": null, "minutesToExceed": 20, "ventilateInMinutes": 10,
        "ventilatingAction": null,
        "pendingAction": {"actionId": 88, "message": "약 20분 뒤 기준 초과 예상"},
        "currentClass": {"courseName": "자료구조", "start": "13:30", "end": "14:45"},
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": "쉬는 시간에 창문 환기 추천"}
      }}""";

  static final String PREDICTED_VENTILATING = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 920, "temp": 24, "humidity": 45, "comfortScore": 81, "comfortLevel": "GOOD",
        "co2Level": "CAUTION", "co2OverBy": 0, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": true,
        "co2Status": "PREDICTED", "overThreshold": null, "minutesToExceed": 20, "ventilateInMinutes": 10,
        "ventilatingAction": {"actionId": 88, "startedAt": "2026-10-21T13:58:00"},
        "pendingAction": null,
        "currentClass": {"courseName": "자료구조", "start": "13:30", "end": "14:45"},
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": "쉬는 시간에 창문 환기 추천"}
      }}""";

  static final String NORMAL = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 650, "temp": 24, "humidity": 45, "comfortScore": 97, "comfortLevel": "GOOD",
        "co2Level": "NORMAL", "co2OverBy": 0, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": false,
        "co2Status": "NORMAL", "overThreshold": null, "minutesToExceed": null, "ventilateInMinutes": null,
        "ventilatingAction": null,
        "pendingAction": null,
        "currentClass": null,
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": null}
      }}""";

  static final String NORMAL_VENTILATING = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "roomId": 7, "building": "북악관", "number": "617", "floor": 6, "simulated": false,
        "co2": 650, "temp": 24, "humidity": 45, "comfortScore": 97, "comfortLevel": "GOOD",
        "co2Level": "NORMAL", "co2OverBy": 0, "tempLabel": "적정", "humidityLabel": "적정",
        "ventilating": true,
        "co2Status": "NORMAL", "overThreshold": null, "minutesToExceed": null, "ventilateInMinutes": null,
        "ventilatingAction": {"actionId": 93, "startedAt": "2026-10-21T14:00:00"},
        "pendingAction": null,
        "currentClass": null,
        "nextClass": {"courseName": "운영체제", "start": "15:00", "end": "16:15", "tip": null}
      }}""";
}
