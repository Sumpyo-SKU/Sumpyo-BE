package com.app.sumpyo_be.global.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "login_id", nullable = false, length = 50, unique = true)
  private String loginId;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false, length = 30)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(name = "co2_threshold", nullable = false)
  private Integer co2Threshold;

  @Column(name = "temp_min")
  private Integer tempMin;

  @Column(name = "temp_max")
  private Integer tempMax;

  @Column(name = "humidity_min")
  private Integer humidityMin;

  @Column(name = "humidity_max")
  private Integer humidityMax;

  @Column(name = "alert_enabled")
  private Boolean alertEnabled;

  @Column(name = "alert_lead_minutes")
  private Integer alertLeadMinutes;
}
