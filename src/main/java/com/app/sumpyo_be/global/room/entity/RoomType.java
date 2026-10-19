package com.app.sumpyo_be.global.room.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomType {
  LECTURE("강의실"),
  RESTROOM("화장실"),
  ETC("기타");

  private final String label;
}
