package com.peoplefirst.volunteering.service;

import com.peoplefirst.volunteering.entity.VolunteeringEnrollment;
import com.peoplefirst.volunteering.repository.VolunteeringEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VolunteeringService {

    private final VolunteeringEnrollmentRepository repository;

    public VolunteeringService(VolunteeringEnrollmentRepository repository) {
        this.repository = repository;
    }

    public VolunteeringEnrollment enroll(UUID userId, String groupName, UUID leaveRequestId, boolean bannerOptIn) {
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("Volunteering group must be named.");
        }
        VolunteeringEnrollment enrollment = new VolunteeringEnrollment();
        enrollment.setUserId(userId);
        enrollment.setGroupName(groupName.trim());
        enrollment.setLeaveRequestId(leaveRequestId);
        enrollment.setBannerOptIn(bannerOptIn);
        enrollment.setCreatedAt(LocalDateTime.now());
        return repository.save(enrollment);
    }
}
