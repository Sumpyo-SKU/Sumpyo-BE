package com.app.sumpyo_be.global.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 휴강 */
@Entity
@Table(name = "class_cancellation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassCancellation {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "class_schedule_id", nullable = false)
  private ClassSchedule classSchedule;

  @Column(name = "cancel_date", nullable = false)
  private LocalDate cancelDate;

  @Column(length = 100)
  private String memo;
}
