package com.app.sumpyo_be.domain.talk.repository;

import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.entity.TalkStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface TalkPostRepository extends JpaRepository<TalkPost, Long>, JpaSpecificationExecutor<TalkPost> {

  /** 목록. target("6층 · 617호") 때문에 room 을 같이 가져온다 (N+1 방지) */
  @Override
  @EntityGraph(attributePaths = "room")
  Page<TalkPost> findAll(Specification<TalkPost> spec, Pageable pageable);

  @Query("select p from TalkPost p join fetch p.building left join fetch p.room where p.id = :id")
  Optional<TalkPost> findDetailById(@Param("id") Long id);

  /** UNREAD 일 때만 바꾼다 (동시에 열어도 한 번만) */
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query("update TalkPost p set p.status = :to where p.id = :id and p.status = :from")
  int changeStatus(@Param("id") Long id, @Param("from") TalkStatus from, @Param("to") TalkStatus to);

  /**
   * 추천 답변과 생성 시각을 덮어쓴다 (비동기 생성 결과, 30분 지난 추천 재생성).
   * AFTER_COMMIT 리스너에서는 끝난 트랜잭션이 아직 묶여 있어 REQUIRED 로는 참여할 트랜잭션이 없으므로 새로 연다
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Modifying(clearAutomatically = true)
  @Query("update TalkPost p set p.suggestions = :suggestions, p.suggestionsGeneratedAt = :at where p.id = :id")
  int saveSuggestions(@Param("id") Long id, @Param("suggestions") String suggestions, @Param("at") LocalDateTime at);
}
