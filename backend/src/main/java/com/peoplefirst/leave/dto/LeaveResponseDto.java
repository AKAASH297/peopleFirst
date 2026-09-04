package com.peoplefirst.leave.dto;

import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.policy.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class LeaveResponseDto {

    private UUID id;
    private UUID userId;
    private String employeeName;
    private String employeeRole;
    private String department;
    private LeaveType leaveType;
    private String leaveTypeDisplayName;
    private LeaveType combinedWithType;
    private LocalDate startDate;
    private LocalDate endDate;
    private double totalDays;
    private boolean halfDay;
    private String halfDaySession;
    private String reason;
    private LeaveStatus status;
    private String documentUrl;
    private boolean documentAttached;
    private LocalDate appliedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public LeaveResponseDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeRole() {
        return employeeRole;
    }

    public void setEmployeeRole(String employeeRole) {
        this.employeeRole = employeeRole;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public String getLeaveTypeDisplayName() {
        return leaveTypeDisplayName;
    }

    public void setLeaveTypeDisplayName(String leaveTypeDisplayName) {
        this.leaveTypeDisplayName = leaveTypeDisplayName;
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

    public double getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(double totalDays) {
        this.totalDays = totalDays;
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

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
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

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
