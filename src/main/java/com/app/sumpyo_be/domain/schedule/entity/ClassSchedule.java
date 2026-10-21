package com.app.sumpyo_be.domain.schedule.entity;

import com.app.sumpyo_be.domain.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "class_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassSchedule {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Column(name = "course_name", nullable = false, length = 50)
  private String courseName;

  @Column(name = "day_of_week", nullable = false)
  private Integer dayOfWeek;   // 1=월 ~ 7=일 (java.time.DayOfWeek 값과 같음)

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  private Integer enrolled;    // 미정이면 null

  @Column(length = 10)
  private String semester;

  /** 테스트용 */
  public static ClassSchedule of(Long id, String courseName, int dayOfWeek,
      LocalTime startTime, LocalTime endTime, Integer enrolled) {
    return of(id, null, courseName, dayOfWeek, startTime, endTime, enrolled);
  }

  /** 테스트용 */
  public static ClassSchedule of(Long id, Room room, String courseName, int dayOfWeek,
      LocalTime startTime, LocalTime endTime, Integer enrolled) {
    ClassSchedule s = new ClassSchedule();
    s.id = id;
    s.room = room;
    s.courseName = courseName;
    s.dayOfWeek = dayOfWeek;
    s.startTime = startTime;
    s.endTime = endTime;
    s.enrolled = enrolled;
    return s;
  }
}
