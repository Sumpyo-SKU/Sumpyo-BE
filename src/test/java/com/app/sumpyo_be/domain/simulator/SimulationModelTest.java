package com.app.sumpyo_be.domain.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.repository.ClassCancellationRepository;
import com.app.sumpyo_be.domain.schedule.repository.ClassScheduleRepository;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.simulator.SimulationModel.Conditions;
import com.app.sumpyo_be.domain.simulator.SimulationModel.State;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SimulationModelTest {

  private static final long ROOM_ID = 12L;
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
  private static final ClassSchedule CLASS_15 = ClassSchedule.of(1L, "자연어처리", 1,
      LocalTime.of(15, 0), LocalTime.of(16, 15), null);

  private static LocalDateTime at(int hour, int minute) {
    return MONDAY.atTime(hour, minute);
  }

  /** start 부터 minutes 분 동안 1분씩 진행 */
  private static State run(State start, LocalDateTime from, int minutes, List<ClassSchedule> classes,
      boolean ventilating) {
    State s = start;
    for (int i = 0; i < minutes; i++) {
      s = SimulationModel.next(s, SimulationModel.conditions(ROOM_ID, classes, from.plusMinutes(i), ventilating));
    }
    return s;
  }

  @Nested
  class Co2 {

    @Test
    void 수업_중에는_CO2가_오르고_75분_수업이면_기준을_넘는다() {
      State start = new State(450, 23, 45);
      State after30 = run(start, at(15, 0), 30, List.of(CLASS_15), false);
      State after75 = run(start, at(15, 0), 75, List.of(CLASS_15), false);

      assertThat(after30.co2()).isGreaterThan(start.co2());
      assertThat(after75.co2()).isGreaterThan(after30.co2()).isGreaterThan(1000);
    }

    @Test
    void 수업이_끝나면_CO2가_내려간다() {
      State endOfClass = run(new State(450, 23, 45), at(15, 0), 75, List.of(CLASS_15), false);
      State after30 = run(endOfClass, at(16, 15), 30, List.of(CLASS_15), false);
      State after60 = run(endOfClass, at(16, 15), 60, List.of(CLASS_15), false);

      assertThat(after30.co2()).isLessThan(endOfClass.co2());
      assertThat(after60.co2()).isLessThan(after30.co2());
    }

    @Test
    void 환기_중이면_더_빨리_내려간다() {
      State high = new State(1400, 24, 50);
      State noVent = run(high, at(16, 30), 15, List.of(), false);
      State vent = run(high, at(16, 30), 15, List.of(), true);

      assertThat(vent.co2()).isLessThan(noVent.co2());
      // 15분 환기(ACH 6)면 초과분(980ppm)의 3/4 이상이 빠진다
      assertThat(vent.co2()).isLessThan(420 + 980 * 0.25);
    }

    @Test
    void 수업_중이라도_환기하면_내려간다() {
      State high = new State(1400, 24, 50);
      State vent = run(high, at(15, 0), 20, List.of(CLASS_15), true);
      assertThat(vent.co2()).isLessThan(high.co2());
    }

    @Test
    void 휴강일엔_수업_시간이어도_오르지_않는다() {
      // 휴강 수업은 ClassScheduleService 에서 빠져서 빈 목록이 넘어온다
      ClassScheduleRepository scheduleRepository = mock(ClassScheduleRepository.class);
      ClassCancellationRepository cancellationRepository = mock(ClassCancellationRepository.class);
      Room room = mock(Room.class);
      when(room.getId()).thenReturn(ROOM_ID);
      ClassSchedule cancelledClass = ClassSchedule.of(1L, room, "자연어처리", 1,
          LocalTime.of(15, 0), LocalTime.of(16, 15), null);
      when(scheduleRepository.findByRoomIdInAndDayOfWeek(anyCollection(), eq(1))).thenReturn(List.of(cancelledClass));
      when(cancellationRepository.findCancelledScheduleIds(eq(MONDAY), anyCollection())).thenReturn(List.of(1L));

      List<ClassSchedule> today = new ClassScheduleService(scheduleRepository, cancellationRepository)
          .classesOn(List.of(ROOM_ID), MONDAY)
          .getOrDefault(ROOM_ID, List.of());
      assertThat(today).isEmpty();

      State start = new State(450, 23, 45);
      State after = run(start, at(15, 0), 75, today, false);
      assertThat(after.co2()).isLessThanOrEqualTo(start.co2());
      assertThat(SimulationModel.conditions(ROOM_ID, today, at(15, 30), false).people()).isZero();
    }

    @Test
    void 최소_400ppm() {
      State low = run(new State(400, 23, 45), at(3, 0), 60, List.of(), true);
      assertThat(low.co2()).isGreaterThanOrEqualTo(400);
    }
  }

  @Nested
  class Conditions_ {

    @Test
    void ACH는_환기_수업_낮_밤_순으로_정한다() {
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(CLASS_15), at(15, 30), true).ach()).isEqualTo(6.0);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(CLASS_15), at(15, 30), false).ach()).isEqualTo(2.0);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(CLASS_15), at(16, 15), false).ach()).isEqualTo(0.8);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(), at(8, 0), false).ach()).isEqualTo(0.8);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(), at(19, 0), false).ach()).isEqualTo(0.3);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(), at(2, 0), false).ach()).isEqualTo(0.3);
    }

    @Test
    void 수업_중_인원은_30명_곱하기_방_날짜별_출석률() {
      Conditions c = SimulationModel.conditions(ROOM_ID, List.of(CLASS_15), at(15, 30), false);
      double attendance = SimulationModel.attendance(ROOM_ID, MONDAY);
      assertThat(attendance).isBetween(0.7, 0.9);
      assertThat(c.people()).isEqualTo(30 * attendance);
      // 같은 방·같은 날은 항상 같은 출석률
      assertThat(SimulationModel.attendance(ROOM_ID, MONDAY)).isEqualTo(attendance);
      assertThat(SimulationModel.conditions(ROOM_ID, List.of(CLASS_15), at(14, 59), false).people()).isZero();
    }
  }

  @Nested
  class TempHumidityAndNoise {

    @Test
    void 수업_중_온도는_시간당_1도씩_최대_2도_습도는_3씩_최대_6() {
      State start = new State(450, 23, 45);
      State after60 = run(start, at(9, 0), 60, List.of(ClassSchedule.of(2L, "1교시", 1,
          LocalTime.of(9, 0), LocalTime.of(12, 0), null)), false);
      State after180 = run(start, at(9, 0), 180, List.of(ClassSchedule.of(2L, "1교시", 1,
          LocalTime.of(9, 0), LocalTime.of(12, 0), null)), false);

      assertThat(after60.temp()).isCloseTo(24, org.assertj.core.data.Offset.offset(0.01));
      assertThat(after60.humidity()).isCloseTo(48, org.assertj.core.data.Offset.offset(0.01));
      assertThat(after180.temp()).isEqualTo(25);
      assertThat(after180.humidity()).isEqualTo(51);
    }

    @Test
    void 환기_중_온도는_서서히_내려간다() {
      State warm = new State(1200, 25, 51);
      State after10 = run(warm, at(16, 30), 10, List.of(), true);
      assertThat(after10.temp()).isLessThan(25).isGreaterThan(24);
      assertThat(after10.humidity()).isLessThan(51);
    }

    @Test
    void 저장값은_정수이고_노이즈는_12ppm_이내() {
      SplittableRandom random = new SplittableRandom(1);
      State s = new State(800.4, 23.6, 47.4);
      for (int i = 0; i < 500; i++) {
        int[] v = SimulationModel.observe(s, random);
        assertThat(v[0]).isBetween(788, 812);
        assertThat(v[1]).isEqualTo(24);
        assertThat(v[2]).isEqualTo(47);
      }
    }

    @Test
    void 직전_값이_없으면_430에서_470_사이로_시작() {
      SplittableRandom random = new SplittableRandom(7);
      for (int i = 0; i < 200; i++) {
        assertThat(SimulationModel.initial(random).co2()).isBetween(430.0, 470.0);
      }
    }
  }
}
