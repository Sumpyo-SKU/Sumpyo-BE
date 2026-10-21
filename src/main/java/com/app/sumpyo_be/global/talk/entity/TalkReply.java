package com.app.sumpyo_be.global.talk.entity;

import com.app.sumpyo_be.global.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 캠퍼스톡 답변 */
@Entity
@Table(name = "talk_reply")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TalkReply {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private TalkPost post;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(nullable = false, length = 300)
  private String content;

  /** 추천 답변 칩을 골라 보냈는지 */
  @Column(name = "is_suggested", nullable = false)
  private boolean suggested;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public static TalkReply create(TalkPost post, Member member, String content, boolean suggested,
      LocalDateTime now) {
    TalkReply r = new TalkReply();
    r.post = post;
    r.member = member;
    r.content = content;
    r.suggested = suggested;
    r.createdAt = now;
    return r;
  }
}
