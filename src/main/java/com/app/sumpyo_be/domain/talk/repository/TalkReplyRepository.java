package com.app.sumpyo_be.domain.talk.repository;

import com.app.sumpyo_be.domain.talk.entity.TalkReply;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TalkReplyRepository extends JpaRepository<TalkReply, Long> {

  @Query("select r from TalkReply r join fetch r.member where r.post.id = :postId order by r.createdAt, r.id")
  List<TalkReply> findWithMemberByPostId(@Param("postId") Long postId);

  @Query("select r from TalkReply r join fetch r.member where r.id = :id and r.post.id = :postId")
  Optional<TalkReply> findInPost(@Param("id") Long id, @Param("postId") Long postId);

  /** 글쓴이가 아닌 근로생의 (삭제 안 한) 답변이 남아 있는지 → 있으면 ANSWERED */
  boolean existsByPostIdAndDeletedAtIsNullAndMemberIdNot(Long postId, Long senderId);
}
