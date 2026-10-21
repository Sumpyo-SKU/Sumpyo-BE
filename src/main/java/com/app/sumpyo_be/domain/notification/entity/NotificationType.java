package com.app.sumpyo_be.domain.notification.entity;

/** 알림 종류. 아이콘 색은 프론트가 type 별로 정한다 */
public enum NotificationType {
  /** 방이 기준 초과(OVER). 받는 사람: notifyVentilation */
  VENTILATION_NOW,
  /** 예측 조치의 기준 초과까지 alertLeadMinutes 이내. 받는 사람: notifyVentilation + alertEnabled */
  VENTILATION_SOON,
  /** 같은 방 민원 최근 7일 5건 이상 (방·주 단위 1번). 받는 사람: notifyComplaint */
  REPEATED_COMPLAINT,
  /** 민원 접수. 받는 사람: notifyComplaint */
  NEW_COMPLAINT,
  /** FULL 센서 방 10분 넘게 수신 없음. 받는 사람: notifySensorError */
  SENSOR_ERROR
}
