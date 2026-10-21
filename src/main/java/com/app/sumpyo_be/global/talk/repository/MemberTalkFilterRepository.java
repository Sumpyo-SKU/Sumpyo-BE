package com.app.sumpyo_be.global.talk.repository;

import com.app.sumpyo_be.global.talk.entity.MemberTalkFilter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberTalkFilterRepository extends JpaRepository<MemberTalkFilter, Long> {

  @Query("""
      select f from MemberTalkFilter f left join fetch f.room r left join fetch r.building
      where f.memberId = :memberId
      order by f.floor, r.number
      """)
  List<MemberTalkFilter> findWithRoomByMemberId(@Param("memberId") Long memberId);

  /** 통째 교체용 일괄 삭제 (deleteAll + saveAll 은 INSERT 가 먼저 나가 UNIQUE 에 걸린다) */
  @Modifying(clearAutomatically = true)
  @Query("delete from MemberTalkFilter f where f.memberId = :memberId")
  void deleteByMemberId(@Param("memberId") Long memberId);
}
