package com.app.sumpyo_be.global.simulator;

import com.app.sumpyo_be.global.schedule.entity.ClassSchedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * 강의실 공기질 시뮬레이션 식 (1분 단위). DB·Spring 에 의존하지 않는 순수 함수.
 *
 * CO2: C_next = C + 사람수 × 1.6 − (ACH / 60) × (C − 420)
 *   - 사람수: 수업 중이면 30 × 출석률(방·날짜별 0.7~0.9 고정), 아니면 0. 휴강 수업은 호출 쪽에서 미리 뺀다
 *   - ACH(시간당 환기 횟수): 환기 중 6.0 > 수업 중 2.0 > 낮(08~19시) 빈 방 0.8 > 밤 0.3
 *   예) 24명 수업이면 정상상태 420 + 24×1.6×60/2 ≈ 1570ppm (75분 수업 끝 무렵 ≈ 1480 → 기준 초과),
 *       같은 수업 중 환기하면 정상상태 ≈ 800ppm 으로 떨어진다.
 * 온도: 23℃ 기준, 수업 중 시간당 +1℃ (최대 +2), 환기 중 시간당 −1.5℃ (최저 21), 그 외 시간당 1℃씩 23으로 복귀
 * 습도: 45% 기준, 수업 중 시간당 +3%p (최대 +6), 그 외 시간당 3%p(환기 중 6%p)씩 45로 복귀
 *
 * 내부 상태는 소수로 이어가고(정수 반올림 누적 오차 방지), 저장할 때만 노이즈 ±12ppm 을 더해 정수로 만든다.
 */
public final class SimulationModel {

  private SimulationModel() {}

  static final double OUTDOOR_CO2 = 420;
  static final double CO2_PER_PERSON_PER_MIN = 1.6;
  static final int CLASS_SIZE = 30;
  static final double ATTENDANCE_MIN = 0.7;
  static final double ATTENDANCE_MAX = 0.9;

  static final double ACH_VENTILATING = 6.0;
  static final double ACH_IN_CLASS = 2.0;
  static final double ACH_DAY_EMPTY = 0.8;
  static final double ACH_NIGHT = 0.3;
  static final LocalTime DAY_START = LocalTime.of(8, 0);
  static final LocalTime DAY_END = LocalTime.of(19, 0);

  static final int NOISE_PPM = 12;
  static final int MIN_CO2 = 400;
  static final int INITIAL_CO2_MIN = 430;
  static final int INITIAL_CO2_MAX = 470;

  static final double BASE_TEMP = 23;
  static final double TEMP_MAX_RISE = 2;
  static final double TEMP_RISE_PER_MIN = 1.0 / 60;
  static final double TEMP_VENT_DROP_PER_MIN = 1.5 / 60;
  static final double TEMP_VENT_MIN = 21;
  static final double TEMP_RELAX_PER_MIN = 1.0 / 60;

  static final double BASE_HUMIDITY = 45;
  static final double HUMIDITY_MAX_RISE = 6;
  static final double HUMIDITY_RISE_PER_MIN = 3.0 / 60;
  static final double HUMIDITY_RELAX_PER_MIN = 3.0 / 60;
  static final double HUMIDITY_VENT_RELAX_PER_MIN = 6.0 / 60;

  /** 시뮬레이션 내부 상태 (소수) */
  public record State(double co2, double temp, double humidity) {}

  /** 이번 1분 동안의 조건 */
  public record Conditions(double people, double ach, boolean inClass, boolean ventilating) {}

  /** 방·날짜별 출석률 0.7~0.9. 같은 날 같은 방은 항상 같은 값 */
  public static double attendance(long roomId, LocalDate date) {
    return new SplittableRandom(roomId * 1_000_003L + date.toEpochDay())
        .nextDouble(ATTENDANCE_MIN, ATTENDANCE_MAX);
  }

  /** @param todayClasses 오늘 수업 (휴강 제외) */
  public static Conditions conditions(long roomId, List<ClassSchedule> todayClasses, LocalDateTime now,
      boolean ventilating) {
    LocalTime t = now.toLocalTime();
    boolean inClass = todayClasses.stream()
        .anyMatch(c -> !t.isBefore(c.getStartTime()) && t.isBefore(c.getEndTime()));
    double people = inClass ? CLASS_SIZE * attendance(roomId, now.toLocalDate()) : 0;
    double ach;
    if (ventilating) {
      ach = ACH_VENTILATING;
    } else if (inClass) {
      ach = ACH_IN_CLASS;
    } else if (!t.isBefore(DAY_START) && t.isBefore(DAY_END)) {
      ach = ACH_DAY_EMPTY;
    } else {
      ach = ACH_NIGHT;
    }
    return new Conditions(people, ach, inClass, ventilating);
  }

  /** 직전 값이 없을 때 시작 상태 */
  public static State initial(RandomGenerator random) {
    return new State(random.nextInt(INITIAL_CO2_MIN, INITIAL_CO2_MAX + 1), BASE_TEMP, BASE_HUMIDITY);
  }

  public static State next(State s, Conditions c) {
    double co2 = s.co2() + c.people() * CO2_PER_PERSON_PER_MIN - (c.ach() / 60) * (s.co2() - OUTDOOR_CO2);
    co2 = Math.max(MIN_CO2, co2);

    double temp;
    if (c.ventilating()) {
      temp = Math.max(TEMP_VENT_MIN, s.temp() - TEMP_VENT_DROP_PER_MIN);
    } else if (c.inClass()) {
      temp = Math.min(BASE_TEMP + TEMP_MAX_RISE, s.temp() + TEMP_RISE_PER_MIN);
    } else {
      temp = toward(s.temp(), BASE_TEMP, TEMP_RELAX_PER_MIN);
    }

    double humidity;
    if (c.inClass() && !c.ventilating()) {
      humidity = Math.min(BASE_HUMIDITY + HUMIDITY_MAX_RISE, s.humidity() + HUMIDITY_RISE_PER_MIN);
    } else {
      humidity = toward(s.humidity(), BASE_HUMIDITY,
          c.ventilating() ? HUMIDITY_VENT_RELAX_PER_MIN : HUMIDITY_RELAX_PER_MIN);
    }
    return new State(co2, temp, humidity);
  }

  /** 저장할 측정값: [co2, temp, humidity] 정수, CO2 에만 노이즈 */
  public static int[] observe(State s, RandomGenerator random) {
    int co2 = (int) Math.round(s.co2() + random.nextInt(-NOISE_PPM, NOISE_PPM + 1));
    return new int[] {Math.max(MIN_CO2, co2), (int) Math.round(s.temp()), (int) Math.round(s.humidity())};
  }

  private static double toward(double value, double target, double step) {
    return value > target ? Math.max(target, value - step) : Math.min(target, value + step);
  }
}
