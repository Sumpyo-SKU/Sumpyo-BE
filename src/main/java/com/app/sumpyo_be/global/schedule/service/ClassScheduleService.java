package com.app.sumpyo_be.global.schedule.service;

import com.app.sumpyo_be.global.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.global.schedule.repository.ClassCancellationRepository;
import com.app.sumpyo_be.global.schedule.repository.ClassScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassScheduleService {

  private final ClassScheduleRepository scheduleRepository;
  private final ClassCancellationRepository cancellationRepository;

  public record CurrentAndNext(ClassSchedule current, ClassSchedule next) {}

  /** 해당 날짜(요일)의 수업. 그날 휴강인 수업은 뺀다. 시작 시각 순. */
  public List<ClassSchedule> classesOn(Long roomId, LocalDate date) {
    List<ClassSchedule> schedules = scheduleRepository
        .findByRoomIdAndDayOfWeekOrderByStartTimeAsc(roomId, date.getDayOfWeek().getValue());
    if (schedules.isEmpty()) {
      return schedules;
    }
    Set<Long> cancelled = new HashSet<>(cancellationRepository.findCancelledScheduleIds(
        date, schedules.stream().map(ClassSchedule::getId).toList()));
    return schedules.stream().filter(s -> !cancelled.contains(s.getId())).toList();
  }

  /** current: start ≤ now < end, next: now 이후 처음 시작하는 수업 */
  public static CurrentAndNext currentAndNext(List<ClassSchedule> classes, LocalTime now) {
    ClassSchedule current = null;
    ClassSchedule next = null;
    for (ClassSchedule c : classes) {
      if (!now.isBefore(c.getStartTime()) && now.isBefore(c.getEndTime())) {
        current = c;
      } else if (c.getStartTime().isAfter(now) && (next == null || c.getStartTime().isBefore(next.getStartTime()))) {
        next = c;
      }
    }
    return new CurrentAndNext(current, next);
  }
}
