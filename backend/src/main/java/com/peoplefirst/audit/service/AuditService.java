package com.peoplefirst.audit.service;

import com.peoplefirst.audit.entity.LeaveAuditLog;
import com.peoplefirst.audit.repository.LeaveAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final LeaveAuditLogRepository leaveAuditLogRepository;

    public AuditService(LeaveAuditLogRepository leaveAuditLogRepository) {
        this.leaveAuditLogRepository = leaveAuditLogRepository;
    }

    @Transactional
    public LeaveAuditLog recordAuditLog(UUID leaveRequestId, UUID actorId, String actorName, String actorRole,
                                       String action, String previousStatus, String newStatus, String comment,
                                       boolean adminOverride, boolean adminDirectEdit) {
        LeaveAuditLog log = new LeaveAuditLog(
                leaveRequestId,
                actorId,
                actorName,
                actorRole,
                action,
                previousStatus,
                newStatus,
                comment,
                adminOverride,
                adminDirectEdit
        );
        return leaveAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<LeaveAuditLog> getAuditLogsForLeave(UUID leaveRequestId) {
        return leaveAuditLogRepository.findByLeaveRequestIdOrderByTimestampDesc(leaveRequestId);
    }

    @Transactional(readOnly = true)
    public List<LeaveAuditLog> getAllAuditLogs() {
        return leaveAuditLogRepository.findAllByOrderByTimestampDesc();
    }
}
