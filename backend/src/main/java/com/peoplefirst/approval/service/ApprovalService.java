package com.peoplefirst.approval.service;

import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.validator.ApprovalValidator;
import com.peoplefirst.audit.service.AuditService;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApprovalService {

    private final LeaveService leaveService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final ApprovalValidator approvalValidator;
    private final AuditService auditService;
    private final UserService userService;
    private final LeaveMapper leaveMapper;

    public ApprovalService(LeaveService leaveService,
                           LeaveRequestRepository leaveRequestRepository,
                           LeaveBalanceService leaveBalanceService,
                           ApprovalValidator approvalValidator,
                           AuditService auditService,
                           UserService userService,
                           LeaveMapper leaveMapper) {
        this.leaveService = leaveService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.approvalValidator = approvalValidator;
        this.auditService = auditService;
        this.userService = userService;
        this.leaveMapper = leaveMapper;
    }

    @Transactional
    public LeaveResponseDto approveLeave(UUID leaveId, ApprovalActionDto dto, User approver) {
        LeaveRequest request = leaveService.getLeaveEntityById(leaveId);
        User leaveOwner = userService.getUserEntityById(request.getUserId());

        approvalValidator.validateApprovalAuthority(request, leaveOwner, approver);

        boolean isAdminOverride = approver.getRole() == Role.ADMIN &&
                (leaveOwner.getManagerId() != null && !leaveOwner.getManagerId().equals(approver.getId()));

        String previousStatus = request.getStatus().name();
        request.setStatus(LeaveStatus.APPROVED);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Move from pending to used
        leaveBalanceService.commitApprovedDays(
                leaveOwner,
                request.getLeaveType(),
                request.getTotalDays(),
                request.getStartDate().getYear()
        );

        auditService.recordAuditLog(
                saved.getId(),
                approver.getId(),
                approver.getFullName(),
                approver.getRole().name(),
                "APPROVE",
                previousStatus,
                LeaveStatus.APPROVED.name(),
                dto != null && dto.getComment() != null ? dto.getComment() : "Approved",
                isAdminOverride,
                false
        );

        return leaveMapper.toDto(saved, leaveOwner);
    }

    @Transactional
    public LeaveResponseDto rejectLeave(UUID leaveId, ApprovalActionDto dto, User approver) {
        LeaveRequest request = leaveService.getLeaveEntityById(leaveId);
        User leaveOwner = userService.getUserEntityById(request.getUserId());

        approvalValidator.validateApprovalAuthority(request, leaveOwner, approver);

        boolean isAdminOverride = approver.getRole() == Role.ADMIN &&
                (leaveOwner.getManagerId() != null && !leaveOwner.getManagerId().equals(approver.getId()));

        String previousStatus = request.getStatus().name();
        request.setStatus(LeaveStatus.REJECTED);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Release pending days back to available
        leaveBalanceService.releasePendingDays(
                leaveOwner,
                request.getLeaveType(),
                request.getTotalDays(),
                request.getStartDate().getYear()
        );

        auditService.recordAuditLog(
                saved.getId(),
                approver.getId(),
                approver.getFullName(),
                approver.getRole().name(),
                "REJECT",
                previousStatus,
                LeaveStatus.REJECTED.name(),
                dto != null && dto.getComment() != null ? dto.getComment() : "Rejected",
                isAdminOverride,
                false
        );

        return leaveMapper.toDto(saved, leaveOwner);
    }

    @Transactional
    public LeaveResponseDto sendBackLeave(UUID leaveId, ApprovalActionDto dto, User approver) {
        LeaveRequest request = leaveService.getLeaveEntityById(leaveId);
        User leaveOwner = userService.getUserEntityById(request.getUserId());

        approvalValidator.validateApprovalAuthority(request, leaveOwner, approver);

        boolean isAdminOverride = approver.getRole() == Role.ADMIN &&
                (leaveOwner.getManagerId() != null && !leaveOwner.getManagerId().equals(approver.getId()));

        String previousStatus = request.getStatus().name();
        request.setStatus(LeaveStatus.RETURNED);
        LeaveRequest saved = leaveRequestRepository.save(request);

        auditService.recordAuditLog(
                saved.getId(),
                approver.getId(),
                approver.getFullName(),
                approver.getRole().name(),
                "SEND_BACK",
                previousStatus,
                LeaveStatus.RETURNED.name(),
                dto != null && dto.getComment() != null ? dto.getComment() : "Sent back for modification",
                isAdminOverride,
                false
        );

        return leaveMapper.toDto(saved, leaveOwner);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getPendingApprovals(User approver) {
        if (approver.getRole() == Role.ADMIN) {
            // Admin sees pending leaves across org (excluding own leave)
            return leaveRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(req -> req.getStatus() == LeaveStatus.PENDING && !req.getUserId().equals(approver.getId()))
                    .map(req -> {
                        User owner = userService.getUserEntityById(req.getUserId());
                        return leaveMapper.toDto(req, owner);
                    })
                    .collect(Collectors.toList());
        }

        if (approver.getRole() == Role.MANAGER) {
            List<UserResponseDto> reportees = userService.getDirectReportees(approver.getId());
            List<UUID> reporteeIds = reportees.stream().map(UserResponseDto::getId).collect(Collectors.toList());
            if (reporteeIds.isEmpty()) {
                return new ArrayList<>();
            }
            return leaveRequestRepository.findByUserIdInOrderByCreatedAtDesc(reporteeIds).stream()
                    .filter(req -> req.getStatus() == LeaveStatus.PENDING)
                    .map(req -> {
                        User owner = userService.getUserEntityById(req.getUserId());
                        return leaveMapper.toDto(req, owner);
                    })
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
