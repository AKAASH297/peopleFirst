package com.peoplefirst.approval.controller;

import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.service.ApprovalService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final CurrentUserProvider currentUserProvider;

    public ApprovalController(ApprovalService approvalService, CurrentUserProvider currentUserProvider) {
        this.approvalService = approvalService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/approvals/pending")
    public ResponseEntity<List<LeaveResponseDto>> getPendingApprovals() {
        User currentUser = currentUserProvider.getCurrentUser();
        List<LeaveResponseDto> pending = approvalService.getPendingApprovals(currentUser);
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<LeaveResponseDto> approveLeave(@PathVariable UUID id,
                                                         @RequestBody(required = false) ApprovalActionDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = approvalService.approveLeave(id, dto, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<LeaveResponseDto> rejectLeave(@PathVariable UUID id,
                                                        @RequestBody(required = false) ApprovalActionDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = approvalService.rejectLeave(id, dto, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/sendBack")
    public ResponseEntity<LeaveResponseDto> sendBackLeave(@PathVariable UUID id,
                                                          @RequestBody(required = false) ApprovalActionDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = approvalService.sendBackLeave(id, dto, currentUser);
        return ResponseEntity.ok(response);
    }
}
