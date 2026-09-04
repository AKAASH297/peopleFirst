package com.peoplefirst.leave.dto;

import com.peoplefirst.policy.entity.LeaveType;
import java.util.UUID;

public class LeaveBalanceDto {

    private UUID id;
    private UUID userId;
    private String employeeName;
    private LeaveType leaveType;
    private String leaveTypeDisplayName;
    private double allocatedDays;
    private double usedDays;
    private double pendingDays;
    private double remainingDays;
    private int year;

    public LeaveBalanceDto() {
    }

    public LeaveBalanceDto(UUID id, UUID userId, String employeeName, LeaveType leaveType,
                           String leaveTypeDisplayName, double allocatedDays, double usedDays,
                           double pendingDays, double remainingDays, int year) {
        this.id = id;
        this.userId = userId;
        this.employeeName = employeeName;
        this.leaveType = leaveType;
        this.leaveTypeDisplayName = leaveTypeDisplayName;
        this.allocatedDays = allocatedDays;
        this.usedDays = usedDays;
        this.pendingDays = pendingDays;
        this.remainingDays = remainingDays;
        this.year = year;
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

    public double getAllocatedDays() {
        return allocatedDays;
    }

    public void setAllocatedDays(double allocatedDays) {
        this.allocatedDays = allocatedDays;
    }

    public double getUsedDays() {
        return usedDays;
    }

    public void setUsedDays(double usedDays) {
        this.usedDays = usedDays;
    }

    public double getPendingDays() {
        return pendingDays;
    }

    public void setPendingDays(double pendingDays) {
        this.pendingDays = pendingDays;
    }

    public double getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(double remainingDays) {
        this.remainingDays = remainingDays;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
