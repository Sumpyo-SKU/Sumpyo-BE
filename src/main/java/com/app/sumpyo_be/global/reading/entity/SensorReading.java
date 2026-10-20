package com.app.sumpyo_be.global.reading.entity;

import com.app.sumpyo_be.global.reading.dto.SensorPayload;
import com.app.sumpyo_be.global.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sensor_reading")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  private Integer co2;
  private Integer temp;       // DHT 실패 시 null
  private Integer humidity;

  @Column(name = "measured_at", nullable = false)
  private LocalDateTime measuredAt;

  public static SensorReading of(Room room, SensorPayload p, LocalDateTime at) {
    SensorReading r = new SensorReading();
    r.room = room;
    r.co2 = p.getCo2();
    r.temp = p.getTemp();
    r.humidity = p.getHumidity();
    r.measuredAt = at;
    return r;
  }
}
