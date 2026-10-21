package com.app.sumpyo_be.domain.member.repository;

import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);

  /** 이 건물·층이 담당 구역인 회원 (알림 받는 사람) */
  @Query("""
      select m from Member m
      where m.role = :role
        and exists (select 1 from MemberZone z
                    where z.memberId = m.id and z.building.id = :buildingId and z.floor = :floor)
      order by m.id
      """)
  List<Member> findInZone(@Param("buildingId") Long buildingId, @Param("floor") int floor, @Param("role") Role role);
}
