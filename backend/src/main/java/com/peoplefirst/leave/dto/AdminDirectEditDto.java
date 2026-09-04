package com.peoplefirst.leave.dto;

import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.policy.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class AdminDirectEditDto {

    @NotNull(message = "Leave status is required")
    private LeaveStatus status;

    private LeaveType leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDays;
    private String reason;

    @NotBlank(message = "Audit comment explaining reason for direct edit is required")
    private String auditComment;

    public AdminDirectEditDto() {
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Double getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(Double totalDays) {
        this.totalDays = totalDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAuditComment() {
        return auditComment;
    }

    public void setAuditComment(String auditComment) {
        this.auditComment = auditComment;
    }
}
