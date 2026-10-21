package com.app.sumpyo_be.global.talk.entity;

/** 캠퍼스톡 상태. 보낸 사람이 아닌 근로생이 처음 열면 CHECKING, 첫 답변이 달리면 ANSWERED */
public enum TalkStatus {
  UNREAD, CHECKING, ANSWERED
}
