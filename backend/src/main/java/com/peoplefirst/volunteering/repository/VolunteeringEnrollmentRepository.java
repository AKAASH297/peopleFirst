package com.peoplefirst.volunteering.repository;

import com.peoplefirst.volunteering.entity.VolunteeringEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VolunteeringEnrollmentRepository extends JpaRepository<VolunteeringEnrollment, UUID> {
    List<VolunteeringEnrollment> findByUserId(UUID userId);
}
