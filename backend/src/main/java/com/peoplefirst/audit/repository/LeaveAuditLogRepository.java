package com.peoplefirst.audit.repository;

import com.peoplefirst.audit.entity.LeaveAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveAuditLogRepository extends JpaRepository<LeaveAuditLog, UUID> {
    List<LeaveAuditLog> findByLeaveRequestIdOrderByTimestampDesc(UUID leaveRequestId);
    List<LeaveAuditLog> findAllByOrderByTimestampDesc();
}
