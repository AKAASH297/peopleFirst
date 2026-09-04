package com.peoplefirst.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.IntentParser;
import com.peoplefirst.agent.service.AgentService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.service.PolicyService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
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
    private AgentService agentService;
    private User employee;

    @BeforeEach
    void setUp() {
        IntentParser intentParser = new IntentParser();
        currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        leaveService = Mockito.mock(LeaveService.class);
        leaveBalanceService = Mockito.mock(LeaveBalanceService.class);
        PolicyService policyService = Mockito.mock(PolicyService.class);
        WellbeingService wellbeingService = Mockito.mock(WellbeingService.class);
        leaveMapper = Mockito.mock(LeaveMapper.class);
        genAiClient = Mockito.mock(GenAiClient.class);

        agentService = new AgentService(intentParser, currentUserProvider, leaveService,
                leaveBalanceService, policyService, wellbeingService, leaveMapper, genAiClient);

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
}
