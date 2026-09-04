package com.peoplefirst.leave.service;

import com.peoplefirst.audit.service.AuditService;
import com.peoplefirst.leave.dto.*;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.leave.validator.LeaveValidator;
import com.peoplefirst.policy.validator.PolicyValidator;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final PolicyValidator policyValidator;
    private final LeaveValidator leaveValidator;
    private final LeaveMapper leaveMapper;
    private final AuditService auditService;
    private final UserService userService;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        LeaveBalanceService leaveBalanceService,
                        PolicyValidator policyValidator,
                        LeaveValidator leaveValidator,
                        LeaveMapper leaveMapper,
                        AuditService auditService,
                        UserService userService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.policyValidator = policyValidator;
        this.leaveValidator = leaveValidator;
        this.leaveMapper = leaveMapper;
        this.auditService = auditService;
        this.userService = userService;
    }

    @Transactional
    public LeaveResponseDto applyLeave(CreateLeaveRequestDto dto, User user) {
        double totalDays = leaveValidator.calculateTotalDays(dto.getStartDate(), dto.getEndDate(), dto.isHalfDay());
        leaveValidator.validateNoOverlap(user.getId(), dto.getStartDate(), dto.getEndDate(), dto.isHalfDay(), dto.getHalfDaySession(), null);
        if (dto.isHalfDay() && !"FIRST_HALF".equals(dto.getHalfDaySession()) && !"SECOND_HALF".equals(dto.getHalfDaySession())) {
            throw new PolicyViolationException("Half-day leave needs a session: FIRST_HALF or SECOND_HALF.");
        }

        // Validate policy constraints
        policyValidator.validateLeaveApplication(
                user,
                dto.getLeaveType(),
                dto.getCombinedWithType(),
                dto.getStartDate(),
                dto.getEndDate(),
                totalDays,
                dto.isDocumentAttached(),
                dto.getDocumentUrl(),
                LocalDate.now()
        );

        // Validate no overlap with active leaves (PENDING or APPROVED)
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findByUserIdAndStatusIn(
                user.getId(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        leaveValidator.validateNoDateOverlap(
                activeLeaves,
                null,
                dto.getStartDate(),
                dto.getEndDate(),
                dto.isHalfDay(),
                dto.getHalfDaySession()
        );

        // Reserve pending balance
        leaveBalanceService.reservePendingDays(user, dto.getLeaveType(), totalDays, dto.getStartDate().getYear());

        LeaveRequest leaveRequest = new LeaveRequest(
                user.getId(),
                dto.getLeaveType(),
                dto.getCombinedWithType(),
                dto.getStartDate(),
                dto.getEndDate(),
                totalDays,
                dto.isHalfDay(),
                dto.getHalfDaySession(),
                dto.getReason(),
                dto.getDocumentUrl(),
                dto.isDocumentAttached(),
                LocalDate.now()
        );

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        // Record audit trail
        auditService.recordAuditLog(
                saved.getId(),
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                "APPLY",
                null,
                LeaveStatus.PENDING.name(),
                dto.getReason(),
                false,
                false
        );

        return leaveMapper.toDto(saved, user);
    }

    @Transactional(readOnly = true)
    public LeaveRequest getLeaveEntityById(UUID id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public LeaveResponseDto getLeaveById(UUID id) {
        LeaveRequest request = getLeaveEntityById(id);
        User user = userService.getUserEntityById(request.getUserId());
        return leaveMapper.toDto(request, user);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getLeavesForUser(UUID userId) {
        User user = userService.getUserEntityById(userId);
        return leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(req -> leaveMapper.toDto(req, user))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeaveEntitiesForUser(UUID userId) {
        return leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getLeavesForUsers(List<UUID> userIds) {
        return leaveRequestRepository.findByUserIdInOrderByCreatedAtDesc(userIds).stream()
                .map(req -> {
                    User user = userService.getUserEntityById(req.getUserId());
                    return leaveMapper.toDto(req, user);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getAllLeavesOrgWide() {
        return leaveRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(req -> {
                    User user = userService.getUserEntityById(req.getUserId());
                    return leaveMapper.toDto(req, user);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public LeaveResponseDto editLeave(UUID leaveId, UpdateLeaveRequestDto dto, User user) {
        LeaveRequest leaveRequest = getLeaveEntityById(leaveId);

        // Security check
        if (!leaveRequest.getUserId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not authorized to edit this leave request.");
        }

        // Only PENDING or RETURNED leaves can be edited
        if (leaveRequest.getStatus() != LeaveStatus.PENDING && leaveRequest.getStatus() != LeaveStatus.RETURNED) {
            throw new PolicyViolationException("Only PENDING or RETURNED leave requests can be edited. Current status: " + leaveRequest.getStatus());
        }

        // Must be before leave start date
        policyValidator.validateActionBeforeStartDate(leaveRequest.getStartDate(), "edit");

        double newTotalDays = leaveValidator.calculateTotalDays(dto.getStartDate(), dto.getEndDate(), dto.isHalfDay());
        leaveValidator.validateNoOverlap(user.getId(), dto.getStartDate(), dto.getEndDate(), dto.isHalfDay(), dto.getHalfDaySession(), leaveId);
        if (dto.isHalfDay() && !"FIRST_HALF".equals(dto.getHalfDaySession()) && !"SECOND_HALF".equals(dto.getHalfDaySession())) {
            throw new PolicyViolationException("Half-day leave needs a session: FIRST_HALF or SECOND_HALF.");
        }

        // Validate new policy requirements
        policyValidator.validateLeaveApplication(
                user,
                dto.getLeaveType(),
                dto.getCombinedWithType(),
                dto.getStartDate(),
                dto.getEndDate(),
                newTotalDays,
                dto.isDocumentAttached(),
                dto.getDocumentUrl(),
                LocalDate.now()
        );

        // Validate no overlap with other active leaves (excluding this leave)
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findByUserIdAndStatusIn(
                user.getId(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)
        );
        leaveValidator.validateNoDateOverlap(
                activeLeaves,
                leaveId,
                dto.getStartDate(),
                dto.getEndDate(),
                dto.isHalfDay(),
                dto.getHalfDaySession()
        );

        // Adjust pending balance
        leaveBalanceService.adjustPendingDaysOnEdit(
                user,
                leaveRequest.getLeaveType(),
                leaveRequest.getTotalDays(),
                dto.getLeaveType(),
                newTotalDays,
                dto.getStartDate().getYear()
        );

        String previousStatus = leaveRequest.getStatus().name();
        leaveRequest.setLeaveType(dto.getLeaveType());
        leaveRequest.setCombinedWithType(dto.getCombinedWithType());
        leaveRequest.setStartDate(dto.getStartDate());
        leaveRequest.setEndDate(dto.getEndDate());
        leaveRequest.setTotalDays(newTotalDays);
        leaveRequest.setHalfDay(dto.isHalfDay());
        leaveRequest.setHalfDaySession(dto.getHalfDaySession());
        leaveRequest.setReason(dto.getReason());
        leaveRequest.setDocumentUrl(dto.getDocumentUrl());
        leaveRequest.setDocumentAttached(dto.isDocumentAttached());
        leaveRequest.setStatus(LeaveStatus.PENDING); // Resubmitted to PENDING

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);

        auditService.recordAuditLog(
                updated.getId(),
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                "EDIT",
                previousStatus,
                LeaveStatus.PENDING.name(),
                "Edited and resubmitted: " + dto.getReason(),
                false,
                false
        );

        return leaveMapper.toDto(updated, user);
    }

    @Transactional
    public LeaveResponseDto cancelLeave(UUID leaveId, User user, String comment) {
        LeaveRequest leaveRequest = getLeaveEntityById(leaveId);

        // Security check
        if (!leaveRequest.getUserId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You are not authorized to cancel this leave request.");
        }

        // Must be PENDING or APPROVED
        if (leaveRequest.getStatus() != LeaveStatus.PENDING && leaveRequest.getStatus() != LeaveStatus.APPROVED) {
            throw new PolicyViolationException("Cannot cancel leave with status: " + leaveRequest.getStatus());
        }

        // Must be before leave start date
        policyValidator.validateActionBeforeStartDate(leaveRequest.getStartDate(), "cancel");

        boolean wasApproved = leaveRequest.getStatus() == LeaveStatus.APPROVED;
        String previousStatus = leaveRequest.getStatus().name();

        User leaveOwner = userService.getUserEntityById(leaveRequest.getUserId());
        // Restore days
        leaveBalanceService.restoreDaysOnCancel(
                leaveOwner,
                leaveRequest.getLeaveType(),
                leaveRequest.getTotalDays(),
                wasApproved,
                leaveRequest.getStartDate().getYear()
        );

        leaveRequest.setStatus(LeaveStatus.CANCELLED);
        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);

        auditService.recordAuditLog(
                updated.getId(),
                user.getId(),
                user.getFullName(),
                user.getRole().name(),
                "CANCEL",
                previousStatus,
                LeaveStatus.CANCELLED.name(),
                comment != null ? comment : "Cancelled by user before start date",
                user.getRole() == Role.ADMIN && !user.getId().equals(leaveOwner.getId()),
                false
        );

        return leaveMapper.toDto(updated, leaveOwner);
    }

    @Transactional
    public LeaveResponseDto adminDirectEdit(UUID leaveId, AdminDirectEditDto dto, User adminUser) {
        if (adminUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can perform direct database edits.");
        }

        LeaveRequest leaveRequest = getLeaveEntityById(leaveId);
        User leaveOwner = userService.getUserEntityById(leaveRequest.getUserId());
        String previousStatus = leaveRequest.getStatus().name();

        // If status changed to/from APPROVED or PENDING, adjust balances
        if (leaveRequest.getStatus() != dto.getStatus()) {
            // Revert old status effect
            if (leaveRequest.getStatus() == LeaveStatus.APPROVED) {
                leaveBalanceService.restoreDaysOnCancel(leaveOwner, leaveRequest.getLeaveType(), leaveRequest.getTotalDays(), true, leaveRequest.getStartDate().getYear());
            } else if (leaveRequest.getStatus() == LeaveStatus.PENDING) {
                leaveBalanceService.releasePendingDays(leaveOwner, leaveRequest.getLeaveType(), leaveRequest.getTotalDays(), leaveRequest.getStartDate().getYear());
            }

            // Apply new status effect
            if (dto.getStatus() == LeaveStatus.APPROVED) {
                leaveBalanceService.reservePendingDays(leaveOwner, dto.getLeaveType() != null ? dto.getLeaveType() : leaveRequest.getLeaveType(),
                        dto.getTotalDays() != null ? dto.getTotalDays() : leaveRequest.getTotalDays(),
                        leaveRequest.getStartDate().getYear());
                leaveBalanceService.commitApprovedDays(leaveOwner, dto.getLeaveType() != null ? dto.getLeaveType() : leaveRequest.getLeaveType(),
                        dto.getTotalDays() != null ? dto.getTotalDays() : leaveRequest.getTotalDays(),
                        leaveRequest.getStartDate().getYear());
            } else if (dto.getStatus() == LeaveStatus.PENDING) {
                leaveBalanceService.reservePendingDays(leaveOwner, dto.getLeaveType() != null ? dto.getLeaveType() : leaveRequest.getLeaveType(),
                        dto.getTotalDays() != null ? dto.getTotalDays() : leaveRequest.getTotalDays(),
                        leaveRequest.getStartDate().getYear());
            }
        }

        if (dto.getLeaveType() != null) {
            leaveRequest.setLeaveType(dto.getLeaveType());
        }
        if (dto.getStartDate() != null) {
            leaveRequest.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            leaveRequest.setEndDate(dto.getEndDate());
        }
        if (dto.getTotalDays() != null) {
            leaveRequest.setTotalDays(dto.getTotalDays());
        }
        if (dto.getReason() != null) {
            leaveRequest.setReason(dto.getReason());
        }
        leaveRequest.setStatus(dto.getStatus());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        // Spec §5 & Criterion 10: Admin's direct-DB-edit action is a distinct, separately audited operation from an approval override
        auditService.recordAuditLog(
                saved.getId(),
                adminUser.getId(),
                adminUser.getFullName(),
                adminUser.getRole().name(),
                "ADMIN_DIRECT_EDIT",
                previousStatus,
                dto.getStatus().name(),
                dto.getAuditComment(),
                false,
                true
        );

        return leaveMapper.toDto(saved, leaveOwner);
    }
}
