package com.peoplefirst.policy;

import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.validator.PolicyValidator;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PolicyValidatorTest {

    private PolicyValidator policyValidator;
    private User employee;
    private User contractor;

    @BeforeEach
    void setUp() {
        policyValidator = new PolicyValidator();

        employee = new User("emp1", "emp1@test.com", "hash", "Test Employee",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        employee.setId(UUID.randomUUID());

        contractor = new User("cont1", "cont1@test.com", "hash", "Test Contractor",
                Role.CONTRACTOR, true, "Eng", "Bangalore", UUID.randomUUID());
        contractor.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Criterion 2: Contractor cannot apply Casual, WFH, Maternity, or Volunteering")
    void testContractorIneligibleLeaveTypes() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(6);

        // Casual
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.CASUAL, null, start, end, 2.0, false, null, LocalDate.now()));

        // WFH
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.WFH, null, start, end, 2.0, false, null, LocalDate.now()));

        // Maternity
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.MATERNITY, null, start, end, 2.0, false, null, LocalDate.now()));

        // Volunteering
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.VOLUNTEERING, null, start, end, 2.0, false, null, LocalDate.now()));

        // Contractor CAN apply eligible type (Sick <= 2 days)
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.SICK, null, start, start, 1.0, false, null, LocalDate.now()));
    }

    @Test
    @DisplayName("Criterion 3: Casual Leave rejected unless combined with nothing, or with WFH only")
    void testCasualLeaveCombinations() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(6);

        // Casual alone is valid
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, null, start, end, 2.0, false, null, LocalDate.now()));

        // Casual combined with WFH is valid
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, LeaveType.WFH, start, end, 2.0, false, null, LocalDate.now()));

        // Casual combined with Sick is rejected
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, LeaveType.SICK, start, end, 2.0, false, null, LocalDate.now()));

        // Casual combined with Paid is rejected
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, LeaveType.PAID, start, end, 2.0, false, null, LocalDate.now()));
    }

    @Test
    @DisplayName("Criterion 3b: Contractor has zero combination rights")
    void testContractorZeroCombinations() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(6);

        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(contractor, LeaveType.SICK, LeaveType.PAID, start, end, 2.0, false, null, LocalDate.now()));
    }

    @Test
    @DisplayName("Criterion 4: Sick Leave > 2 days blocked until medical documents attached")
    void testSickLeaveDocumentationRequirement() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(7); // 3 days

        // 3 days without document -> must throw
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.SICK, null, start, end, 3.0, false, null, LocalDate.now()));

        // 3 days with document -> valid
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(employee, LeaveType.SICK, null, start, end, 3.0, true, "https://doc.pdf", LocalDate.now()));

        // <= 2 days without document -> valid
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(employee, LeaveType.SICK, null, start, start.plusDays(1), 2.0, false, null, LocalDate.now()));
    }

    @Test
    @DisplayName("Criterion 5: Paid Leave rejected if start date isn't more than 2 days out (applied on + 2 < startDate)")
    void testPaidLeaveNoticePeriod() {
        LocalDate appliedDate = LocalDate.of(2026, 9, 10);

        // Start date = appliedDate + 1 day -> rejected
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.PAID, null, appliedDate.plusDays(1), appliedDate.plusDays(2), 2.0, false, null, appliedDate));

        // Start date = appliedDate + 2 days -> rejected (must be MORE than 2 days)
        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.PAID, null, appliedDate.plusDays(2), appliedDate.plusDays(3), 2.0, false, null, appliedDate));

        // Start date = appliedDate + 3 days -> valid (> 2 days)
        assertDoesNotThrow(() ->
                policyValidator.validateLeaveApplication(employee, LeaveType.PAID, null, appliedDate.plusDays(3), appliedDate.plusDays(4), 2.0, false, null, appliedDate));
    }

    @Test
    @DisplayName("Criterion 6: Casual/WFH rejected after end-of-week cutoff for past weeks")
    void testCasualWfhPastWeekCutoff() {
        LocalDate appliedDate = LocalDate.of(2026, 9, 14); // Monday
        LocalDate pastWeekDate = LocalDate.of(2026, 9, 6); // Previous Sunday

        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, null, pastWeekDate, pastWeekDate, 1.0, false, null, appliedDate));
    }

    @Test
    @DisplayName("Criterion 7: Sick/Paid/LOP rejected after 25th of month for current month")
    void testSickPaidLopCutoffAfter25th() {
        LocalDate appliedDate = LocalDate.of(2026, 9, 26); // After 25th
        LocalDate currentMonthDate = LocalDate.of(2026, 9, 28);

        assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.SICK, null, currentMonthDate, currentMonthDate, 1.0, false, null, appliedDate));
    }

    @Test
    @DisplayName("Backdate leave rejected with: You can't apply leave for backdate.")
    void testBackdateLeaveRejected() {
        LocalDate appliedDate = LocalDate.of(2026, 9, 10);
        LocalDate backdate = LocalDate.of(2026, 9, 8);

        PolicyViolationException ex = assertThrows(PolicyViolationException.class, () ->
                policyValidator.validateLeaveApplication(employee, LeaveType.CASUAL, null, backdate, backdate, 1.0, false, null, appliedDate));
        assertEquals("You can't apply leave for backdate.", ex.getMessage());
    }
}
