package com.peoplefirst.approval;

import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.service.ApprovalService;
import com.peoplefirst.approval.validator.ApprovalValidator;
import com.peoplefirst.audit.service.AuditService;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    private LeaveService leaveService;
    private LeaveRequestRepository leaveRequestRepository;
    private LeaveBalanceService leaveBalanceService;
    private ApprovalValidator approvalValidator;
    private AuditService auditService;
    private UserService userService;
    private LeaveMapper leaveMapper;
    private ApprovalService approvalService;

    private User managerA;
    private User employeeA; // Reports to managerA
    private User employeeB; // Reports to another manager
    private User admin1;
    private User admin2;

    @BeforeEach
    void setUp() {
        leaveService = Mockito.mock(LeaveService.class);
        leaveRequestRepository = Mockito.mock(LeaveRequestRepository.class);
        leaveBalanceService = Mockito.mock(LeaveBalanceService.class);
        approvalValidator = new ApprovalValidator();
        auditService = Mockito.mock(AuditService.class);
        userService = Mockito.mock(UserService.class);
        leaveMapper = new LeaveMapper();

        approvalService = new ApprovalService(
                leaveService, leaveRequestRepository, leaveBalanceService,
                approvalValidator, auditService, userService, leaveMapper
        );

        managerA = new User("mgrA", "mgrA@test.com", "hash", "Manager A", Role.MANAGER, false, "Eng", "Bangalore", null);
        managerA.setId(UUID.randomUUID());

        employeeA = new User("empA", "empA@test.com", "hash", "Employee A", Role.EMPLOYEE, false, "Eng", "Bangalore", managerA.getId());
        employeeA.setId(UUID.randomUUID());

        employeeB = new User("empB", "empB@test.com", "hash", "Employee B", Role.EMPLOYEE, false, "Prod", "Hyderabad", UUID.randomUUID());
        employeeB.setId(UUID.randomUUID());

        admin1 = new User("admin1", "admin1@test.com", "hash", "Admin 1", Role.ADMIN, false, "HR", "Bangalore", null);
        admin1.setId(UUID.randomUUID());

        admin2 = new User("admin2", "admin2@test.com", "hash", "Admin 2", Role.ADMIN, false, "HR", "Bangalore", null);
        admin2.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Criterion 11: Admin cannot approve their own leave")
    void testAdminCannotApproveOwnLeave() {
        UUID leaveId = UUID.randomUUID();
        LeaveRequest adminLeave = new LeaveRequest(
                admin1.getId(), LeaveType.PAID, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Vacation", null, false, LocalDate.now()
        );
        adminLeave.setId(leaveId);

        when(leaveService.getLeaveEntityById(leaveId)).thenReturn(adminLeave);
        when(userService.getUserEntityById(admin1.getId())).thenReturn(admin1);

        assertThrows(PolicyViolationException.class, () ->
                approvalService.approveLeave(leaveId, new ApprovalActionDto("Self approval attempt"), admin1));
    }

    @Test
    @DisplayName("Admin 2 can approve Admin 1's leave")
    void testAdmin2CanApproveAdmin1Leave() {
        UUID leaveId = UUID.randomUUID();
        LeaveRequest adminLeave = new LeaveRequest(
                admin1.getId(), LeaveType.PAID, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Vacation", null, false, LocalDate.now()
        );
        adminLeave.setId(leaveId);

        when(leaveService.getLeaveEntityById(leaveId)).thenReturn(adminLeave);
        when(userService.getUserEntityById(admin1.getId())).thenReturn(admin1);
        when(leaveRequestRepository.save(any())).thenReturn(adminLeave);

        assertDoesNotThrow(() ->
                approvalService.approveLeave(leaveId, new ApprovalActionDto("Approved by peer admin"), admin2));
    }

    @Test
    @DisplayName("Criterion 8: Manager can approve direct reportee, but cannot approve other team's leave")
    void testManagerScoping() {
        UUID leaveIdA = UUID.randomUUID();
        LeaveRequest leaveA = new LeaveRequest(
                employeeA.getId(), LeaveType.CASUAL, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Personal", null, false, LocalDate.now()
        );
        leaveA.setId(leaveIdA);

        when(leaveService.getLeaveEntityById(leaveIdA)).thenReturn(leaveA);
        when(userService.getUserEntityById(employeeA.getId())).thenReturn(employeeA);
        when(leaveRequestRepository.save(any())).thenReturn(leaveA);

        // Manager A approving direct reportee Employee A -> Valid
        assertDoesNotThrow(() ->
                approvalService.approveLeave(leaveIdA, new ApprovalActionDto("Approved reportee"), managerA));

        // Manager A attempting to approve Employee B (other team) -> AccessDenied
        UUID leaveIdB = UUID.randomUUID();
        LeaveRequest leaveB = new LeaveRequest(
                employeeB.getId(), LeaveType.CASUAL, null,
                LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 2.0,
                false, null, "Personal", null, false, LocalDate.now()
        );
        leaveB.setId(leaveIdB);

        when(leaveService.getLeaveEntityById(leaveIdB)).thenReturn(leaveB);
        when(userService.getUserEntityById(employeeB.getId())).thenReturn(employeeB);

        assertThrows(AccessDeniedException.class, () ->
                approvalService.approveLeave(leaveIdB, new ApprovalActionDto("Illegal approve"), managerA));
    }
}
