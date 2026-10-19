package com.app.sumpyo_be.global.building.repository;

import com.app.sumpyo_be.global.building.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository extends JpaRepository<Building, Long> {
}
