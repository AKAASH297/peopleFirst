package com.peoplefirst.leave.entity;

import com.peoplefirst.policy.entity.LeaveType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "leave_type", "balance_year"})
})
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(name = "allocated_days", nullable = false)
    private double allocatedDays;

    @Column(name = "used_days", nullable = false)
    private double usedDays;

    @Column(name = "pending_days", nullable = false)
    private double pendingDays;

    @Column(name = "remaining_days", nullable = false)
    private double remainingDays;

    @Column(name = "balance_year", nullable = false)
    private int year;

    public LeaveBalance() {
    }

    public LeaveBalance(UUID userId, LeaveType leaveType, double allocatedDays, int year) {
        this.userId = userId;
        this.leaveType = leaveType;
        this.allocatedDays = allocatedDays;
        this.usedDays = 0.0;
        this.pendingDays = 0.0;
        this.remainingDays = allocatedDays;
        this.year = year;
    }

    public void recalculateRemainingDays() {
        this.remainingDays = Math.max(0.0, this.allocatedDays - this.usedDays - this.pendingDays);
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

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
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
