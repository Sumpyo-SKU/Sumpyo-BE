package com.app.sumpyo_be.domain.notification.service;

import com.app.sumpyo_be.domain.member.entity.Member;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.repository.MemberRepository;
import com.app.sumpyo_be.domain.notification.repository.NotificationRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 받는 사람 찾기 + 저장 (dedup_key 로 멱등). 설정(notify* 등) 확인은 부르는 쪽에서 */
@Component
@RequiredArgsConstructor
class NotificationPublisher {

  private final MemberRepository memberRepository;
  private final NotificationRepository notificationRepository;

  /** 그 방의 건물·층이 담당 구역인 근로생. room.building 은 로딩돼 있어야 한다 */
  List<Member> recipients(Room room) {
    return memberRepository.findInZone(room.getBuilding().getId(), room.getFloor(), Role.WORKER);
  }

  /** @return 새로 만들었으면 true, 이미 있으면 false */
  boolean publish(Member to, NotificationDraft d, LocalDateTime now) {
    return notificationRepository.insertIfAbsent(to.getId(), d.type().name(), d.roomId(), d.actionId(),
        d.complaintId(), d.title(), d.body(), d.dedupKey(), now) == 1;
  }
}
