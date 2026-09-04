package com.peoplefirst.leave;

import com.peoplefirst.audit.entity.LeaveAuditLog;
import com.peoplefirst.audit.service.AuditService;
import com.peoplefirst.leave.dto.AdminDirectEditDto;
import com.peoplefirst.leave.dto.CreateLeaveRequestDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.dto.UpdateLeaveRequestDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.leave.validator.LeaveValidator;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.validator.PolicyValidator;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveServiceTest {

    private LeaveRequestRepository leaveRequestRepository;
    private LeaveBalanceService leaveBalanceService;
    private PolicyValidator policyValidator;
    private LeaveValidator leaveValidator;
    private LeaveMapper leaveMapper;
    private AuditService auditService;
    private UserService userService;
    private LeaveService leaveService;

    private User employee;
    private User admin;

    @BeforeEach
    void setUp() {
        leaveRequestRepository = Mockito.mock(LeaveRequestRepository.class);
        leaveBalanceService = Mockito.mock(LeaveBalanceService.class);
        policyValidator = new PolicyValidator();
        leaveValidator = new LeaveValidator(leaveRequestRepository);
        leaveMapper = new LeaveMapper();
        auditService = Mockito.mock(AuditService.class);
        userService = Mockito.mock(UserService.class);

        leaveService = new LeaveService(
                leaveRequestRepository, leaveBalanceService, policyValidator,
                leaveValidator, leaveMapper, auditService, userService
        );

        employee = new User("emp1", "emp1@test.com", "hash", "Test Employee",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        employee.setId(UUID.randomUUID());

        admin = new User("admin1", "admin1@test.com", "hash", "Test Admin",
                Role.ADMIN, false, "HR", "Bangalore", null);
        admin.setId(UUID.randomUUID());

        when(userService.getUserEntityById(employee.getId())).thenReturn(employee);
        when(userService.getUserEntityById(admin.getId())).thenReturn(admin);
    }

    @Test
    @DisplayName("Leave application reserves pending balance and creates audit trail")
    void testApplyLeave() {
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.CASUAL);
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(6));
        dto.setReason("Family function");

        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        LeaveResponseDto result = leaveService.applyLeave(dto, employee);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        assertEquals(2.0, result.getTotalDays());

        // Verify pending balance was reserved
        verify(leaveBalanceService).reservePendingDays(eq(employee), eq(LeaveType.CASUAL), eq(2.0), anyInt());

        // Verify audit log recorded
        verify(auditService).recordAuditLog(
                any(), eq(employee.getId()), eq(employee.getFullName()), eq("EMPLOYEE"),
                eq("APPLY"), isNull(), eq("PENDING"), eq("Family function"), eq(false), eq(false)
        );
    }

    @Test
    @DisplayName("Leave cancellation before start date restores balance")
    void testCancelLeaveRestoresBalance() {
        UUID leaveId = UUID.randomUUID();
        LeaveRequest approvedLeave = new LeaveRequest(
                employee.getId(), LeaveType.PAID, null,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), 3.0,
                false, null, "Vacation", null, false, LocalDate.now()
        );
        approvedLeave.setId(leaveId);
        approvedLeave.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(approvedLeave));
        when(leaveRequestRepository.save(any())).thenReturn(approvedLeave);

        LeaveResponseDto result = leaveService.cancelLeave(leaveId, employee, "Trip cancelled");

        assertEquals(LeaveStatus.CANCELLED, result.getStatus());

        // Verify balance restoration
        verify(leaveBalanceService).restoreDaysOnCancel(eq(employee), eq(LeaveType.PAID), eq(3.0), eq(true), anyInt());

        // Verify audit log
        verify(auditService).recordAuditLog(
                eq(leaveId), eq(employee.getId()), eq(employee.getFullName()), eq("EMPLOYEE"),
                eq("CANCEL"), eq("APPROVED"), eq("CANCELLED"), eq("Trip cancelled"), eq(false), eq(false)
        );
    }

    @Test
    @DisplayName("Criterion 10: Admin direct-DB-edit is a distinct, audited operation with ADMIN_DIRECT_EDIT")
    void testAdminDirectEditAuditedDistinctly() {
        UUID leaveId = UUID.randomUUID();
        LeaveRequest leave = new LeaveRequest(
                employee.getId(), LeaveType.SICK, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Flu", null, false, LocalDate.now()
        );
        leave.setId(leaveId);
        leave.setStatus(LeaveStatus.PENDING);

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(leave));
        when(leaveRequestRepository.save(any())).thenReturn(leave);

        AdminDirectEditDto directEditDto = new AdminDirectEditDto();
        directEditDto.setStatus(LeaveStatus.APPROVED);
        directEditDto.setAuditComment("Executive override per VP approval");

        LeaveResponseDto result = leaveService.adminDirectEdit(leaveId, directEditDto, admin);

        assertEquals(LeaveStatus.APPROVED, result.getStatus());

        // Verify audit log has ADMIN_DIRECT_EDIT and adminDirectEdit flag = true
        verify(auditService).recordAuditLog(
                eq(leaveId), eq(admin.getId()), eq(admin.getFullName()), eq("ADMIN"),
                eq("ADMIN_DIRECT_EDIT"), eq("PENDING"), eq("APPROVED"),
                eq("Executive override per VP approval"), eq(false), eq(true)
        );
    }

    @Test
    @DisplayName("Returned leave can be edited and resubmitted to PENDING")
    void testReturnedLeaveEditAndResubmit() {
        UUID leaveId = UUID.randomUUID();
        LeaveRequest returnedLeave = new LeaveRequest(
                employee.getId(), LeaveType.CASUAL, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Original reason", null, false, LocalDate.now()
        );
        returnedLeave.setId(leaveId);
        returnedLeave.setStatus(LeaveStatus.RETURNED);

        when(leaveRequestRepository.findById(leaveId)).thenReturn(Optional.of(returnedLeave));
        when(leaveRequestRepository.save(any())).thenReturn(returnedLeave);

        UpdateLeaveRequestDto editDto = new UpdateLeaveRequestDto();
        editDto.setLeaveType(LeaveType.CASUAL);
        editDto.setStartDate(LocalDate.now().plusDays(6));
        editDto.setEndDate(LocalDate.now().plusDays(7));
        editDto.setReason("Updated dates per manager feedback");

        LeaveResponseDto result = leaveService.editLeave(leaveId, editDto, employee);

        assertEquals(LeaveStatus.PENDING, result.getStatus());
        verify(auditService).recordAuditLog(
                eq(leaveId), eq(employee.getId()), eq(employee.getFullName()), eq("EMPLOYEE"),
                eq("EDIT"), eq("RETURNED"), eq("PENDING"), anyString(), eq(false), eq(false)
        );
    }

    @Test
    @DisplayName("Second same-day apply after approval is rejected")
    void testSameDayDoubleBookingRejected() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        LeaveRequest existing = new LeaveRequest(
                employee.getId(), LeaveType.SICK, null,
                day, day, 1.0,
                false, null, "Flu", null, false, LocalDate.now()
        );
        existing.setId(UUID.randomUUID());
        existing.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(employee.getId()), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)), eq(day), eq(day)))
                .thenReturn(List.of(existing));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.SICK);
        dto.setStartDate(day);
        dto.setEndDate(day);
        dto.setReason("Still sick");

        assertThrows(PolicyViolationException.class, () -> leaveService.applyLeave(dto, employee));
    }

    @Test
    @DisplayName("Overlapping PENDING leave blocks a second apply")
    void testPendingOverlapRejected() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        LeaveRequest existing = new LeaveRequest(
                employee.getId(), LeaveType.SICK, null,
                day, day, 1.0,
                false, null, "Flu", null, false, LocalDate.now()
        );
        existing.setId(UUID.randomUUID());
        existing.setStatus(LeaveStatus.PENDING);

        when(leaveRequestRepository.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(employee.getId()), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)), eq(day), eq(day)))
                .thenReturn(List.of(existing));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.SICK);
        dto.setStartDate(day);
        dto.setEndDate(day);
        dto.setReason("Second application");

        assertThrows(PolicyViolationException.class, () -> leaveService.applyLeave(dto, employee));
    }

    @Test
    @DisplayName("Complementary half-day sessions on the same day are allowed")
    void testComplementaryHalfDaysAllowed() {
        LocalDate day = LocalDate.of(2026, 10, 6);
        LeaveRequest existing = new LeaveRequest(
                employee.getId(), LeaveType.SICK, null,
                day, day, 0.5,
                true, "FIRST_HALF", "Morning fever", null, false, LocalDate.now()
        );
        existing.setId(UUID.randomUUID());
        existing.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(employee.getId()), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)), eq(day), eq(day)))
                .thenReturn(List.of(existing));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.SICK);
        dto.setStartDate(day);
        dto.setEndDate(day);
        dto.setHalfDay(true);
        dto.setHalfDaySession("SECOND_HALF");
        dto.setReason("Afternoon rest");

        LeaveResponseDto result = leaveService.applyLeave(dto, employee);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
    }

    @Test
    @DisplayName("Same half-day session twice is rejected")
    void testSameHalfDaySessionRejected() {
        LocalDate day = LocalDate.of(2026, 10, 6);
        LeaveRequest existing = new LeaveRequest(
                employee.getId(), LeaveType.SICK, null,
                day, day, 0.5,
                true, "FIRST_HALF", "Morning fever", null, false, LocalDate.now()
        );
        existing.setId(UUID.randomUUID());
        existing.setStatus(LeaveStatus.APPROVED);

        when(leaveRequestRepository.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(employee.getId()), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)), eq(day), eq(day)))
                .thenReturn(List.of(existing));
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.SICK);
        dto.setStartDate(day);
        dto.setEndDate(day);
        dto.setHalfDay(true);
        dto.setHalfDaySession("FIRST_HALF");
        dto.setReason("Duplicate morning session");

        assertThrows(PolicyViolationException.class, () -> leaveService.applyLeave(dto, employee));
    }

    @Test
    @DisplayName("Cancelled/rejected leaves do not block")
    void testCancelledLeavesDoNotBlock() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        // The overlap query filters statuses to PENDING/APPROVED only, so a day
        // holding just a CANCELLED full-day leave yields no clashes.
        when(leaveRequestRepository.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(employee.getId()), eq(List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)), eq(day), eq(day)))
                .thenReturn(List.of());
        when(leaveRequestRepository.save(any())).thenAnswer(invocation -> {
            LeaveRequest req = invocation.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.SICK);
        dto.setStartDate(day);
        dto.setEndDate(day);
        dto.setReason("Fresh application after cancellation");

        LeaveResponseDto result = leaveService.applyLeave(dto, employee);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
    }
}
