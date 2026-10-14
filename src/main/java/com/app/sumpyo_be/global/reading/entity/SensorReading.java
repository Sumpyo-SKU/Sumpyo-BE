package com.app.sumpyo_be.global.reading.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import com.app.sumpyo_be.global.reading.dto.SensorPayload;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sensor_reading",
    indexes = @Index(name = "idx_room_time", columnList = "room_id, measured_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false, length = 20)
  private String roomId;

  @Column(nullable = false)
  private Integer co2;

  private Integer temp;       // DHT 실패 시 null
  private Integer humidity;

  @Column(name = "measured_at", nullable = false)
  private LocalDateTime measuredAt;

  public static SensorReading of(String roomId, SensorPayload p, LocalDateTime at) {
    SensorReading r = new SensorReading();
    r.roomId = roomId;
    r.co2 = p.co2();
    r.temp = p.temp();
    r.humidity = p.humidity();
    r.measuredAt = at;
    return r;
  }
}
