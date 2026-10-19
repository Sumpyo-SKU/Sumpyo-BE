package com.app.sumpyo_be.global.member.repository;

import com.app.sumpyo_be.global.member.entity.MemberZone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberZoneRepository extends JpaRepository<MemberZone, Long> {

  @Query("""
      select z from MemberZone z join fetch z.building b
      where z.memberId = :memberId
      order by b.displayOrder, z.floor
      """)
  List<MemberZone> findWithBuildingByMemberId(@Param("memberId") Long memberId);

  /**
   * 통째 교체용 일괄 삭제. 바로 DB 에 실행된다.
   * (deleteAll + saveAll 로 하면 Hibernate 가 INSERT 를 DELETE 보다 먼저 보내 UNIQUE 제약에 걸린다)
   */
  @Modifying(clearAutomatically = true)
  @Query("delete from MemberZone z where z.memberId = :memberId")
  void deleteByMemberId(@Param("memberId") Long memberId);
}
