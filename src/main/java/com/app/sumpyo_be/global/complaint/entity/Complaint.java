package com.app.sumpyo_be.global.complaint.entity;

import com.app.sumpyo_be.global.room.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 민원 */
@Entity
@Table(name = "complaint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Complaint {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id", nullable = false)
  private Room room;

  @Column(nullable = false, length = 100)
  private String categories;   // STUFFY,HOT,COLD,HUMID,SMELL,BROKEN 쉼표 구분

  @Column(length = 500)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private ComplaintStatus status;

  @Column(name = "co2_snapshot")
  private Integer co2Snapshot;

  @Column(name = "temp_snapshot", precision = 4, scale = 1)
  private BigDecimal tempSnapshot;

  @Column(name = "humidity_snapshot")
  private Integer humiditySnapshot;

  @Column(name = "client_hash", length = 64)
  private String clientHash;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** 연결된 조치가 완료되면 민원도 완료 */
  public void done() {
    this.status = ComplaintStatus.DONE;
  }
}
