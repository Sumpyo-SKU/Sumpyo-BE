package com.app.sumpyo_be.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.repository.ClassCancellationRepository;
import com.app.sumpyo_be.domain.schedule.repository.ClassScheduleRepository;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService.CurrentAndNext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassScheduleServiceTest {

  private static final long ROOM_ID = 7L;
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);   // V2 시드의 휴강일 (월)

  @Mock ClassScheduleRepository scheduleRepository;
  @Mock ClassCancellationRepository cancellationRepository;
  @InjectMocks ClassScheduleService service;

  private final ClassSchedule nlp1 = ClassSchedule.of(1L, "자연어처리", 1, LocalTime.of(15, 0), LocalTime.of(16, 15), null);
  private final ClassSchedule nlp2 = ClassSchedule.of(2L, "자연어처리", 1, LocalTime.of(16, 30), LocalTime.of(17, 45), null);
  private final ClassSchedule ds = ClassSchedule.of(3L, "자료구조", 1, LocalTime.of(12, 0), LocalTime.of(13, 15), 42);

  @Test
  void 휴강인_수업은_목록에서_빠진다() {
    when(scheduleRepository.findByRoomIdAndDayOfWeekOrderByStartTimeAsc(ROOM_ID, 1))
        .thenReturn(List.of(ds, nlp1, nlp2));
    when(cancellationRepository.findCancelledScheduleIds(eq(MONDAY), anyCollection()))
        .thenReturn(List.of(1L, 2L));

    assertThat(service.classesOn(ROOM_ID, MONDAY)).containsExactly(ds);
  }

  @Test
  void 휴강이_없으면_그대로() {
    when(scheduleRepository.findByRoomIdAndDayOfWeekOrderByStartTimeAsc(ROOM_ID, 1))
        .thenReturn(List.of(ds, nlp1));
    when(cancellationRepository.findCancelledScheduleIds(eq(MONDAY), anyCollection()))
        .thenReturn(List.of());

    assertThat(service.classesOn(ROOM_ID, MONDAY)).containsExactly(ds, nlp1);
  }

  @Test
  void 수업이_없는_날은_휴강_조회를_하지_않는다() {
    LocalDate sunday = MONDAY.minusDays(1);
    when(scheduleRepository.findByRoomIdAndDayOfWeekOrderByStartTimeAsc(ROOM_ID, 7)).thenReturn(List.of());

    assertThat(service.classesOn(ROOM_ID, sunday)).isEmpty();
    verify(cancellationRepository, never()).findCancelledScheduleIds(any(), anyCollection());
  }

  @Test
  void 휴강을_뺀_뒤의_다음_수업을_고른다() {
    // 15:00 자연어처리 휴강 → 14:00 기준 현재 수업 없음, 다음 수업은 16:30 (2교시도 휴강이면 없음)
    CurrentAndNext cn = ClassScheduleService.currentAndNext(List.of(ds), LocalTime.of(14, 0));
    assertThat(cn.current()).isNull();
    assertThat(cn.next()).isNull();
  }

  @Test
  void 현재_수업과_다음_수업() {
    List<ClassSchedule> today = List.of(ds, nlp1, nlp2);
    CurrentAndNext during = ClassScheduleService.currentAndNext(today, LocalTime.of(12, 30));
    assertThat(during.current()).isEqualTo(ds);
    assertThat(during.next()).isEqualTo(nlp1);

    // 끝나는 시각은 현재 수업에 포함하지 않는다
    CurrentAndNext atEnd = ClassScheduleService.currentAndNext(today, LocalTime.of(16, 15));
    assertThat(atEnd.current()).isNull();
    assertThat(atEnd.next()).isEqualTo(nlp2);
  }
}
