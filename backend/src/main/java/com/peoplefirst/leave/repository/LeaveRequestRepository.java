package com.peoplefirst.leave.repository;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<LeaveRequest> findByUserIdInOrderByCreatedAtDesc(List<UUID> userIds);
    List<LeaveRequest> findByUserIdAndStatus(UUID userId, LeaveStatus status);
    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
    List<LeaveRequest> findByUserIdAndStartDateAfter(UUID userId, LocalDate date);
}
