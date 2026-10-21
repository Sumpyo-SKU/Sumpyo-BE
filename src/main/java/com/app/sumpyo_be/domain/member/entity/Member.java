package com.app.sumpyo_be.domain.member.entity;

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

  /** JPA 는 null 도 INSERT 에 넣어 DB DEFAULT 가 안 먹으므로 기본값을 여기서 채운다 (V1 DEFAULT 와 같은 값) */
  public static Member create(String loginId, String encodedPassword, String name, Role role) {
    Member m = new Member();
    m.loginId = loginId;
    m.password = encodedPassword;
    m.name = name;
    m.role = role;
    m.co2Threshold = 1000;
    m.tempMin = 20;
    m.tempMax = 26;
    m.humidityMin = 40;
    m.humidityMax = 60;
    m.alertEnabled = true;
    m.alertLeadMinutes = 15;
    return m;
  }

  /** null 인 값은 그대로 둔다 (부분 수정). 검증은 서비스에서 끝낸 뒤 호출 */
  public void updateSettings(Integer co2Threshold, Integer tempMin, Integer tempMax,
      Integer humidityMin, Integer humidityMax, Boolean alertEnabled, Integer alertLeadMinutes) {
    if (co2Threshold != null) this.co2Threshold = co2Threshold;
    if (tempMin != null) this.tempMin = tempMin;
    if (tempMax != null) this.tempMax = tempMax;
    if (humidityMin != null) this.humidityMin = humidityMin;
    if (humidityMax != null) this.humidityMax = humidityMax;
    if (alertEnabled != null) this.alertEnabled = alertEnabled;
    if (alertLeadMinutes != null) this.alertLeadMinutes = alertLeadMinutes;
  }
}
