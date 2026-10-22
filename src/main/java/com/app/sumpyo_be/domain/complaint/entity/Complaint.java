package com.app.sumpyo_be.domain.complaint.entity;

import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.room.entity.Room;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

  /**
   * 학생 민원 접수 (status RECEIVED).
   * @param categories 중복 없이 1개 이상
   * @param snapshot   접수 시점 최근 10분 내 측정값, 없으면 null
   * @param clientHash SHA-256(clientId + salt). 원래 clientId 는 저장하지 않는다
   */
  public static Complaint receive(Room room, List<ComplaintCategory> categories, String content,
      ReadingSnapshot snapshot, String clientHash, LocalDateTime now) {
    Complaint c = new Complaint();
    c.room = room;
    c.categories = categories.stream().map(Enum::name).collect(Collectors.joining(","));
    c.content = content;
    c.status = ComplaintStatus.RECEIVED;
    if (snapshot != null) {
      c.co2Snapshot = snapshot.getCo2();
      c.tempSnapshot = snapshot.getTemp() == null ? null : BigDecimal.valueOf(snapshot.getTemp());
      c.humiditySnapshot = snapshot.getHumidity();
    }
    c.clientHash = clientHash;
    c.createdAt = now;
    return c;
  }

  /** 저장된 순서대로 */
  public List<ComplaintCategory> getCategoryList() {
    return Arrays.stream(categories.split(",")).map(ComplaintCategory::valueOf).toList();
  }

  /** 연결된 조치를 시작하면 처리 중 */
  public void startProcessing() {
    if (status == ComplaintStatus.RECEIVED) {
      this.status = ComplaintStatus.IN_PROGRESS;
    }
  }

  /** 연결된 조치가 완료되면 민원도 완료 */
  public void done() {
    this.status = ComplaintStatus.DONE;
  }
}
