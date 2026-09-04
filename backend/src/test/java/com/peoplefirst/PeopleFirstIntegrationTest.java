package com.peoplefirst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.auth.dto.LoginRequestDto;
import com.peoplefirst.leave.dto.AdminDirectEditDto;
import com.peoplefirst.leave.dto.CreateLeaveRequestDto;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.policy.entity.LeaveType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PeopleFirstIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String getJwtToken(String username, String password, String channel) throws Exception {
        LoginRequestDto loginDto = new LoginRequestDto(username, password, channel);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) map.get("accessToken");
    }

    @Test
    @DisplayName("Criterion 1: Contractor web login rejected (403), agent login allowed (200)")
    void testContractorWebVsAgentLogin() throws Exception {
        // Attempt web login with contractor account -> 403 Forbidden
        LoginRequestDto webLogin = new LoginRequestDto("contractor1", "password123", "WEB");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webLogin)))
                .andExpect(status().isForbidden());

        // Agent login with contractor account -> 200 OK
        LoginRequestDto agentLogin = new LoginRequestDto("contractor1", "password123", "AGENT");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(agentLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.contractor").value(true));
    }

    @Test
    @DisplayName("Criterion 13: Company Leave Policies endpoint returns structured data")
    void testPoliciesEndpoint() throws Exception {
        String token = getJwtToken("employee1", "password123", "WEB");

        mockMvc.perform(get("/api/policies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlineRules").isArray())
                .andExpect(jsonPath("$.combinationRules").isArray())
                .andExpect(jsonPath("$.leaveTypes").isArray());
    }

    @Test
    @DisplayName("Apply valid Casual Leave with WFH combination -> 200 OK")
    void testApplyCasualWithWfh() throws Exception {
        String token = getJwtToken("employee1", "password123", "WEB");

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(LeaveType.CASUAL);
        dto.setCombinedWithType(LeaveType.WFH);
        dto.setStartDate(LocalDate.now().plusDays(5));
        dto.setEndDate(LocalDate.now().plusDays(6));
        dto.setReason("Remote sprint day");

        mockMvc.perform(post("/api/leaves")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.combinedWithType").value("WFH"));
    }

    @Test
    @DisplayName("Kura AI Agent chat endpoint executes tools and resolves identity from SecurityContext")
    void testAgentChatEndpoint() throws Exception {
        String token = getJwtToken("contractor1", "password123", "AGENT");

        AgentChatRequestDto chatDto = new AgentChatRequestDto("What are my leave balances?", "conv-1");

        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.actionExecuted").value(true));
    }

    @Test
    @DisplayName("Admin direct-DB-edit endpoint updates record and generates distinct audit")
    void testAdminDirectEditIntegration() throws Exception {
        String employeeToken = getJwtToken("employee1", "password123", "WEB");
        String adminToken = getJwtToken("admin1", "password123", "WEB");

        // 1. Employee applies for Sick leave (<= 2 days)
        CreateLeaveRequestDto applyDto = new CreateLeaveRequestDto();
        applyDto.setLeaveType(LeaveType.SICK);
        applyDto.setStartDate(LocalDate.now().plusDays(4));
        applyDto.setEndDate(LocalDate.now().plusDays(5));
        applyDto.setReason("Headache");

        MvcResult applyResult = mockMvc.perform(post("/api/leaves")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyDto)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> leaveResp = objectMapper.readValue(applyResult.getResponse().getContentAsString(), Map.class);
        String leaveId = (String) leaveResp.get("id");

        // 2. Admin performs direct DB edit
        AdminDirectEditDto directEditDto = new AdminDirectEditDto();
        directEditDto.setStatus(LeaveStatus.APPROVED);
        directEditDto.setAuditComment("Direct database approval by HR VP");

        mockMvc.perform(put("/api/admin/leaves/" + leaveId + "/direct-edit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(directEditDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 3. Verify audit log contains ADMIN_DIRECT_EDIT
        mockMvc.perform(get("/api/admin/leaves/" + leaveId + "/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ADMIN_DIRECT_EDIT"))
                .andExpect(jsonPath("$[0].adminDirectEdit").value(true));
    }

    @Test
    @DisplayName("Multi-turn Agent Leave Application: user asks to apply -> chooses Sick Leave -> chooses Tomorrow -> leave created")
    void testMultiTurnAgentLeaveApplication() throws Exception {
        String token = getJwtToken("contractor1", "password123", "AGENT");

        // Turn 1: user initiates application without type or date
        AgentChatRequestDto turn1 = new AgentChatRequestDto("I want to apply for leave", "agent-turn-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")))
                .andExpect(jsonPath("$.quickReplies").isArray())
                .andExpect(jsonPath("$.quickReplies[0]").value("Sick Leave"));

        // Turn 2: user selects Sick Leave
        AgentChatRequestDto turn2 = new AgentChatRequestDto("Sick Leave", "agent-turn-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("When would you like your leave to begin?")))
                .andExpect(jsonPath("$.quickReplies").isArray())
                .andExpect(jsonPath("$.quickReplies[0]").value("Tomorrow"));

        // Turn 3: user specifies Tomorrow
        AgentChatRequestDto turn3 = new AgentChatRequestDto("Tomorrow", "agent-turn-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.actionName").value("APPLY_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")));
    }

    @Test
    @DisplayName("Single-turn Agent Sick Leave > 2 days: auto-attaches digital document placeholder")
    void testSingleTurnAgentSickLeaveWithDocAutoAttached() throws Exception {
        String token = getJwtToken("employee1", "password123", "WEB");

        AgentChatRequestDto chat = new AgentChatRequestDto("Apply sick leave for 3 days starting tomorrow", "conv-sick-3");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Digital placeholder attached")));
    }

    @Test
    @DisplayName("Paid Leave notice violation: agent offers constructive date suggestion and confirms on 'Yes'")
    void testAgentPaidLeaveNoticeAutoSuggestionAndConfirm() throws Exception {
        String token = getJwtToken("employee1", "password123", "WEB");

        // Turn 1: user requests Paid Leave for tomorrow (violates > 2 days notice)
        AgentChatRequestDto turn1 = new AgentChatRequestDto("Apply paid leave tomorrow", "paid-notice-test");
        MvcResult res1 = mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Policy Check Notice")))
                .andExpect(jsonPath("$.quickReplies").isArray())
                .andReturn();

        // Turn 2: user confirms the earliest permitted date with "Yes"
        AgentChatRequestDto turn2 = new AgentChatRequestDto("Yes", "paid-notice-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")));
    }
}
