package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.domain.member.entity.Member;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 민원 접수(POST /api/complaints) 안에서 바로 새 민원·반복 민원 알림을 만든다. 받는 사람: notifyComplaint */
@Service
@RequiredArgsConstructor
public class ComplaintNotifier {

  /** 최근 7일 이 건수 이상이면 반복 민원 */
  static final int REPEAT_COUNT = 5;
  static final Duration REPEAT_WINDOW = Duration.ofDays(7);

  private final NotificationPublisher publisher;
  private final ComplaintRepository complaintRepository;

  /** complaint 는 저장된 뒤여야 하고 room.building 이 로딩돼 있어야 한다 */
  public void received(Complaint complaint, LocalDateTime now) {
    List<Member> to = publisher.recipients(complaint.getRoom()).stream().filter(Member::isNotifyComplaint).toList();
    if (to.isEmpty()) {
      return;
    }
    NotificationDraft created = NotificationDraft.newComplaint(complaint);
    to.forEach(m -> publisher.publish(m, created, now));

    long count = complaintRepository.countByRoomIdAndCreatedAtGreaterThanEqual(
        complaint.getRoom().getId(), now.minus(REPEAT_WINDOW));
    if (count >= REPEAT_COUNT) {
      NotificationDraft repeated = NotificationDraft.repeatedComplaint(complaint, count, now);
      to.forEach(m -> publisher.publish(m, repeated, now));   // 같은 주에는 키가 같아 한 번만
    }
  }
}
