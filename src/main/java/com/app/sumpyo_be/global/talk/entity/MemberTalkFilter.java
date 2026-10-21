package com.app.sumpyo_be.global.talk.entity;

import com.app.sumpyo_be.global.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캠퍼스톡 연락 볼 구역 한 줄. room 이 null 이면 그 층 전체, 있으면 그 강의실만 */
@Entity
@Table(name = "member_talk_filter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberTalkFilter {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(nullable = false)
  private Integer floor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  public static MemberTalkFilter of(Long memberId, int floor, Room room) {
    MemberTalkFilter f = new MemberTalkFilter();
    f.memberId = memberId;
    f.floor = floor;
    f.room = room;
    return f;
  }
}
