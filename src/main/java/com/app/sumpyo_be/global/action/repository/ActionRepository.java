package com.app.sumpyo_be.global.action.repository;

import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, Long> {
  Optional<Action> findTopByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId, Collection<ActionStatus> statuses);
  boolean existsByRoomIdAndStatus(Long roomId, ActionStatus status);
}
