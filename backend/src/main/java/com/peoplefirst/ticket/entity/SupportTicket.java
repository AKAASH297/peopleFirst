package com.peoplefirst.ticket.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ticket_type", nullable = false)
    private String ticketType; // LATE_SUBMISSION, POST_DATE_CORRECTION, TECHNICAL_ERROR, POLICY_EXCEPTION

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", length = 2000, nullable = false)
    private String description;

    @Column(name = "related_leave_id")
    private UUID relatedLeaveId;

    @Column(name = "status", nullable = false)
    private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED

    @Column(name = "resolution_comment", length = 1000)
    private String resolutionComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SupportTicket() {
    }

    public SupportTicket(UUID userId, String ticketType, String subject, String description, UUID relatedLeaveId) {
        this.userId = userId;
        this.ticketType = ticketType;
        this.subject = subject;
        this.description = description;
        this.relatedLeaveId = relatedLeaveId;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
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

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getRelatedLeaveId() {
        return relatedLeaveId;
    }

    public void setRelatedLeaveId(UUID relatedLeaveId) {
        this.relatedLeaveId = relatedLeaveId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolutionComment() {
        return resolutionComment;
    }

    public void setResolutionComment(String resolutionComment) {
        this.resolutionComment = resolutionComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
