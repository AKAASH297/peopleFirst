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

    @Autowired
    private com.peoplefirst.agent.service.AgentService agentService;

    @Autowired
    private com.peoplefirst.leave.repository.LeaveRequestRepository leaveRequestRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        agentService.clearDrafts();
        leaveRequestRepository.deleteAll();
    }

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
    @DisplayName("Contractor Policy: Agent alerts immediately when contractor selects ineligible leave type (Casual) before asking for dates or reason")
    void testContractorImmediateIneligibilityAlert() throws Exception {
        String token = getJwtToken("contractor1", "password123", "AGENT");

        // Turn 1: Contractor types 'apply for leave'
        AgentChatRequestDto chat1 = new AgentChatRequestDto("apply for leave", "contractor-ineligible-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chat1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));

        // Turn 2: Contractor selects 'casual' -> must immediately alert without asking for dates or reason!
        AgentChatRequestDto chat2 = new AgentChatRequestDto("casual", "contractor-ineligible-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chat2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Contractors are not eligible for Casual Leave")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("When would you like your leave to begin"))))
                .andExpect(jsonPath("$.quickReplies").isArray());
    }

    @Test
    @DisplayName("Admin direct-DB-edit endpoint updates record and generates distinct audit")
    void testAdminDirectEditIntegration() throws Exception {
        String employeeToken = getJwtToken("employee1", "password123", "WEB");
        String adminToken = getJwtToken("admin1", "password123", "WEB");

        // 1. Employee applies for Sick leave (<= 2 days)
        // Window +12..+13 stays clear of every other employee1 scenario in this class
        // (casual +5..+6, paid tomorrow): the shared H2 DB makes same-user overlaps fail.
        CreateLeaveRequestDto applyDto = new CreateLeaveRequestDto();
        applyDto.setLeaveType(LeaveType.SICK);
        applyDto.setStartDate(LocalDate.now().plusDays(12));
        applyDto.setEndDate(LocalDate.now().plusDays(13));
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

        // Turn 3: user specifies Next Monday -> Agent prompts for reason interactively!
        AgentChatRequestDto turn3 = new AgentChatRequestDto("Next Monday", "agent-turn-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")))
                .andExpect(jsonPath("$.quickReplies").isArray())
                .andExpect(jsonPath("$.quickReplies[0]").value("Viral fever & rest"));

        // Turn 4: user supplies reason -> intelligently refined and applied!
        AgentChatRequestDto turn4 = new AgentChatRequestDto("Viral fever & rest", "agent-turn-test");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.actionName").value("APPLY_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("viral fever")));
    }

    @Test
    @DisplayName("Single-turn Agent Sick Leave > 2 days: auto-attaches digital document placeholder")
    void testSingleTurnAgentSickLeaveWithDocAutoAttached() throws Exception {
        // employee2 has no other leaves in this class; employee1's windows (tomorrow,
        // +5..+6, +12..+13) would overlap this +1..+3 range in the shared H2 DB.
        String token = getJwtToken("employee2", "password123", "WEB");

        AgentChatRequestDto chat = new AgentChatRequestDto("Apply sick leave for 3 days starting next Monday due to viral fever", "conv-sick-3");
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

    @Test
    @DisplayName("Manager views pending approvals and approves via Agent")
    void testManagerApprovalsViaAgent() throws Exception {
        // 1. Employee creates a leave
        String empToken = getJwtToken("employee1", "password123", "WEB");
        AgentChatRequestDto leaveReq = new AgentChatRequestDto("Apply casual leave for 1 day on next Monday for personal work", "emp-apply");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // 2. Manager checks pending approvals
        String mgrToken = getJwtToken("manager1", "password123", "AGENT");
        AgentChatRequestDto viewPending = new AgentChatRequestDto("Show pending approvals", "mgr-view");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewPending)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("VIEW_PENDING_APPROVALS"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Pending Leave Requests Awaiting Your Review")));

        // 3. Manager approves leave via Agent
        AgentChatRequestDto approveReq = new AgentChatRequestDto("Approve leave request because looks good", "mgr-approve");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.intent").value("APPROVE_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("APPROVED")));
    }

    @Test
    @DisplayName("Manager checks direct reportees balances via Agent")
    void testManagerCheckTeamBalancesViaAgent() throws Exception {
        String mgrToken = getJwtToken("manager1", "password123", "AGENT");
        AgentChatRequestDto req = new AgentChatRequestDto("Show my team leave balances", "mgr-team-bal");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_TEAM_BALANCES"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Direct Reportees Leave Balances")));
    }

    @Test
    @DisplayName("Employee raises support ticket via Agent")
    void testRaiseTicketViaAgent() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");
        AgentChatRequestDto req = new AgentChatRequestDto("Raise ticket: missed the cutoff deadline due to network error", "emp-tkt");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("RAISE_TICKET"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Support Ticket Created Successfully!")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("TKT-")));
    }

    @Test
    @DisplayName("Admin direct database edit via Agent")
    void testAdminDirectEditViaAgent() throws Exception {
        // Ensure there is at least one leave in DB
        String empToken = getJwtToken("employee2", "password123", "AGENT");
        AgentChatRequestDto apply = new AgentChatRequestDto("Apply casual leave for 1 day next Friday for personal errand", "emp2-apply");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apply)))
                .andExpect(status().isOk());

        // Admin invokes direct DB edit
        String adminToken = getJwtToken("admin1", "password123", "AGENT");
        AgentChatRequestDto directEdit = new AgentChatRequestDto("Directly update leave DB to APPROVED", "admin-direct");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(directEdit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("ADMIN_DIRECT_EDIT"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Admin Direct-DB-Edit Completed!")));
    }

    @Test
    @DisplayName("Agent replies: 'You can't apply leave for backdate.' when asked to apply for backdate")
    void testBackdateLeaveAgentReply() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. User asks: "apply for back date"
        AgentChatRequestDto req1 = new AgentChatRequestDto("apply for back date", "backdate-test-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("You can't apply leave for backdate."));

        // 2. User asks with yesterday
        AgentChatRequestDto req2 = new AgentChatRequestDto("apply sick leave for yesterday", "backdate-test-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("You can't apply leave for backdate."));
    }

    @Test
    @DisplayName("Overlap Prevention: Agent rejects duplicate or overlapping leave applications for the same dates")
    void testOverlapLeavePreventionViaAgent() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. First leave application succeeds
        AgentChatRequestDto leave1 = new AgentChatRequestDto("Apply sick leave for 2 days starting next Monday due to acute migraine", "overlap-test-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // 2. Second leave application for overlapping dates is rejected
        AgentChatRequestDto leave2 = new AgentChatRequestDto("Apply sick leave on next Monday because of fever", "overlap-test-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Overlapping leave requests on the same date are not permitted")));
    }

    @Test
    @DisplayName("Cancel Leave: Agent handles 'cancle leave' and 'cancel my sick leave' with balance restoration")
    void testCancelLeaveViaAgentWithMisspelling() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. Apply a leave first
        AgentChatRequestDto applyReq = new AgentChatRequestDto("Apply sick leave for next Monday due to cold", "cancel-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // 2. Cancel leave using misspelling "cancle leave"
        AgentChatRequestDto cancelReq = new AgentChatRequestDto("cancle leave", "cancel-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CANCEL_LEAVE"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Cancelled Successfully!")));
    }

    @Test
    @DisplayName("Edit Leave: Agent allows updating upcoming leave dates interactively")
    void testEditLeaveViaAgentInteractive() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. Apply a leave
        AgentChatRequestDto applyReq = new AgentChatRequestDto("Apply sick leave for next Monday due to stomach discomfort", "edit-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // 2. Initiate update: "update my leave"
        AgentChatRequestDto editReq1 = new AgentChatRequestDto("update my leave", "edit-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EDIT_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Editing Leave Request")))
                .andExpect(jsonPath("$.quickReplies").isArray());

        // 3. Provide new dates: "Next Week"
        AgentChatRequestDto editReq2 = new AgentChatRequestDto("Next Week", "edit-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Updated Successfully!")));
    }

    @Test
    @DisplayName("Intelligent Reason: Agent asks for reason and refines raw text into professional justification")
    void testIntelligentReasonRefinement() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. Request leave without reason
        AgentChatRequestDto req1 = new AgentChatRequestDto("Apply sick leave for next Monday", "reason-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")))
                .andExpect(jsonPath("$.quickReplies").isArray());

        // 2. Provide raw reason: "fever"
        AgentChatRequestDto req2 = new AgentChatRequestDto("fever", "reason-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("viral fever")));
    }

    @Test
    @DisplayName("Stress Intervention: Expressing stress in leave application intercepts flow and suggests amenities before submitting")
    void testPreApplicationStressInterventionImmediate() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. Employee applies with stress keyword: "feeling very stressed and burnt out"
        AgentChatRequestDto stressApply = new AgentChatRequestDto(
                "Apply casual leave for next Monday, I am feeling very stressed and burnt out from project deadlines",
                "stress-conv-1"
        );
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stressApply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Your Wellbeing Comes First")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Zero-Gravity Massage Recliners")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Recreational Lounge")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Confidential Psychologist Consultation")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Would you still like to proceed with submitting your leave request?")))
                .andExpect(jsonPath("$.quickReplies").value(org.hamcrest.Matchers.hasItem("✅ Yes, proceed with leave")));

        // 2. User confirms proceeding with the leave
        AgentChatRequestDto confirmProceed = new AgentChatRequestDto("✅ Yes, proceed with leave", "stress-conv-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmProceed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("work-life balance")));
    }

    @Test
    @DisplayName("Stress Intervention: Expressing stress in reason prompt intercepts flow and suggests amenities")
    void testPreApplicationStressInterventionInReason() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. User applies leave without reason
        AgentChatRequestDto req1 = new AgentChatRequestDto("Apply casual leave for next Monday", "stress-conv-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // 2. User provides stress reason
        AgentChatRequestDto req2 = new AgentChatRequestDto("Too much work pressure and exhausted", "stress-conv-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Your Wellbeing Comes First")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Zero-Gravity Massage Recliners")))
                .andExpect(jsonPath("$.quickReplies").value(org.hamcrest.Matchers.hasItem("✅ Yes, proceed with leave")));

        // 3. User proceeds
        AgentChatRequestDto req3 = new AgentChatRequestDto("proceed", "stress-conv-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully!")));
    }

    @Test
    @DisplayName("Weekly Wellbeing: Agent responds to weekly wellbeing inquiry with full report")
    void testWeeklyWellbeingStatusInquiry() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        AgentChatRequestDto req = new AgentChatRequestDto("Check my weekly wellbeing status", "weekly-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("WELLBEING_INQUIRY"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Weekly Wellbeing & Benefits Status")))
                .andExpect(jsonPath("$.actionData.recommendedAmenities").isArray());
    }

    @Test
    @DisplayName("Weekly Wellbeing Endpoint: GET /api/wellbeing/weekly-status returns structured metrics")
    void testWeeklyWellbeingEndpoint() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "WEB");

        mockMvc.perform(get("/api/wellbeing/weekly-status")
                        .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").isNotEmpty())
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.recommendedAmenities").isArray())
                .andExpect(jsonPath("$.recommendedAmenities.length()").value(9));
    }

    @Test
    @DisplayName("Security: Unauthenticated request to protected agent chat returns 401 Unauthorized")
    void testUnauthenticatedChatReturns401Unauthorized() throws Exception {
        AgentChatRequestDto req = new AgentChatRequestDto("Hello Kura", "no-auth-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Overlap Prevention: Agent warns of overlapping leave immediately when date is specified before asking for reason")
    void testOverlapLeavePreventionInMultiTurnReasonFlow() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // Step 1: Employee applies for Casual Leave on day +10
        LocalDate targetDate = LocalDate.now().plusDays(10);
        AgentChatRequestDto step1 = new AgentChatRequestDto(
                "Apply casual leave on " + targetDate + " for personal work", "overlap-flow-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // Step 2: Employee tries to apply Casual Leave on the same date without reason initially
        // Overlap warning must come IMMEDIATELY when the date is specified, before asking for reason!
        AgentChatRequestDto step2 = new AgentChatRequestDto(
                "Apply casual leave on " + targetDate, "overlap-flow-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Overlapping leave requests on the same date are not permitted")))
                .andExpect(jsonPath("$.quickReplies").isArray());
    }

    @Test
    @DisplayName("Fresh Apply Resets Draft: When user types 'apply for a leave' during an awaiting-reason draft, the draft is cleared and user is asked for leave type")
    void testFreshApplyResetsDraftWhenAwaitingReason() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        LocalDate targetDate = LocalDate.now().plusDays(10);
        // User starts applying
        AgentChatRequestDto step1 = new AgentChatRequestDto("Apply casual leave on " + targetDate, "fresh-apply-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // User changes mind and says "apply for a leave" -> must prompt for leave type, not execute targetDate draft
        AgentChatRequestDto step2 = new AgentChatRequestDto("apply for a leave", "fresh-apply-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));
    }

    @Test
    @DisplayName("Explicit Command During Awaiting Reason: When user checks balance during an awaiting-reason draft, draft is discarded and balance is returned")
    void testExplicitCommandDuringDraftResetsDraft() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        LocalDate targetDate = LocalDate.now().plusDays(10);
        // User starts applying
        AgentChatRequestDto step1 = new AgentChatRequestDto("Apply casual leave on " + targetDate, "switch-command-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // User changes mind and says "check my balance" -> must return balance, NOT submit leave with reason "check my balance"
        AgentChatRequestDto step2 = new AgentChatRequestDto("check my balance", "switch-command-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsStringIgnoringCase("leave balances")));
    }

    @Test
    @DisplayName("Balance Inquiry: 'how many leaves do i have' is recognized as CHECK_BALANCE")
    void testHowManyLeavesDoIHaveRecognizedAsBalance() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        AgentChatRequestDto req = new AgentChatRequestDto("how many leaves do i have", "balance-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsStringIgnoringCase("leave balances")));
    }

    @Test
    @DisplayName("Edit Leave: 'change my ... leave from [date1] to [date2]' updates existing leave without colliding with itself")
    void testChangeLeaveFromOldDateToNewDateUpdatesExistingLeaveWithoutSelfOverlap() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        LocalDate date1 = LocalDate.now().plusDays(10);
        LocalDate date2 = LocalDate.now().plusDays(11);

        // Step 1: Apply for Casual Leave on date1
        AgentChatRequestDto step1 = new AgentChatRequestDto("Apply casual leave on " + date1 + " for personal work", "edit-shift-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(true));

        // Step 2: Change the leave from date1 to date2
        AgentChatRequestDto step2 = new AgentChatRequestDto("change my casual leave from " + date1 + " to " + date2, "edit-shift-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EDIT_LEAVE"))
                .andExpect(jsonPath("$.actionExecuted").value(true))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Updated Successfully!")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString(date2.toString())));
    }

    @Test
    @DisplayName("Draft Continuity: Entering 'today' rejects backdate but preserves draft so next turn 'tomorrow' succeeds")
    void testDateCorrectionAfterBackdatePreservesDraft() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // Step 1: User asks to apply sick leave
        AgentChatRequestDto step1 = new AgentChatRequestDto("Apply sick leave", "backdate-cont-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("When would you like your leave to begin?")));

        // Step 2: User says 'today' -> rejected with backdate notice
        AgentChatRequestDto step2 = new AgentChatRequestDto("today", "backdate-cont-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("You can't apply leave for backdate.")));

        // Step 3: User corrects to 'next Monday' -> draft continues and prompts for reason
        AgentChatRequestDto step3 = new AgentChatRequestDto("next Monday", "backdate-cont-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(step3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));
    }

    @Test
    @DisplayName("Weekend Restriction: Agent immediately blocks leave on Saturday or Sunday and prompts for working days")
    void testWeekendLeaveRejectionViaAgent() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // Try to apply on Sunday 2026-09-13
        AgentChatRequestDto weekendReq = new AgentChatRequestDto("Apply sick leave for 2026-09-13", "weekend-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weekendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leaves cannot be applied on weekends (Saturday or Sunday)")))
                .andExpect(jsonPath("$.quickReplies").isArray());
    }

    @Test
    @DisplayName("Fuzzy Typo Intent Recognition: Agent understands misspelled intents like 'aply for a leev', 'cancal my leav', 'chekc balanace'")
    void testFuzzyTypoIntentRecognition() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. "aply for a leev" -> recognized as APPLY_LEAVE
        AgentChatRequestDto req1 = new AgentChatRequestDto("aply for a leev", "fuzzy-conv-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("APPLY_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));

        // 2. "chekc balanace" -> recognized as CHECK_BALANCE
        AgentChatRequestDto req2 = new AgentChatRequestDto("chekc balanace", "fuzzy-conv-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("overview of your leave balances"),
                        org.hamcrest.Matchers.containsString("Leave Balances"))));

        // 3. "cancal my leav" -> recognized as CANCEL_LEAVE
        AgentChatRequestDto req3 = new AgentChatRequestDto("cancal my leav", "fuzzy-conv-3");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CANCEL_LEAVE"));

        // 4. "edut my leave" -> recognized as EDIT_LEAVE
        AgentChatRequestDto req4 = new AgentChatRequestDto("edut my leave", "fuzzy-conv-4");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EDIT_LEAVE"));
    }

    @Test
    @DisplayName("Hindi Language Support: Recognizes Hindi leave phrasing, relative days, and balance queries")
    void testHindiLanguageSupport() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // 1. "kitni chutti bachi hai" -> CHECK_BALANCE
        AgentChatRequestDto req1 = new AgentChatRequestDto("kitni chutti bachi hai", "hindi-conv-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CHECK_BALANCE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("overview of your leave balances"),
                        org.hamcrest.Matchers.containsString("Leave Balances"))));

        // 2. "chutti chahiye kal se" -> APPLY_LEAVE
        AgentChatRequestDto req2 = new AgentChatRequestDto("chutti chahiye kal se", "hindi-conv-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("APPLY_LEAVE"))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));

        // 3. "mujhe chutti chahiye parso" -> APPLY_LEAVE with day after tomorrow
        AgentChatRequestDto req3 = new AgentChatRequestDto("mujhe chutti chahiye parso", "hindi-conv-3");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("APPLY_LEAVE"));
    }

    @Test
    @DisplayName("Flexible Date Format Parsing: Parses written months, dot separators, and date ranges")
    void testFlexibleDateFormatParsing() throws Exception {
        String empToken = getJwtToken("employee2", "password123", "AGENT");

        // 1. Written month: "21st September"
        AgentChatRequestDto req1 = new AgentChatRequestDto("Apply sick leave on 21st September for fever", "date-flex-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("2026-09-21")));

        // 2. Dot-separated date: "22.09.2026"
        AgentChatRequestDto req2 = new AgentChatRequestDto("Apply sick leave on 22.09.2026 for flu", "date-flex-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("2026-09-22")));

        // 3. Month range: "23 to 25 September"
        AgentChatRequestDto req3 = new AgentChatRequestDto("Apply sick leave from 23 to 25 September for viral fever", "date-flex-3");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("-09-23 to ")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("-09-25")));
    }

    @Test
    @DisplayName("Fuzzy Leave Type: Understands misspelled leave types like 'casul' and 'sik'")
    void testFuzzyLeaveTypeExtraction() throws Exception {
        String empToken = getJwtToken("employee1", "password123", "AGENT");

        // "casul leave" -> recognized as Casual Leave
        AgentChatRequestDto req1 = new AgentChatRequestDto("Apply casul leave on 2026-11-20", "fuzzy-lt-1");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Casual Leave")));

        // "sik leave" -> recognized as Sick Leave
        AgentChatRequestDto req2 = new AgentChatRequestDto("Apply sik leave on 2026-11-22", "fuzzy-lt-2");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Sick Leave")));
    }

    @Test
    @DisplayName("Context-Aware Multi-Turn: Follow-up messages understand context without repeating full commands")
    void testContextAwareMultiTurnConversation() throws Exception {
        String empToken = getJwtToken("employee2", "password123", "AGENT");

        // Turn 1: User says "apply leave" -> Agent asks "Which type of leave?"
        AgentChatRequestDto turn1 = new AgentChatRequestDto("apply leave", "context-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));

        // Turn 2: User replies with just "casual" -> Agent understands it as leave type, prompts for dates
        AgentChatRequestDto turn2 = new AgentChatRequestDto("casual", "context-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Casual Leave")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("When would you like your leave to begin?")));

        // Turn 3: User replies with "next monday" -> Agent understands date, prompts for reason
        AgentChatRequestDto turn3 = new AgentChatRequestDto("next monday", "context-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // Turn 4: User provides reason "sister wedding" -> Agent refines and prompts for confirmation / submits
        AgentChatRequestDto turn4 = new AgentChatRequestDto("sister wedding", "context-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("Leave Request Ready for Review"),
                        org.hamcrest.Matchers.containsString("Leave Application Submitted"),
                        org.hamcrest.Matchers.containsString("Leave Request Submitted"),
                        org.hamcrest.Matchers.containsString("Shall I submit this request"))));
    }

    @Test
    @DisplayName("Half Day Leave: 'apply half day leave for next Monday' prompts for leave type, session, and reason")
    void testApplyHalfDayLeaveForTomorrowTypoDoesNotUseDateAsReason() throws Exception {
        String empToken = getJwtToken("employee2", "password123", "AGENT");

        // Turn 1: User says "apply half day leave for next Monday"
        AgentChatRequestDto turn1 = new AgentChatRequestDto("apply half day leave for next Monday", "halfday-typo-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Which type of leave would you like to apply for?")));

        // Turn 2: User says "casual" -> Agent sets leaveType=CASUAL, notices half-day session is missing, and prompts for Morning/Afternoon!
        AgentChatRequestDto turn2 = new AgentChatRequestDto("casual", "halfday-typo-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Morning")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Afternoon")));

        // Turn 3: User says "morning" -> Agent sets FIRST_HALF session, notices reason is missing, and prompts for reason!
        AgentChatRequestDto turn3 = new AgentChatRequestDto("morning", "halfday-typo-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionExecuted").value(false))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // Turn 4: User provides their actual reason -> Agent submits with polished reason and Morning session!
        AgentChatRequestDto turn4 = new AgentChatRequestDto("personal work at bank", "halfday-typo-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("Leave Request Ready for Review"),
                        org.hamcrest.Matchers.containsString("Leave Request Submitted"),
                        org.hamcrest.Matchers.containsString("Shall I submit this request"))))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("First Half (Morning)")));
    }

    @Test
    @DisplayName("Half Day Leave: 'apply half day casual leave for 2026-09-21' prompts for morning/afternoon and applies correctly")
    void testApplyHalfDayCasualLeavePromptsForSession() throws Exception {
        String empToken = getJwtToken("employee2", "password123", "AGENT");

        // Turn 1: User says "apply half day casual leave for 2026-09-21"
        AgentChatRequestDto turn1 = new AgentChatRequestDto("apply half day casual leave for 2026-09-21", "halfday-session-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Would you like to take the **Morning** or **Afternoon** session off?")))
                .andExpect(jsonPath("$.quickReplies").value(org.hamcrest.Matchers.hasItems("🌅 First Half (Morning)", "🌇 Second Half (Afternoon)")));

        // Turn 2: User says "afternoon" -> Agent sets SECOND_HALF session, prompts for reason
        AgentChatRequestDto turn2 = new AgentChatRequestDto("afternoon", "halfday-session-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // Turn 3: User provides reason -> Leave submitted with Second Half (Afternoon)
        AgentChatRequestDto turn3 = new AgentChatRequestDto("visiting doctor clinic", "halfday-session-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Second Half (Afternoon)")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("0.5 day")));
    }

    @Test
    @DisplayName("Flexible Day Range: 'apply paid leave from 8th to 9th' then 'okay then apply from 9th to 11th' correctly parses dates")
    void testApplyPaidLeaveWithFlexibleDayNumberRanges() throws Exception {
        String empToken = getJwtToken("employee2", "password123", "AGENT");

        // Turn 1: User says "i want to apply paid leave from 8th to 9th"
        AgentChatRequestDto turn1 = new AgentChatRequestDto("i want to apply paid leave from 8th to 9th", "paid-range-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Paid Leave")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("2026-09-08 to 2026-09-09")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Could you share the **reason** for your leave?")));

        // Turn 2: User says "okay then apply from 9th to 11th" -> Agent updates dates and submits!
        AgentChatRequestDto turn2 = new AgentChatRequestDto("okay then apply from 9th to 11th", "paid-range-conv");
        mockMvc.perform(post("/api/agent/chat")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(turn2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("Leave Request Submitted Successfully")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("2026-09-09 to 2026-09-11")))
                .andExpect(jsonPath("$.reply").value(org.hamcrest.Matchers.containsString("3.0 days")));
    }
}
