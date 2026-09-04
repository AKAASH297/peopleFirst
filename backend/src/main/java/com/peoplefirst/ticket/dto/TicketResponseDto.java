package com.peoplefirst.ticket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class TicketResponseDto {

    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String ticketType;
    private String subject;
    private String description;
    private UUID relatedLeaveId;
    private String status;
    private String resolutionComment;
    private LocalDateTime createdAt;

    public TicketResponseDto() {
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNumber() {
        return "TKT-" + (id != null ? id.toString().substring(0, 8).toUpperCase() : "0000");
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
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
