package com.peoplefirst.user.dto;

import com.peoplefirst.user.entity.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserResponseDto {

    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private boolean contractor;
    private String department;
    private String baseLocation;
    private UUID managerId;
    private LocalDateTime createdAt;

    public UserResponseDto() {
    }

    public UserResponseDto(UUID id, String username, String email, String fullName, Role role,
                           boolean contractor, String department, String baseLocation,
                           UUID managerId, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.contractor = contractor;
        this.department = department;
        this.baseLocation = baseLocation;
        this.managerId = managerId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isContractor() {
        return contractor;
    }

    public void setContractor(boolean contractor) {
        this.contractor = contractor;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBaseLocation() {
        return baseLocation;
    }

    public void setBaseLocation(String baseLocation) {
        this.baseLocation = baseLocation;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
