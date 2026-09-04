package com.peoplefirst.leave.controller;

import com.peoplefirst.audit.entity.LeaveAuditLog;
import com.peoplefirst.audit.service.AuditService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.AdminDirectEditDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLeaveController {

    private final LeaveService leaveService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public AdminLeaveController(LeaveService leaveService,
                                AuditService auditService,
                                CurrentUserProvider currentUserProvider) {
        this.leaveService = leaveService;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/leaves")
    public ResponseEntity<List<LeaveResponseDto>> getAllLeaves() {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can view org-wide leaves.");
        }
        return ResponseEntity.ok(leaveService.getAllLeavesOrgWide());
    }

    @PutMapping("/leaves/{id}/direct-edit")
    public ResponseEntity<LeaveResponseDto> directEditLeave(@PathVariable UUID id,
                                                           @Valid @RequestBody AdminDirectEditDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto result = leaveService.adminDirectEdit(id, dto, currentUser);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<LeaveAuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(auditService.getAllAuditLogs());
    }

    @GetMapping("/leaves/{id}/audit-logs")
    public ResponseEntity<List<LeaveAuditLog>> getAuditLogsForLeave(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.getAuditLogsForLeave(id));
    }
}
