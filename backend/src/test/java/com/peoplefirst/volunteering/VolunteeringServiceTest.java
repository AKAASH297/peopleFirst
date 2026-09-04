package com.peoplefirst.volunteering;

import com.peoplefirst.volunteering.entity.VolunteeringEnrollment;
import com.peoplefirst.volunteering.repository.VolunteeringEnrollmentRepository;
import com.peoplefirst.volunteering.service.VolunteeringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class VolunteeringServiceTest {

    private VolunteeringEnrollmentRepository repository;
    private VolunteeringService service;
    private UUID userId;
    private UUID leaveId;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(VolunteeringEnrollmentRepository.class);
        service = new VolunteeringService(repository);
        userId = UUID.randomUUID();
        leaveId = UUID.randomUUID();
        when(repository.save(any(VolunteeringEnrollment.class))).thenAnswer(invocation -> {
            VolunteeringEnrollment e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void enrollPersistsGroupAndBannerChoice() {
        VolunteeringEnrollment e = service.enroll(userId, "Green Earth Afforestation Drive", leaveId, true);

        assertEquals("Green Earth Afforestation Drive", e.getGroupName());
        assertTrue(e.isBannerOptIn());
        assertEquals(leaveId, e.getLeaveRequestId());
        assertEquals(userId, e.getUserId());
        ArgumentCaptor<VolunteeringEnrollment> captor = ArgumentCaptor.forClass(VolunteeringEnrollment.class);
        Mockito.verify(repository).save(captor.capture());
        assertEquals("Green Earth Afforestation Drive", captor.getValue().getGroupName());
    }

    @Test
    void blankGroupNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.enroll(userId, "  ", leaveId, false));
        Mockito.verify(repository, Mockito.never()).save(any());
    }
}
