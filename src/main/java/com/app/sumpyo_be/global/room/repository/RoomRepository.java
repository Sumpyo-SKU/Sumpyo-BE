package com.app.sumpyo_be.global.room.repository;

import com.app.sumpyo_be.global.room.entity.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
  Optional<Room> findByCode(String code);
}
