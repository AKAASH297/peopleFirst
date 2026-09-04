package com.peoplefirst.volunteering.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "volunteering_enrollments")
public class VolunteeringEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    @Column(name = "banner_opt_in", nullable = false)
    private boolean bannerOptIn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public VolunteeringEnrollment() {
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(UUID leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public boolean isBannerOptIn() {
        return bannerOptIn;
    }

    public void setBannerOptIn(boolean bannerOptIn) {
        this.bannerOptIn = bannerOptIn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
