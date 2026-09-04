package com.peoplefirst.leave.repository;

import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.policy.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    List<LeaveBalance> findByUserIdAndYear(UUID userId, int year);
    List<LeaveBalance> findByUserIdInAndYear(List<UUID> userIds, int year);
    Optional<LeaveBalance> findByUserIdAndLeaveTypeAndYear(UUID userId, LeaveType leaveType, int year);
}
