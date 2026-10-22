package com.app.sumpyo_be.domain.outdoor.service;

import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.outdoor.dto.OutdoorLatestResponse;
import com.app.sumpyo_be.domain.outdoor.repository.OutdoorObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutdoorService {

  private final MemberAccess memberAccess;
  private final OutdoorObservationRepository repository;

  /** 가장 최근 관측. 없으면 null */
  public OutdoorLatestResponse latest() {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    return repository.findTopByOrderByObservedAtDesc().map(OutdoorLatestResponse::from).orElse(null);
  }
}
