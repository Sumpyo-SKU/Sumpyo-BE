package com.app.sumpyo_be.domain.action.controller;

/** 조치 다른 방법 API Swagger 응답 예시. ComplaintActionResponse 필드를 바꾸면 같이 고칠 것 */
final class ActionRecommendationExamples {

  private ActionRecommendationExamples() {}

  /** POST /api/actions/{actionId}/recommendation: CO2 기준 초과 예측 조치의 다른 방법 (창문 2곳 → 4곳) */
  static final String REPLAN_WINDOW = """
      {"success": true, "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": {
        "actionId": 88, "status": "PENDING", "summary": "창문 4곳을 15분 열어 환기해주세요",
        "reason": "창문을 더 많이 열면 더 빨리 환기돼요", "source": "RULE",
        "co2Before": 1250, "expectedCo2After": 757,
        "effectMetric": "CO2", "effectBefore": 1250, "effectAfter": 757, "effectUnit": "ppm",
        "forecastCo2": 1320,
        "steps": [{"type": "WINDOW_OPEN", "label": "창문 4곳 열기", "windows": 4, "targetTemp": null, "minutes": 15,
                   "doneAt": null}],
        "completedAt": null, "measureAt": null, "actualCo2After": null, "goalReached": null
      }}""";
}
