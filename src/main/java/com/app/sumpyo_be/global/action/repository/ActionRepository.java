package com.app.sumpyo_be.global.action.repository;

import com.app.sumpyo_be.global.action.entity.Action;
import com.app.sumpyo_be.global.action.entity.ActionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActionRepository extends JpaRepository<Action, Long> {
  Optional<Action> findTopByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId, Collection<ActionStatus> statuses);
  boolean existsByRoomIdAndStatus(Long roomId, ActionStatus status);

  @Query("select distinct a.room.id from Action a where a.room.id in :roomIds and a.status = :status")
  List<Long> findRoomIdsByStatus(@Param("roomIds") Collection<Long> roomIds, @Param("status") ActionStatus status);
}
