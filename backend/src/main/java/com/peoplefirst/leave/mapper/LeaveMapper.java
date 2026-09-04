package com.peoplefirst.leave.mapper;

import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class LeaveMapper {

    public LeaveResponseDto toDto(LeaveRequest leaveRequest, User user) {
        if (leaveRequest == null) {
            return null;
        }

        LeaveResponseDto dto = new LeaveResponseDto();
        dto.setId(leaveRequest.getId());
        dto.setUserId(leaveRequest.getUserId());
        if (user != null) {
            dto.setEmployeeName(user.getFullName());
            dto.setEmployeeRole(user.getRole().name());
            dto.setDepartment(user.getDepartment());
        }
        dto.setLeaveType(leaveRequest.getLeaveType());
        dto.setLeaveTypeDisplayName(leaveRequest.getLeaveType().getDisplayName());
        dto.setCombinedWithType(leaveRequest.getCombinedWithType());
        dto.setStartDate(leaveRequest.getStartDate());
        dto.setEndDate(leaveRequest.getEndDate());
        dto.setTotalDays(leaveRequest.getTotalDays());
        dto.setHalfDay(leaveRequest.isHalfDay());
        dto.setHalfDaySession(leaveRequest.getHalfDaySession());
        dto.setReason(leaveRequest.getReason());
        dto.setStatus(leaveRequest.getStatus());
        dto.setDocumentUrl(leaveRequest.getDocumentUrl());
        dto.setDocumentAttached(leaveRequest.isDocumentAttached());
        dto.setAppliedDate(leaveRequest.getAppliedDate());
        dto.setCreatedAt(leaveRequest.getCreatedAt());
        dto.setUpdatedAt(leaveRequest.getUpdatedAt());

        return dto;
    }

    public LeaveBalanceDto toBalanceDto(LeaveBalance balance, User user) {
        if (balance == null) {
            return null;
        }

        return new LeaveBalanceDto(
                balance.getId(),
                balance.getUserId(),
                user != null ? user.getFullName() : null,
                balance.getLeaveType(),
                balance.getLeaveType().getDisplayName(),
                balance.getAllocatedDays(),
                balance.getUsedDays(),
                balance.getPendingDays(),
                balance.getRemainingDays(),
                balance.getYear()
        );
    }
}
