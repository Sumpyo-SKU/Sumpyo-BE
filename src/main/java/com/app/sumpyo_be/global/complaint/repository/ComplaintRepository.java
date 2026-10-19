package com.app.sumpyo_be.global.complaint.repository;

import com.app.sumpyo_be.global.complaint.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
}
