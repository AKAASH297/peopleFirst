package com.peoplefirst.audit.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_audit_logs")
public class LeaveAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "leave_request_id", nullable = false)
    private UUID leaveRequestId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "action", nullable = false)
    private String action; // APPLY, APPROVE, REJECT, SEND_BACK, EDIT, CANCEL, ADMIN_DIRECT_EDIT

    @Column(name = "previous_status")
    private String previousStatus;

    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "is_admin_override", nullable = false)
    private boolean adminOverride;

    @Column(name = "is_admin_direct_edit", nullable = false)
    private boolean adminDirectEdit;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public LeaveAuditLog() {
    }

    public LeaveAuditLog(UUID leaveRequestId, UUID actorId, String actorName, String actorRole,
                         String action, String previousStatus, String newStatus, String comment,
                         boolean adminOverride, boolean adminDirectEdit) {
        this.leaveRequestId = leaveRequestId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.comment = comment;
        this.adminOverride = adminOverride;
        this.adminDirectEdit = adminDirectEdit;
        this.timestamp = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(UUID leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isAdminOverride() {
        return adminOverride;
    }

    public void setAdminOverride(boolean adminOverride) {
        this.adminOverride = adminOverride;
    }

    public boolean isAdminDirectEdit() {
        return adminDirectEdit;
    }

    public void setAdminDirectEdit(boolean adminDirectEdit) {
        this.adminDirectEdit = adminDirectEdit;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
