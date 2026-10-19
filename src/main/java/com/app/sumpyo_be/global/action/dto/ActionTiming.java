package com.app.sumpyo_be.global.action.dto;

/** 조치 목록의 긴급도. expectedAt 이 지났거나 10분 이내 NOW, 30분 이내 SOON, 그 외 LATER */
public enum ActionTiming {
  NOW, SOON, LATER
}
