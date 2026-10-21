package com.app.sumpyo_be.domain.complaint.service;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.complaint.dto.ComplaintCreateRequest;
import com.app.sumpyo_be.domain.complaint.dto.ComplaintCreateResponse;
import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.domain.notification.service.ComplaintNotifier;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 민원(QR) 접수. 로그인 없는 공개 API */
@Service
@RequiredArgsConstructor
@Transactional
public class ComplaintService {

  static final int MAX_CONTENT_LENGTH = 500;
  /** 같은 신고자 · 같은 방 재제출 제한 */
  static final Duration RESUBMIT_WINDOW = Duration.ofMinutes(10);

  private final ComplaintRepository complaintRepository;
  private final RoomRepository roomRepository;
  private final SensorReadingRepository readingRepository;
  private final ClientHasher clientHasher;
  private final ComplaintNotifier complaintNotifier;
  private final Clock clock;

  /**
   * 민원 저장 (status RECEIVED). 접수 시점 최근 10분 내 측정값을 스냅샷으로 남긴다.
   * 같은 clientId 로 같은 방에 10분 안에 다시 내면 429. 담당 근로생에게 새 민원(·반복 민원) 알림을 바로 만든다.
   */
  public ComplaintCreateResponse create(ComplaintCreateRequest req) {
    if (!clientHasher.isReady()) {
      throw new CustomException(GlobalErrorCode.COMPLAINT_UNAVAILABLE);
    }
    String content = normalizeContent(req.getContent());
    Room room = roomRepository.findWithBuildingById(req.getRoomId())
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ROOM_LOOKUP_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now(clock);
    String clientHash = clientHasher.hash(req.getClientId().trim());
    if (complaintRepository.existsByClientHashAndRoomIdAndCreatedAtGreaterThanEqual(
        clientHash, room.getId(), now.minus(RESUBMIT_WINDOW))) {
      throw new CustomException(GlobalErrorCode.COMPLAINT_TOO_SOON);
    }

    Complaint complaint = Complaint.receive(room, distinct(req.getCategories()), content,
        latestReading(room.getId(), now), clientHash, now);
    complaintRepository.save(complaint);
    complaintNotifier.received(complaint, now);
    return ComplaintCreateResponse.from(complaint);
  }

  /** 앞뒤 공백 제거, 비면 null. 제거 후 500자 초과면 400 */
  static String normalizeContent(String content) {
    if (content == null || content.isBlank()) {
      return null;
    }
    String trimmed = content.strip();
    if (trimmed.length() > MAX_CONTENT_LENGTH) {
      throw new CustomException(GlobalErrorCode.INVALID_COMPLAINT_CONTENT);
    }
    return trimmed;
  }

  /** 중복 제거, 보낸 순서 유지 */
  private static List<ComplaintCategory> distinct(List<ComplaintCategory> categories) {
    return List.copyOf(new LinkedHashSet<>(categories));
  }

  /** 최근 10분 내 최신 측정값, 없으면 null */
  private ReadingSnapshot latestReading(Long roomId, LocalDateTime now) {
    return readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream().findFirst().orElse(null);
  }
}
