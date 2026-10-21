package com.app.sumpyo_be.global.complaint.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 민원 분류. complaint.categories 에 쉼표로 이어서 저장한다 (V1 CHECK 제약과 같은 값) */
@Getter
@RequiredArgsConstructor
public enum ComplaintCategory {
  STUFFY("답답해요"),
  HOT("더워요"),
  COLD("추워요"),
  HUMID("습해요"),
  SMELL("냄새나요"),
  BROKEN("설비 고장");

  private final String label;
}
