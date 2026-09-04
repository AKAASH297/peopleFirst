package com.peoplefirst.leave.dto;

import com.peoplefirst.policy.entity.LeaveType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UpdateLeaveRequestDto {

    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    private LeaveType combinedWithType;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private boolean halfDay;
    private String halfDaySession;
    private String reason;
    private String documentUrl;
    private boolean documentAttached;

    public UpdateLeaveRequestDto() {
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LeaveType getCombinedWithType() {
        return combinedWithType;
    }

    public void setCombinedWithType(LeaveType combinedWithType) {
        this.combinedWithType = combinedWithType;
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

    public boolean isHalfDay() {
        return halfDay;
    }

    public void setHalfDay(boolean halfDay) {
        this.halfDay = halfDay;
    }

    public String getHalfDaySession() {
        return halfDaySession;
    }

    public void setHalfDaySession(String halfDaySession) {
        this.halfDaySession = halfDaySession;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public boolean isDocumentAttached() {
        return documentAttached;
    }

    public void setDocumentAttached(boolean documentAttached) {
        this.documentAttached = documentAttached;
    }
}
