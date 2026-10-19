package com.app.sumpyo_be.global.schedule.repository;

import com.app.sumpyo_be.global.schedule.entity.ClassCancellation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassCancellationRepository extends JpaRepository<ClassCancellation, Long> {

  /** 주어진 시간표 중 해당 날짜에 휴강인 시간표 ID */
  @Query("""
      select c.classSchedule.id from ClassCancellation c
      where c.cancelDate = :date and c.classSchedule.id in :scheduleIds
      """)
  List<Long> findCancelledScheduleIds(@Param("date") LocalDate date,
      @Param("scheduleIds") Collection<Long> scheduleIds);
}
