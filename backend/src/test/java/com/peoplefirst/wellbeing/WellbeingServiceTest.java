package com.peoplefirst.wellbeing;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.WeeklyWellbeingDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.rules.*;
import com.peoplefirst.wellbeing.service.WellbeingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WellbeingServiceTest {

    private WellbeingService wellbeingService;
    private User employeeBangalore;
    private User employeeHyderabad;

    @BeforeEach
    void setUp() {
        SickLeaveWellbeingRule sickLeaveRule = new SickLeaveWellbeingRule();
        HalfDaySickLeaveWellbeingRule halfDayRule = new HalfDaySickLeaveWellbeingRule();
        StressExpressionWellbeingRule stressRule = new StressExpressionWellbeingRule();
        VacationNudgeWellbeingRule nudgeRule = new VacationNudgeWellbeingRule();
        VolunteeringWellbeingRule volunteeringRule = new VolunteeringWellbeingRule();

        wellbeingService = new WellbeingService(sickLeaveRule, halfDayRule, stressRule, nudgeRule, volunteeringRule);

        employeeBangalore = new User("empBlr", "empBlr@test.com", "hash", "Bangalore Dev",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        employeeBangalore.setId(UUID.randomUUID());

        employeeHyderabad = new User("empHyd", "empHyd@test.com", "hash", "Hyderabad Dev",
                Role.EMPLOYEE, false, "Eng", "Hyderabad", UUID.randomUUID());
        employeeHyderabad.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Trigger 1: Sick leave triggers doctor inquiry, 90-day OPD reimbursement notice, and location hospitals")
    void testSickLeaveTrigger() {
        LeaveRequest sickRequest = new LeaveRequest(
                employeeBangalore.getId(), LeaveType.SICK, null,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 2.0,
                false, null, "Flu", null, false, LocalDate.now()
        );

        List<WellbeingSuggestionDto> suggestions = wellbeingService.evaluateLeaveWellbeing(sickRequest, employeeBangalore);
        assertFalse(suggestions.isEmpty());

        WellbeingSuggestionDto sickSug = suggestions.get(0);
        assertEquals("SICK_LEAVE_APPLIED", sickSug.getTrigger());
        assertTrue(sickSug.getMessage().contains("90 days"));
        assertNotNull(sickSug.getPartnerHospitals());
        assertTrue(sickSug.getPartnerHospitals().stream().anyMatch(h -> h.getCity().equalsIgnoreCase("Bangalore")));
    }

    @Test
    @DisplayName("Trigger 2: Half-day sick leave prompts for office sick room with room number")
    void testHalfDaySickLeaveTrigger() {
        LeaveRequest halfDaySick = new LeaveRequest(
                employeeBangalore.getId(), LeaveType.SICK, null,
                LocalDate.now().plusDays(3), LocalDate.now().plusDays(3), 0.5,
                true, "FIRST_HALF", "Migraine", null, false, LocalDate.now()
        );

        List<WellbeingSuggestionDto> suggestions = wellbeingService.evaluateLeaveWellbeing(halfDaySick, employeeBangalore);
        assertTrue(suggestions.stream().anyMatch(s -> s.getTrigger().equals("HALF_DAY_SICK_LEAVE_APPLIED")));

        WellbeingSuggestionDto roomSug = suggestions.stream()
                .filter(s -> s.getTrigger().equals("HALF_DAY_SICK_LEAVE_APPLIED"))
                .findFirst().orElseThrow();
        assertTrue(roomSug.getMessage().contains("Room 304"));
    }

    @Test
    @DisplayName("Trigger 3: Stress expression triggers massage chair and recreational area suggestions")
    void testStressExpressionTrigger() {
        String stressedMessage = "I am feeling burnt out and under severe pressure with this release deadline";
        WellbeingSuggestionDto suggestion = wellbeingService.evaluateStressMessage(stressedMessage);

        assertNotNull(suggestion);
        assertEquals("STRESS_EXPRESSION_DETECTED", suggestion.getTrigger());
        assertTrue(suggestion.getMessage().contains("massage chair") || suggestion.getMessage().contains("Recreational Lounge"));
    }

    @Test
    @DisplayName("Trigger 4: No leave taken in last quarter triggers vacation nudge and partner resorts")
    void testVacationNudgeTrigger() {
        WellbeingSuggestionDto nudge = wellbeingService.checkVacationNudge(employeeBangalore, false);

        assertNotNull(nudge);
        assertEquals("NO_LEAVE_LAST_QUARTER", nudge.getTrigger());
        assertNotNull(nudge.getPartnerResorts());
        assertFalse(nudge.getPartnerResorts().isEmpty());
    }

    @Test
    @DisplayName("Trigger 5: Volunteering leave suggests company CSR groups and intranet banner")
    void testVolunteeringLeaveTrigger() {
        LeaveRequest volRequest = new LeaveRequest(
                employeeBangalore.getId(), LeaveType.VOLUNTEERING, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(5), 1.0,
                false, null, "Community tree planting", null, false, LocalDate.now()
        );

        List<WellbeingSuggestionDto> suggestions = wellbeingService.evaluateLeaveWellbeing(volRequest, employeeBangalore);
        assertFalse(suggestions.isEmpty());

        WellbeingSuggestionDto volSug = suggestions.get(0);
        assertEquals("VOLUNTEERING_LEAVE_APPLIED", volSug.getTrigger());
        assertTrue(volSug.getMessage().contains("intranet banner"));
        assertNotNull(volSug.getGroupSuggestions());
        assertFalse(volSug.getGroupSuggestions().isEmpty());
    }

    @Test
    @DisplayName("Catalog: All 9 company wellbeing amenities are registered with timings and locations")
    void testAllAmenitiesCatalog() {
        List<AmenityDto> amenities = wellbeingService.getAllAmenities();
        assertEquals(9, amenities.size());

        // Verify Gym, Doctor, Psychologist, Legal Advisor, Health Insurance, Yoga, Zumba, Recreational area, Massage chairs
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Gymnasium")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("General Physician") && a.getTiming().contains("9:00 AM - 5:00 PM")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Psychologist")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Legal Advisor")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Insurance")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Yoga")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Zumba")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Recreational Lounge")));
        assertTrue(amenities.stream().anyMatch(a -> a.getName().contains("Massage Chairs")));
    }

    @Test
    @DisplayName("Weekly Wellbeing Report: Generates healthy status when leave balance is maintained")
    void testWeeklyWellbeingReportHealthy() {
        LeaveRequest casualLeave = new LeaveRequest(
                employeeBangalore.getId(), LeaveType.CASUAL, null,
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(9), 2.0,
                false, null, "Family function", null, false, LocalDate.now().minusDays(12)
        );

        WeeklyWellbeingDto report = wellbeingService.getWeeklyWellbeingReport(employeeBangalore, List.of(casualLeave));
        assertNotNull(report);
        assertEquals("HEALTHY", report.getStatus());
        assertEquals(1, report.getLeavesTakenThisMonth());
        assertEquals(1, report.getLeavesTakenLastQuarter());
        assertFalse(report.isRecentSickLeave());
        assertFalse(report.isVacationNudge());
    }

    @Test
    @DisplayName("Weekly Wellbeing Report: Flags 90-day OPD claim reminder when recent sick leave taken")
    void testWeeklyWellbeingReportWithRecentSickLeave() {
        LeaveRequest sickLeave = new LeaveRequest(
                employeeBangalore.getId(), LeaveType.SICK, null,
                LocalDate.now().minusDays(15), LocalDate.now().minusDays(14), 2.0,
                false, null, "Flu", null, false, LocalDate.now().minusDays(16)
        );

        WeeklyWellbeingDto report = wellbeingService.getWeeklyWellbeingReport(employeeBangalore, List.of(sickLeave));
        assertNotNull(report);
        assertEquals("ACTION_REQUIRED", report.getStatus());
        assertTrue(report.isRecentSickLeave());
        assertNotNull(report.getOpdClaimReminder());
        assertTrue(report.getOpdClaimReminder().contains("90-day"));
        assertNotNull(report.getSuggestedHospitals());
        assertFalse(report.getSuggestedHospitals().isEmpty());
    }

    @Test
    @DisplayName("Weekly Wellbeing Report: Triggers vacation nudge and partner resorts when no leaves in last quarter")
    void testWeeklyWellbeingReportVacationNudge() {
        WeeklyWellbeingDto report = wellbeingService.getWeeklyWellbeingReport(employeeBangalore, List.of());
        assertNotNull(report);
        assertEquals("RECHARGE_RECOMMENDED", report.getStatus());
        assertTrue(report.isVacationNudge());
        assertEquals(0, report.getLeavesTakenLastQuarter());
        assertNotNull(report.getSuggestedResorts());
        assertFalse(report.getSuggestedResorts().isEmpty());
    }
}
