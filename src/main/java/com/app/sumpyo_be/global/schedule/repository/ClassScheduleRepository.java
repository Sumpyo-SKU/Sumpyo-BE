package com.app.sumpyo_be.global.schedule.repository;

import com.app.sumpyo_be.global.schedule.entity.ClassSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Long> {
  // TODO: 학기가 여러 개 쌓이면 semester 조건 추가
  List<ClassSchedule> findByRoomIdAndDayOfWeekOrderByStartTimeAsc(Long roomId, Integer dayOfWeek);
}
