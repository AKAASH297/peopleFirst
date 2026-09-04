package com.peoplefirst.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.IntentParser;
import com.peoplefirst.agent.service.AgentService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.entity.LeaveBalance;
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
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AgentServiceAgenticTest {

    private GenAiClient genAiClient;
    private LeaveBalanceService leaveBalanceService;
    private LeaveMapper leaveMapper;
    private AgentService agentService;
    private User employee;

    @BeforeEach
    void setUp() {
        IntentParser intentParser = new IntentParser();
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        LeaveService leaveService = Mockito.mock(LeaveService.class);
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
}
