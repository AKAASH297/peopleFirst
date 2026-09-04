package com.peoplefirst.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.IntentParser;
import com.peoplefirst.agent.service.AgentService;
import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.service.ApprovalService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.dto.CreateLeaveRequestDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.service.PolicyService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.volunteering.service.VolunteeringService;
import com.peoplefirst.wellbeing.service.WellbeingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AgentServiceAgenticTest {

    private GenAiClient genAiClient;
    private CurrentUserProvider currentUserProvider;
    private LeaveService leaveService;
    private LeaveBalanceService leaveBalanceService;
    private LeaveMapper leaveMapper;
    private ApprovalService approvalService;
    private WellbeingService wellbeingService;
    private VolunteeringService volunteeringService;
    private AgentService agentService;
    private User employee;

    @BeforeEach
    void setUp() {
        IntentParser intentParser = new IntentParser();
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        leaveService = Mockito.mock(LeaveService.class);
        leaveBalanceService = Mockito.mock(LeaveBalanceService.class);
        PolicyService policyService = Mockito.mock(PolicyService.class);
        wellbeingService = Mockito.mock(WellbeingService.class);
        leaveMapper = Mockito.mock(LeaveMapper.class);
        genAiClient = Mockito.mock(GenAiClient.class);
        approvalService = Mockito.mock(ApprovalService.class);
        volunteeringService = Mockito.mock(VolunteeringService.class);

        agentService = new AgentService(intentParser, currentUserProvider, leaveService,
                leaveBalanceService, policyService, wellbeingService, leaveMapper, genAiClient, approvalService, volunteeringService);

        employee = new User("emp1", "emp1@test.com", "encodedPass", "Test Employee",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        employee.setId(UUID.randomUUID());
        when(currentUserProvider.getCurrentUser()).thenReturn(employee);
        when(genAiClient.isConfigured()).thenReturn(true);
    }

    @Test
    void balanceQuestionUsesToolAndKeepsDtoContract() throws Exception {
        LeaveBalance balance = Mockito.mock(LeaveBalance.class);
        when(balance.getLeaveType()).thenReturn(LeaveType.SICK);
        when(balance.getRemainingDays()).thenReturn(14.0);
        when(balance.getUsedDays()).thenReturn(2.0);
        when(balance.getPendingDays()).thenReturn(0.0);
        when(balance.getAllocatedDays()).thenReturn(16.0);
        when(leaveBalanceService.getUserBalances(eq(employee.getId()), anyInt()))
                .thenReturn(List.of(balance));
        when(leaveMapper.toBalanceDto(eq(balance), eq(employee)))
                .thenReturn(Mockito.mock(LeaveBalanceDto.class));
        String toolCall = "{\"content\": null, \"tool_calls\": [{\"id\": \"c1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"check_balance\", \"arguments\": \"{}\"}`]}".replace('`', '}');
        String finalReply = "{\"content\": \"You have 14 sick days left.\", \"tool_calls\": []}";
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(toolCall), Optional.of(finalReply));

        AgentChatRequestDto request = new AgentChatRequestDto("how many sick days do I have left?", "test-conv-1");
        AgentChatResponseDto response = agentService.processMessage(request);

        assertTrue(response.isActionExecuted());
        assertEquals("CHECK_BALANCE", response.getActionName());
        assertNotNull(response.getActionData());
        assertNotNull(response.getReply());
        assertNotNull(response.getQuickReplies());
    }

    @Test
    void unconfiguredClientKeepsRuleBasedReply() {
        when(genAiClient.isConfigured()).thenReturn(false);
        AgentChatRequestDto request = new AgentChatRequestDto("hello", "test-conv-2");
        AgentChatResponseDto response = agentService.processMessage(request);
        assertTrue(response.getReply().contains("Kura"));
    }

    @Test
    void statusReportsAgenticModeWithoutLeakingSecrets() {
        when(genAiClient.isConfigured()).thenReturn(true);
        Map<String, Object> status = agentService.getAgentStatus();
        assertEquals("agentic", status.get("agentMode"));
        assertFalse(status.values().stream().anyMatch(v -> "test-key-not-sk".equals(v)));
    }

    @Test
    void statusReportsRuleBasedWhenUnconfigured() {
        when(genAiClient.isConfigured()).thenReturn(false);
        Map<String, Object> status = agentService.getAgentStatus();
        assertEquals("rule-based", status.get("agentMode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyIsScopedPerUserForSameConversationId() {
        LeaveBalance balance = Mockito.mock(LeaveBalance.class);
        when(balance.getLeaveType()).thenReturn(LeaveType.SICK);
        when(balance.getRemainingDays()).thenReturn(14.0);
        when(balance.getUsedDays()).thenReturn(2.0);
        when(balance.getPendingDays()).thenReturn(0.0);
        when(balance.getAllocatedDays()).thenReturn(16.0);
        when(leaveBalanceService.getUserBalances(eq(employee.getId()), anyInt()))
                .thenReturn(List.of(balance));
        when(leaveMapper.toBalanceDto(eq(balance), eq(employee)))
                .thenReturn(Mockito.mock(LeaveBalanceDto.class));
        String toolCall = "{\"content\": null, \"tool_calls\": [{\"id\": \"c1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"check_balance\", \"arguments\": \"{}\"}`]}".replace('`', '}');
        String finalReply = "{\"content\": \"done.\", \"tool_calls\": []}";
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(toolCall), Optional.of(finalReply));

        String userAMessage = "how many sick days left for alpha-user-seven?";
        agentService.processMessage(new AgentChatRequestDto(userAMessage, "shared-conv"));

        User userB = new User("emp2", "emp2@test.com", "encodedPass", "Second User",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        userB.setId(UUID.randomUUID());
        when(currentUserProvider.getCurrentUser()).thenReturn(userB);
        agentService.processMessage(new AgentChatRequestDto("hello", "shared-conv"));

        ArgumentCaptor<List<Map<String, String>>> historyCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(genAiClient, Mockito.atLeastOnce())
                .chatWithTools(anyString(), historyCaptor.capture(), anyList());
        List<List<Map<String, String>>> allHistories = historyCaptor.getAllValues();
        List<Map<String, String>> lastHistory = allHistories.get(allHistories.size() - 1);
        assertTrue(lastHistory.stream()
                        .map(m -> m.get("content"))
                        .filter(c -> c != null)
                        .noneMatch(c -> c.contains("alpha-user-seven")),
                "User B history must not contain User A message text");
    }

    private String applyLeaveToolCall() {
        String args = "{\\\"leaveType\\\":\\\"SICK\\\",\\\"startDate\\\":\\\"2030-01-06\\\","
                + "\\\"endDate\\\":\\\"2030-01-07\\\",\\\"reason\\\":\\\"test via agent\\\"}";
        return "{\"content\": null, \"tool_calls\": [{\"id\": \"c1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"apply_leave\", \"arguments\": \"" + args + "\"}}]}";
    }

    private void stubApplyLeaveExecution() {
        LeaveResponseDto created = Mockito.mock(LeaveResponseDto.class);
        when(created.getLeaveTypeDisplayName()).thenReturn("Sick Leave");
        when(created.getStartDate()).thenReturn(LocalDate.of(2030, 1, 6));
        when(created.getEndDate()).thenReturn(LocalDate.of(2030, 1, 7));
        when(leaveService.applyLeave(any(), any())).thenReturn(created);
    }

    @Test
    void yesterdayPrefixedMessageDoesNotConfirmPendingApply() {
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(applyLeaveToolCall()));
        stubApplyLeaveExecution();

        AgentChatResponseDto proposal = agentService.processMessage(
                new AgentChatRequestDto("i want to apply for leave", "conv-confirm-a"));
        assertFalse(proposal.isActionExecuted());

        // The old gate over-triggered on "yesterday…" via startsWith("yes"); must NOT confirm.
        AgentChatResponseDto afterYesterday = agentService.processMessage(
                new AgentChatRequestDto("yesterday we leave for a trip", "conv-confirm-a"));
        // The brief's literal substring case must also not resolve the gate.
        AgentChatResponseDto afterLiteral = agentService.processMessage(
                new AgentChatRequestDto("we leave yesterday", "conv-confirm-a"));
        Mockito.verify(leaveService, Mockito.never()).applyLeave(any(), any());
        assertFalse(afterYesterday.isActionExecuted());
        assertFalse(afterLiteral.isActionExecuted());
    }

    @Test
    void notSureDoesNotDiscardPendingApplyAndYesStillExecutes() {
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(applyLeaveToolCall()));
        stubApplyLeaveExecution();

        agentService.processMessage(new AgentChatRequestDto("i want to apply for leave", "conv-confirm-b"));
        // The old gate discarded on "not sure…" via startsWith("no"); must NOT discard.
        agentService.processMessage(new AgentChatRequestDto("not sure about that", "conv-confirm-b"));
        Mockito.verify(leaveService, Mockito.never()).applyLeave(any(), any());

        AgentChatResponseDto confirmed = agentService.processMessage(
                new AgentChatRequestDto("yes", "conv-confirm-b"));
        assertTrue(confirmed.isActionExecuted());
        Mockito.verify(leaveService, Mockito.times(1)).applyLeave(any(), eq(employee));
    }

    @Test
    void yesExecutesPendingCancelLeave() {
        String cancelToolCall = "{\"content\": null, \"tool_calls\": [{\"id\": \"c1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"cancel_leave\", \"arguments\": \"{}\"}`]}".replace('`', '}');
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(cancelToolCall));

        LeaveResponseDto upcoming = Mockito.mock(LeaveResponseDto.class);
        when(upcoming.getId()).thenReturn(UUID.randomUUID());
        when(upcoming.getStatus()).thenReturn(LeaveStatus.PENDING);
        when(upcoming.getStartDate()).thenReturn(LocalDate.now().plusDays(5));
        when(leaveService.getLeavesForUser(eq(employee.getId()))).thenReturn(List.of(upcoming));
        LeaveResponseDto cancelled = Mockito.mock(LeaveResponseDto.class);
        when(cancelled.getLeaveTypeDisplayName()).thenReturn("Sick Leave");
        when(cancelled.getStartDate()).thenReturn(LocalDate.now().plusDays(5));
        when(cancelled.getEndDate()).thenReturn(LocalDate.now().plusDays(6));
        when(leaveService.cancelLeave(any(), any(), any())).thenReturn(cancelled);

        agentService.processMessage(new AgentChatRequestDto("cancel my upcoming leave", "conv-confirm-c"));
        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("yes", "conv-confirm-c"));
        Mockito.verify(leaveService, Mockito.times(1)).cancelLeave(any(), eq(employee), anyString());
        assertTrue(response.isActionExecuted());
    }

    @Test
    void overlongMessageIsRefusedWithoutLlmCost() {
        when(genAiClient.isConfigured()).thenReturn(true);
        String longMessage = "x".repeat(2001);
        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto(longMessage, "conv-limit-a"));
        assertTrue(response.getReply().contains("2000"));
        assertFalse(response.isActionExecuted());
        Mockito.verifyNoInteractions(genAiClient);
    }

    @Test
    void exactly2000CharsStillGetsRuleGreeting() {
        when(genAiClient.isConfigured()).thenReturn(false);
        String message = "hello" + "x".repeat(1995);
        assertEquals(2000, message.length());
        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto(message, "conv-limit-b"));
        assertTrue(response.getReply().contains("Kura"));
    }

    private User managerUser() {
        User manager = new User("mgr1", "mgr1@test.com", "encodedPass", "Test Manager",
                Role.MANAGER, false, "Eng", "Bangalore", UUID.randomUUID());
        manager.setId(UUID.randomUUID());
        return manager;
    }

    private LeaveResponseDto pendingLeave(UUID id, String employeeName, String typeDisplay,
                                          LocalDate start, LocalDate end, double days) {
        LeaveResponseDto dto = Mockito.mock(LeaveResponseDto.class);
        when(dto.getId()).thenReturn(id);
        when(dto.getEmployeeName()).thenReturn(employeeName);
        when(dto.getLeaveTypeDisplayName()).thenReturn(typeDisplay);
        when(dto.getStartDate()).thenReturn(start);
        when(dto.getEndDate()).thenReturn(end);
        when(dto.getTotalDays()).thenReturn(days);
        return dto;
    }

    @Test
    void managerListsPendingApprovalsInChat() {
        when(genAiClient.isConfigured()).thenReturn(false);
        User manager = managerUser();
        when(currentUserProvider.getCurrentUser()).thenReturn(manager);
        LeaveResponseDto first = pendingLeave(UUID.randomUUID(), "Alice", "Casual Leave",
                LocalDate.of(2030, 2, 3), LocalDate.of(2030, 2, 4), 2.0);
        LeaveResponseDto second = pendingLeave(UUID.randomUUID(), "Bob", "Sick Leave",
                LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 10), 1.0);
        when(approvalService.getPendingApprovals(eq(manager))).thenReturn(List.of(first, second));

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("show pending approvals", "conv-approve-a"));

        assertEquals("APPROVE_LEAVES", response.getIntent());
        assertTrue(response.getReply().contains("1."));
        assertTrue(response.getReply().contains("2."));
        assertTrue(response.getReply().contains("Alice"));
        assertTrue(response.getReply().contains("Bob"));
    }

    @Test
    void managerApprovesByNumberWithExistingAuthority() {
        when(genAiClient.isConfigured()).thenReturn(false);
        User manager = managerUser();
        when(currentUserProvider.getCurrentUser()).thenReturn(manager);
        UUID leaveId = UUID.randomUUID();
        LeaveResponseDto pending = pendingLeave(leaveId, "Alice", "Casual Leave",
                LocalDate.of(2030, 2, 3), LocalDate.of(2030, 2, 4), 2.0);
        when(approvalService.getPendingApprovals(eq(manager))).thenReturn(List.of(pending));
        LeaveResponseDto approved = pendingLeave(leaveId, "Alice", "Casual Leave",
                LocalDate.of(2030, 2, 3), LocalDate.of(2030, 2, 4), 2.0);
        when(approvalService.approveLeave(eq(leaveId), any(ApprovalActionDto.class), eq(manager)))
                .thenReturn(approved);

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("approve 1", "conv-approve-b"));

        assertTrue(response.isActionExecuted());
        assertEquals("APPROVE_LEAVE", response.getActionName());
        assertTrue(response.getReply().contains("Approved"));
        Mockito.verify(approvalService, Mockito.times(1))
                .approveLeave(eq(leaveId), any(ApprovalActionDto.class), eq(manager));
    }

    @Test
    void nonManagerGetsEmptyApprovalsMessage() {
        when(genAiClient.isConfigured()).thenReturn(false);
        when(approvalService.getPendingApprovals(eq(employee))).thenReturn(List.of());

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("show pending approvals", "conv-approve-c"));

        assertTrue(response.getReply().toLowerCase().contains("no pending approvals"));
        assertFalse(response.isActionExecuted());
    }

    @Test
    void agenticApproveGoesThroughConfirmGate() {
        User manager = managerUser();
        when(currentUserProvider.getCurrentUser()).thenReturn(manager);
        UUID leaveId = UUID.randomUUID();
        String args = "{\\\"leaveId\\\":\\\"" + leaveId + "\\\",\\\"comment\\\":\\\"ok\\\"}";
        String toolCall = "{\"content\": null, \"tool_calls\": [{\"id\": \"c1\", \"type\": \"function\", "
                + "\"function\": {\"name\": \"approve_leave\", \"arguments\": \"" + args + "\"}}]}";
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of(toolCall));
        LeaveResponseDto approved = pendingLeave(leaveId, "Alice", "Casual Leave",
                LocalDate.of(2030, 2, 3), LocalDate.of(2030, 2, 4), 2.0);
        when(approvalService.approveLeave(eq(leaveId), any(ApprovalActionDto.class), eq(manager)))
                .thenReturn(approved);

        AgentChatResponseDto proposal = agentService.processMessage(
                new AgentChatRequestDto("please approve alice's leave", "conv-approve-d"));

        assertFalse(proposal.isActionExecuted());
        Mockito.verifyNoInteractions(approvalService);

        AgentChatResponseDto confirmed = agentService.processMessage(
                new AgentChatRequestDto("yes", "conv-approve-d"));

        Mockito.verify(approvalService, Mockito.times(1))
                .approveLeave(eq(leaveId), any(ApprovalActionDto.class), eq(manager));
        assertTrue(confirmed.isActionExecuted());
    }

    private LeaveResponseDto halfDayCreatedDto() {
        LeaveResponseDto created = Mockito.mock(LeaveResponseDto.class);
        when(created.getId()).thenReturn(UUID.randomUUID());
        when(created.getLeaveTypeDisplayName()).thenReturn("Sick Leave");
        when(created.getStartDate()).thenReturn(LocalDate.now().plusDays(1));
        when(created.getEndDate()).thenReturn(LocalDate.now().plusDays(1));
        when(created.getTotalDays()).thenReturn(0.5);
        return created;
    }

    @Test
    void halfDaySessionIsAskedWhenMissing() {
        when(genAiClient.isConfigured()).thenReturn(false);
        LeaveResponseDto created = halfDayCreatedDto();
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);

        AgentChatResponseDto question = agentService.processMessage(
                new AgentChatRequestDto("apply half day sick leave tomorrow", "conv-half-a"));

        assertEquals("APPLY_LEAVE", question.getIntent());
        assertTrue(question.getReply().contains("First half"));
        assertFalse(question.isActionExecuted());

        AgentChatResponseDto applied = agentService.processMessage(
                new AgentChatRequestDto("First half (morning)", "conv-half-a"));
        assertTrue(applied.isActionExecuted());
    }

    @Test
    void morningMapsToFirstHalfAndApplies() {
        when(genAiClient.isConfigured()).thenReturn(false);
        LeaveResponseDto created = halfDayCreatedDto();
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("apply half day sick leave tomorrow morning", "conv-half-b"));

        assertTrue(response.isActionExecuted());
        ArgumentCaptor<CreateLeaveRequestDto> captor = ArgumentCaptor.forClass(CreateLeaveRequestDto.class);
        Mockito.verify(leaveService).applyLeave(captor.capture(), eq(employee));
        assertTrue(captor.getValue().isHalfDay());
        assertEquals("FIRST_HALF", captor.getValue().getHalfDaySession());
    }

    @Test
    void afternoonMapsToSecondHalf() {
        when(genAiClient.isConfigured()).thenReturn(false);
        LeaveResponseDto created = halfDayCreatedDto();
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);

        agentService.processMessage(
                new AgentChatRequestDto("apply half day sick leave tomorrow", "conv-half-c"));
        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("tomorrow afternoon", "conv-half-c"));

        assertTrue(response.isActionExecuted());
        ArgumentCaptor<CreateLeaveRequestDto> captor = ArgumentCaptor.forClass(CreateLeaveRequestDto.class);
        Mockito.verify(leaveService).applyLeave(captor.capture(), eq(employee));
        assertEquals("SECOND_HALF", captor.getValue().getHalfDaySession());
    }

    @Test
    void halfDaySickNudgesSickRoomFloor6Room7() {
        when(genAiClient.isConfigured()).thenReturn(false);
        LeaveResponseDto created = halfDayCreatedDto();
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);
        when(wellbeingService.evaluateLeaveWellbeing(any(), eq(employee))).thenReturn(List.of());

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("apply half day sick leave tomorrow morning", "conv-half-d"));

        assertTrue(response.isActionExecuted());
        assertTrue(response.getReply().contains("Floor 6"));
        assertTrue(response.getReply().contains("Room 7"));
    }

    private LeaveResponseDto volunteeringCreatedDto(UUID leaveId) {
        LeaveResponseDto created = Mockito.mock(LeaveResponseDto.class);
        when(created.getId()).thenReturn(leaveId);
        when(created.getLeaveTypeDisplayName()).thenReturn("Volunteering Leave");
        when(created.getStartDate()).thenReturn(LocalDate.now().plusDays(3));
        when(created.getEndDate()).thenReturn(LocalDate.now().plusDays(3));
        when(created.getTotalDays()).thenReturn(1.0);
        return created;
    }

    @Test
    void volunteeringApplyOffersCsrGroups() {
        when(genAiClient.isConfigured()).thenReturn(false);
        UUID leaveId = UUID.randomUUID();
        LeaveResponseDto created = volunteeringCreatedDto(leaveId);
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);
        when(wellbeingService.evaluateLeaveWellbeing(any(), eq(employee))).thenReturn(List.of());

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("apply volunteering leave tomorrow", "conv-vol-a"));

        assertTrue(response.isActionExecuted());
        assertTrue(response.getReply().contains("Paws & Care Animal Rescue"));
        assertTrue(response.getReply().toLowerCase().contains("enroll you"));
        assertTrue(response.getReply().toLowerCase().contains("intranet banner"));
    }

    @Test
    void volunteeringSignupEnrollsNamedGroupWithBanner() {
        when(genAiClient.isConfigured()).thenReturn(false);
        UUID leaveId = UUID.randomUUID();
        LeaveResponseDto created = volunteeringCreatedDto(leaveId);
        when(leaveService.applyLeave(any(), eq(employee))).thenReturn(created);
        when(wellbeingService.evaluateLeaveWellbeing(any(), eq(employee))).thenReturn(List.of());

        agentService.processMessage(
                new AgentChatRequestDto("apply volunteering leave tomorrow", "conv-vol-b"));
        AgentChatResponseDto enrolled = agentService.processMessage(
                new AgentChatRequestDto("Paws & Care Animal Rescue and feature me", "conv-vol-b"));

        Mockito.verify(volunteeringService, Mockito.times(1))
                .enroll(eq(employee.getId()), eq("Paws & Care Animal Rescue"), eq(leaveId), eq(true));
        assertTrue(enrolled.getReply().contains("Paws & Care Animal Rescue"));
        assertTrue(enrolled.getReply().toLowerCase().contains("intranet banner"));
    }

    @Test
    void configuredButEndpointDownReturnsUnavailableNotRuleReply() {
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.empty());

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("hello", "conv-unavail-a"));

        assertTrue(response.getReply().contains("isn't reachable"));
        assertFalse(response.isActionExecuted());
    }

    @Test
    void configuredButGarbageResponseReturnsUnavailable() {
        when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
                .thenReturn(Optional.of("not-json{{{"));

        AgentChatResponseDto response = agentService.processMessage(
                new AgentChatRequestDto("how many sick days do I have left?", "conv-unavail-b"));

        assertTrue(response.getReply().contains("isn't reachable"));
        assertFalse(response.isActionExecuted());
    }
}
