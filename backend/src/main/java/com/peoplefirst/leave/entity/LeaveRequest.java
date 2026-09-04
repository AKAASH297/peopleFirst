package com.peoplefirst.leave.entity;

import com.peoplefirst.policy.entity.LeaveType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "combined_with_type")
    private LeaveType combinedWithType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private double totalDays;

    @Column(name = "is_half_day", nullable = false)
    private boolean halfDay;

    @Column(name = "half_day_session")
    private String halfDaySession; // FIRST_HALF, SECOND_HALF

    @Column(name = "reason", length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaveStatus status;

    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "document_attached", nullable = false)
    private boolean documentAttached;

    @Column(name = "applied_date", nullable = false)
    private LocalDate appliedDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LeaveRequest() {
    }

    public LeaveRequest(UUID userId, LeaveType leaveType, LeaveType combinedWithType,
                        LocalDate startDate, LocalDate endDate, double totalDays,
                        boolean halfDay, String halfDaySession, String reason,
                        String documentUrl, boolean documentAttached, LocalDate appliedDate) {
        this.userId = userId;
        this.leaveType = leaveType;
        this.combinedWithType = combinedWithType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalDays = totalDays;
        this.halfDay = halfDay;
        this.halfDaySession = halfDaySession;
        this.reason = reason;
        this.documentUrl = documentUrl;
        this.documentAttached = documentAttached;
        this.appliedDate = appliedDate != null ? appliedDate : LocalDate.now();
        this.status = LeaveStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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
