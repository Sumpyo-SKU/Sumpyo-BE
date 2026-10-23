package com.app.sumpyo_be.domain.talk.entity;

import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.domain.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캠퍼스톡 글. 소통 범위는 건물: 같은 건물 근로생은 모든 글을 보고 답할 수 있다 */
@Entity
@Table(name = "talk_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TalkPost {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "sender_id", nullable = false)
  private Long senderId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "building_id", nullable = false)
  private Building building;

  @Column(nullable = false)
  private Integer floor;

  /** 대상 강의실. 없으면 층 전체 */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(nullable = false, length = 300)
  private String content;

  /** 추천 답변 JSON 배열 문자열. 작성 커밋 후 비동기로 만들고, 30분이 지나면 상세 조회 때 규칙 기반으로 다시 만든다 */
  @Column(columnDefinition = "text")
  private String suggestions;

  @Column(name = "suggestions_generated_at")
  private LocalDateTime suggestionsGeneratedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private TalkStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** 삭제 시각. 삭제한 글은 목록·상세에 나오지 않는다 */
  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 검증(건물·층·강의실·내용)은 서비스에서 끝낸 뒤 호출 */
  public static TalkPost create(Long senderId, Building building, int floor, Room room, String content,
      LocalDateTime now) {
    TalkPost p = new TalkPost();
    p.senderId = senderId;
    p.building = building;
    p.floor = floor;
    p.room = room;
    p.content = content;
    p.status = TalkStatus.UNREAD;
    p.createdAt = now;
    return p;
  }

  public boolean isSentBy(Long memberId) {
    return senderId.equals(memberId);
  }

  /** 답변이 달리면 ANSWERED */
  public void answered() {
    this.status = TalkStatus.ANSWERED;
  }

  /** 남은 답변이 없으면(마지막 답변 삭제) 확인 중으로 되돌린다 */
  public void noRepliesLeft() {
    if (status == TalkStatus.ANSWERED) {
      this.status = TalkStatus.CHECKING;
    }
  }

  public void delete(LocalDateTime now) {
    if (deletedAt == null) {
      this.deletedAt = now;
    }
  }

  /** "6층 · 617호", 강의실이 없으면 "1층". room 은 이미 로딩된 상태여야 한다 */
  public String target() {
    return room == null ? floor + "층" : floor + "층 · " + room.getNumber() + "호";
  }
}
