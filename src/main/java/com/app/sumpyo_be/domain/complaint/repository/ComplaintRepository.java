package com.app.sumpyo_be.domain.complaint.repository;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

  /** 같은 신고자가 같은 방에 since 이후 낸 민원이 있는지 (중복 신고 방지) */
  boolean existsByClientHashAndRoomIdAndCreatedAtGreaterThanEqual(String clientHash, Long roomId, LocalDateTime since);

  /** 같은 방 since 이후 민원 수 (반복 민원) */
  long countByRoomIdAndCreatedAtGreaterThanEqual(Long roomId, LocalDateTime since);
}
