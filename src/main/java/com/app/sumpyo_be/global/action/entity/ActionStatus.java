package com.app.sumpyo_be.global.action.entity;

import java.util.List;

public enum ActionStatus {
  PENDING, CHECKED, IN_PROGRESS, DONE, EXPIRED;

  /** 환기 시작 전(대기) 상태 */
  public static final List<ActionStatus> WAITING = List.of(PENDING, CHECKED);

  public boolean isWaiting() {
    return WAITING.contains(this);
  }
}
