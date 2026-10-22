package com.app.sumpyo_be.domain.complaint.repository;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {

  /** 같은 신고자가 같은 방에 since 이후 낸 민원이 있는지 (중복 신고 방지) */
  boolean existsByClientHashAndRoomIdAndCreatedAtGreaterThanEqual(String clientHash, Long roomId, LocalDateTime since);

  /** 같은 방 since 이후 민원 수 (반복 민원) */
  long countByRoomIdAndCreatedAtGreaterThanEqual(Long roomId, LocalDateTime since);

  /** 접수함 목록. "북악관 618호" 때문에 방·건물을 같이 가져온다 (N+1 방지) */
  @Override
  @EntityGraph(attributePaths = {"room", "room.building"})
  Page<Complaint> findAll(Specification<Complaint> spec, Pageable pageable);

  @Query("select c from Complaint c join fetch c.room r join fetch r.building where c.id = :id")
  Optional<Complaint> findDetailById(@Param("id") Long id);

  /** [from, to) 방별 민원 수가 min 이상인 방. 행: [roomId, 건수], 많은 순 */
  @Query("""
      select c.room.id, count(c) from Complaint c
      where c.createdAt >= :from and c.createdAt < :to
      group by c.room.id
      having count(c) >= :min
      order by count(c) desc, c.room.id
      """)
  List<Object[]> countByRoomBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
      @Param("min") long min);
}
