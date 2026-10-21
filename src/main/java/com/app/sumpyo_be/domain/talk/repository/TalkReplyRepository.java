package com.app.sumpyo_be.domain.talk.repository;

import com.app.sumpyo_be.domain.talk.entity.TalkReply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TalkReplyRepository extends JpaRepository<TalkReply, Long> {

  @Query("select r from TalkReply r join fetch r.member where r.post.id = :postId order by r.createdAt, r.id")
  List<TalkReply> findWithMemberByPostId(@Param("postId") Long postId);
}
