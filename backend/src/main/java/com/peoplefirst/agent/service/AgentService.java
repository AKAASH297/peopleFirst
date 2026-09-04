package com.peoplefirst.agent.service;

import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.AgentIntent;
import com.peoplefirst.agent.intent.IntentParser;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.CreateLeaveRequestDto;
import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.policy.dto.PolicyResponseDto;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.service.PolicyService;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.service.WellbeingService;
import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.tools.AgentTool;
import com.peoplefirst.agent.tools.AgentToolCatalog;
import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.service.ApprovalService;
import com.peoplefirst.user.entity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final IntentParser intentParser;
    private final CurrentUserProvider currentUserProvider;
    private final LeaveService leaveService;
    private final LeaveBalanceService leaveBalanceService;
    private final PolicyService policyService;
    private final WellbeingService wellbeingService;
    private final LeaveMapper leaveMapper;
    private final GenAiClient genAiClient;
    private final ApprovalService approvalService;

    // Multi-turn conversational leave draft store keyed by User UUID
    private final Map<UUID, PendingLeaveDraft> userDrafts = new ConcurrentHashMap<>();

    // Agentic loop state: per-conversation message history and pending write confirmations
    private final Map<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAgentAction> pendingActions = new ConcurrentHashMap<>();

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private static final Set<String> CONFIRM_WORDS = Set.of("yes", "confirm", "proceed");
    private static final Set<String> DISCARD_WORDS = Set.of("no", "cancel", "discard", "abort", "stop");

    public AgentService(IntentParser intentParser,
                        CurrentUserProvider currentUserProvider,
                        LeaveService leaveService,
                        LeaveBalanceService leaveBalanceService,
                        PolicyService policyService,
                        WellbeingService wellbeingService,
                        LeaveMapper leaveMapper,
                        GenAiClient genAiClient,
                        ApprovalService approvalService) {
        this.intentParser = intentParser;
        this.currentUserProvider = currentUserProvider;
        this.leaveService = leaveService;
        this.leaveBalanceService = leaveBalanceService;
        this.policyService = policyService;
        this.wellbeingService = wellbeingService;
        this.leaveMapper = leaveMapper;
        this.genAiClient = genAiClient;
        this.approvalService = approvalService;
    }

    public AgentChatResponseDto processMessage(AgentChatRequestDto request) {
        // Overriding rule: Identity comes strictly from SecurityContext -> DB
        User user = currentUserProvider.getCurrentUser();
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
        if (message.length() > MAX_MESSAGE_LENGTH) {
            AgentChatResponseDto tooLong = new AgentChatResponseDto(
                    "Please keep your message under 2000 characters (yours was " + message.length()
                            + "). Try splitting it into smaller messages.",
                    AgentIntent.UNKNOWN.name());
            tooLong.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies"));
            return tooLong;
        }
        if (genAiClient.isConfigured()) {
            return processAgentic(message, request.getConversationId(), user);
        }
        return processRuleBased(request, message, user);
    }

    private AgentChatResponseDto processRuleBased(AgentChatRequestDto request, String message, User user) {
        String lower = message.toLowerCase().trim();

        // 1. Manage active drafts
        PendingLeaveDraft draft = userDrafts.get(user.getId());
        if (draft != null && draft.isExpired()) {
            userDrafts.remove(user.getId());
            draft = null;
        }

        // Check if user explicitly wants to cancel an active draft
        if (draft != null && (lower.equals("cancel") || lower.equals("stop") || lower.equals("abort") ||
                lower.equals("never mind") || lower.equals("nevermind") || lower.equals("no") ||
                lower.equals("cancel draft"))) {
            userDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "Your pending leave application draft has been cancelled. Let me know if you need help with anything else!",
                    AgentIntent.APPLY_LEAVE.name()
            );
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        }

        AgentIntent intent = intentParser.parseIntent(message);

        // 2. If the user has an active draft and did not trigger a separate inquiry intent,
        // treat message as follow-up to the active draft
        boolean isExplicitOtherIntent = (intent == AgentIntent.CHECK_BALANCE ||
                intent == AgentIntent.VIEW_LEAVES ||
                intent == AgentIntent.WELLBEING_INQUIRY ||
                intent == AgentIntent.CANCEL_LEAVE ||
                intent == AgentIntent.CHECK_POLICY ||
                intent == AgentIntent.STRESS_EXPRESSION);

        if (draft != null && !isExplicitOtherIntent) {
            return continueLeaveDraft(message, draft, user);
        }

        switch (intent) {
            case GREETING:
                return handleGreeting(user);
            case CHECK_BALANCE:
                return handleCheckBalance(message, user);
            case APPLY_LEAVE:
                return handleApplyLeave(message, user);
            case CANCEL_LEAVE:
                return handleCancelLeave(message, user);
            case APPROVE_LEAVES:
                return handleApprovalInbox(message, user);
            case VIEW_LEAVES:
                return handleViewLeaves(user);
            case CHECK_POLICY:
                return handleCheckPolicy(user);
            case STRESS_EXPRESSION:
                return handleStressExpression(message, user);
            case WELLBEING_INQUIRY:
                return handleWellbeingInquiry(message, user);
            case TICKET_INQUIRY:
                return handleTicketInquiry(user);
            case UNKNOWN:
            default:
                if (draft != null) {
                    return continueLeaveDraft(message, draft, user);
                }
                return handleUnknown(message, user);
        }
    }

    private AgentChatResponseDto processAgentic(String message, String conversationId, User user) {
        String lower = message.toLowerCase().trim();

        // Confirm-gate: resolve a pending write before calling the model
        PendingAgentAction pending = pendingActions.get(user.getId());
        if (pending != null && pending.isExpired()) {
            pendingActions.remove(user.getId());
            pending = null;
        }
        if (pending != null) {
            if (isConfirmReply(lower)) {
                pendingActions.remove(user.getId());
                if (AgentTool.APPLY_LEAVE.getName().equals(pending.getToolName())) {
                    return executeLeaveApplication(buildDraftFromArguments(pending.getArgumentsJson(), message), user);
                }
                if (AgentTool.APPROVE_LEAVE.getName().equals(pending.getToolName())
                        || AgentTool.REJECT_LEAVE.getName().equals(pending.getToolName())) {
                    return executeApprovalAction(pending, user);
                }
                return handleCancelLeave(message, user);
            }
            if (isDiscardReply(lower)) {
                pendingActions.remove(user.getId());
                AgentChatResponseDto cancelled = new AgentChatResponseDto(
                        "Understood \u2014 I've discarded the pending action. Let me know if you need anything else!",
                        AgentIntent.UNKNOWN.name());
                cancelled.setQuickReplies(getPostActionQuickReplies(user));
                return cancelled;
            }
        }

        String convKey = (conversationId != null && !conversationId.isBlank()) ? conversationId : "default";
        String historyKey = user.getId().toString() + ":" + convKey;
        List<Map<String, String>> history =
                conversations.computeIfAbsent(historyKey, k -> new ArrayList<>());
        // Bound total keys: conversation IDs are client-controlled and could grow the map without limit.
        // ConcurrentHashMap has no insertion order, so evict an arbitrary entry when over budget.
        while (conversations.size() > 500) {
            Iterator<String> eldest = conversations.keySet().iterator();
            if (!eldest.hasNext()) {
                break;
            }
            conversations.remove(eldest.next());
        }
        appendToHistory(history, Map.of("role", "user", "content", message));

        ObjectMapper mapper = new ObjectMapper();
        AgentChatResponseDto lastToolResponse = null;

        for (int turn = 0; turn < 5; turn++) {
            List<Map<String, String>> snapshot = new ArrayList<>(history);
            Optional<String> raw;
            try {
                raw = genAiClient.chatWithTools(
                        buildSystemContext(user) + "\nToday is " + LocalDate.now() + ".",
                        snapshot, new AgentToolCatalog().getSchemas());
            } catch (Exception e) {
                raw = Optional.empty();
            }
            if (raw.isEmpty()) {
                return processRuleBased(new AgentChatRequestDto(message, conversationId), message, user);
            }

            JsonNode assistant;
            try {
                assistant = mapper.readTree(raw.get());
            } catch (Exception e) {
                return processRuleBased(new AgentChatRequestDto(message, conversationId), message, user);
            }

            String content = assistant.path("content").isNull()
                    ? null : assistant.path("content").asText(null);
            JsonNode toolCalls = assistant.path("tool_calls");
            boolean hasTools = toolCalls.isArray() && toolCalls.size() > 0;

            appendToHistory(history, Map.of("role", "assistant",
                    "content", content != null ? content : ""));

            if (!hasTools) {
                if (lastToolResponse != null) {
                    String reply = (content != null && !content.isBlank())
                            ? content : lastToolResponse.getReply();
                    AgentChatResponseDto merged = new AgentChatResponseDto(reply, lastToolResponse.getIntent());
                    merged.setActionExecuted(true);
                    merged.setActionName(lastToolResponse.getActionName());
                    merged.setActionData(lastToolResponse.getActionData());
                    merged.setWellbeingSuggestions(lastToolResponse.getWellbeingSuggestions());
                    merged.setQuickReplies(lastToolResponse.getQuickReplies() != null
                            ? lastToolResponse.getQuickReplies()
                            : List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
                    return merged;
                }
                AgentChatResponseDto response = new AgentChatResponseDto(
                        content != null ? content : "", AgentIntent.UNKNOWN.name());
                response.setQuickReplies(
                        List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
                return response;
            }

            for (JsonNode call : toolCalls) {
                String callId = call.path("id").asText(null);
                String toolCallId = callId != null ? callId : "";
                JsonNode function = call.path("function");
                String toolName = function.path("name").asText(null);
                String argumentsJson = function.path("arguments").isNull()
                        ? "{}" : function.path("arguments").asText("{}");

                AgentTool tool;
                try {
                    tool = AgentTool.fromName(toolName);
                } catch (IllegalArgumentException | NullPointerException e) {
                    appendToHistory(history, Map.of("role", "tool",
                            "tool_call_id", toolCallId, "content", "Unknown tool"));
                    continue;
                }

                switch (tool) {
                    case CHECK_BALANCE -> {
                        AgentChatResponseDto toolResponse = handleCheckBalance(message, user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toolResponse.getActionData())));
                    }
                    case VIEW_LEAVES -> {
                        AgentChatResponseDto toolResponse = handleViewLeaves(user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toolResponse.getActionData())));
                    }
                    case CHECK_POLICY -> {
                        AgentChatResponseDto toolResponse = handleCheckPolicy(user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toolResponse.getActionData())));
                    }
                    case WELLBEING -> {
                        AgentChatResponseDto toolResponse = handleWellbeingInquiry(message, user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toolResponse.getActionData())));
                    }
                    case TICKET_INQUIRY -> {
                        AgentChatResponseDto toolResponse = handleTicketInquiry(user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toolResponse.getActionData())));
                    }
                    case APPLY_LEAVE, CANCEL_LEAVE, APPROVE_LEAVE, REJECT_LEAVE -> {
                        pendingActions.put(user.getId(), new PendingAgentAction(tool.getName(), argumentsJson));
                        String intent = tool == AgentTool.APPLY_LEAVE
                                ? AgentIntent.APPLY_LEAVE.name()
                                : tool == AgentTool.CANCEL_LEAVE
                                ? AgentIntent.CANCEL_LEAVE.name() : AgentIntent.APPROVE_LEAVES.name();
                        AgentChatResponseDto confirm = new AgentChatResponseDto(
                                "I've prepared " + summarizeArguments(tool, argumentsJson)
                                        + ". Reply **yes** to confirm or **no** to discard.",
                                intent);
                        confirm.setActionExecuted(false);
                        confirm.setQuickReplies(List.of("Yes, confirm", "No, discard"));
                        return confirm;
                    }
                }
            }
        }

        if (lastToolResponse != null) {
            return lastToolResponse;
        }
        return processRuleBased(new AgentChatRequestDto(message, conversationId), message, user);
    }

    private boolean isConfirmReply(String lower) {
        return CONFIRM_WORDS.contains(lower) || CONFIRM_WORDS.contains(firstToken(lower));
    }

    private boolean isDiscardReply(String lower) {
        if (lower.equals("never mind") || lower.equals("nevermind")) {
            return true;
        }
        return DISCARD_WORDS.contains(lower) || DISCARD_WORDS.contains(firstToken(lower));
    }

    private String firstToken(String lower) {
        String[] tokens = lower.split("[\\s\\p{Punct}]+");
        return tokens.length > 0 ? tokens[0] : "";
    }

    private void appendToHistory(List<Map<String, String>> history, Map<String, String> entry) {
        history.add(entry);
        while (history.size() > 20) {
            history.remove(0);
        }
    }

    private String toCompactJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return value != null ? value.toString() : "null";
        }
    }

    private String summarizeArguments(AgentTool tool, String argumentsJson) {
        if (tool == AgentTool.CANCEL_LEAVE) {
            return "cancellation of your upcoming leave";
        }
        if (tool == AgentTool.APPROVE_LEAVE) {
            return "approval of the requested team leave";
        }
        if (tool == AgentTool.REJECT_LEAVE) {
            return "rejection of the requested team leave";
        }
        try {
            JsonNode args = new ObjectMapper().readTree(argumentsJson != null ? argumentsJson : "{}");
            String type = args.path("leaveType").asText("");
            String start = args.path("startDate").asText("");
            String end = args.path("endDate").asText("");
            StringBuilder sb = new StringBuilder("your leave application");
            if (!type.isBlank() || !start.isBlank()) {
                sb.append(" (");
                if (!type.isBlank()) {
                    sb.append(type);
                }
                if (!start.isBlank()) {
                    if (!type.isBlank()) {
                        sb.append(" ");
                    }
                    sb.append(start);
                    if (!end.isBlank() && !end.equals(start)) {
                        sb.append(" to ").append(end);
                    }
                }
                sb.append(")");
            }
            return sb.toString();
        } catch (Exception e) {
            return "your leave application";
        }
    }

    private PendingLeaveDraft buildDraftFromArguments(String argumentsJson, String message) {
        PendingLeaveDraft draft = new PendingLeaveDraft();
        try {
            JsonNode args = new ObjectMapper().readTree(argumentsJson != null ? argumentsJson : "{}");
            String typeText = args.path("leaveType").asText(null);
            LeaveType type = (typeText != null && !typeText.isBlank())
                    ? intentParser.extractLeaveType(typeText) : null;
            if (type == null) {
                type = intentParser.extractLeaveType(message);
            }
            LocalDate start = parseIsoDate(args.path("startDate").asText(null));
            LocalDate end = parseIsoDate(args.path("endDate").asText(null));
            if (start == null) {
                LocalDate[] dates = intentParser.extractDates(message);
                start = dates[0];
                end = dates[1] != null ? dates[1] : dates[0];
            }
            if (end == null) {
                end = start;
            }
            draft.setLeaveType(type);
            draft.setStartDate(start);
            draft.setEndDate(end);
            draft.setHalfDay(args.path("halfDay").asBoolean(false) || intentParser.extractHalfDay(message));
            String sessionArg = args.path("halfDaySession").asText(null);
            if (!"FIRST_HALF".equals(sessionArg) && !"SECOND_HALF".equals(sessionArg)) {
                sessionArg = null;
            }
            if (sessionArg == null) {
                sessionArg = intentParser.extractHalfDaySession(message);
            }
            draft.setHalfDaySession(sessionArg);
            draft.setDocAttached(intentParser.extractDocumentAttached(message));
            String reason = args.path("reason").asText(null);
            draft.setReason((reason != null && !reason.isBlank())
                    ? reason : "Applied via Kura AI Agent: " + message);
        } catch (Exception e) {
            draft.setReason("Applied via Kura AI Agent: " + message);
        }
        return draft;
    }

    private LocalDate parseIsoDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSystemContext(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Kura, the intelligent AI Leave Management & Wellbeing Concierge for peopleFirst.\n");
        sb.append("The current user is: ").append(user.getFullName())
                .append(", Role: ").append(user.isContractor() ? "Contractor Partner" : user.getRole().name())
                .append(", Department: ").append(user.getDepartment())
                .append(", Base Location: ").append(user.getBaseLocation()).append(".\n\n");

        sb.append("CORE RULES & GROUNDING CONSTRAINTS:\n");
        if (user.isContractor()) {
            sb.append("- Contractors have AGENT-ONLY access (no web portal access).\n");
            sb.append("- Contractors are eligible ONLY for: Sick Leave (16 days), Paid Leave (24 days), LOP (30 days).\n");
            sb.append("- Contractors are NOT eligible for Casual Leave, WFH, Maternity, or Volunteering.\n");
            sb.append("- Contractors CANNOT combine leave types (0 combination rights).\n");
        } else {
            sb.append("- Permanent employees get: Casual (12), Sick (16), Paid (20), LOP (180), WFH (24), Maternity (182), Volunteering (2).\n");
            sb.append("- Casual Leave may ONLY be combined with WFH. Other combinations are strictly prohibited.\n");
        }

        sb.append("- Sick Leave exceeding 2 days requires verified medical certificate/prescription.\n");
        sb.append("- Paid Leave requires advance notice of MORE THAN 2 DAYS (start date must be at least 3 days from application).\n");
        sb.append("- Deadlines: Casual & WFH must be requested by end of the current week (Sunday 23:59:59). Sick, Paid, and LOP on or before the 25th of the month.\n");
        sb.append("- Late requests or retrospective corrections require raising a Support Ticket.\n");
        sb.append("- Campus Wellbeing perks: Zero-gravity massage chairs (Bldg 1, 4th Fl), Games lounge (Bldg 3, 3rd Fl), Psychologist counseling (Bldg 2, 2nd Fl), Guided Yoga (Bldg 1, Terrace).\n");
        sb.append("- Healthcare: Partner hospital OPD discounts available; OPD claims must be submitted within 90 days.\n");
        if (user.getRole() == Role.MANAGER || user.getRole() == Role.ADMIN) {
            sb.append("- As a ").append(user.getRole().name())
                    .append(" you can review team leave: ask me for \"pending approvals\" and approve or reject by number.\n");
        }
        sb.append("Formatting rule: NEVER use markdown tables (pipes) — this chat cannot render them. Present tabular data as bullet lists with bold labels (e.g. \"• **Sick Leave:** 16 days remaining\").\n");
        sb.append("Respond warmly, concisely, and empathetically. Keep markdown formatting clean.");
        return sb.toString();
    }

    private AgentChatResponseDto handleGreeting(User user) {
        if (genAiClient.isConfigured()) {
            String systemContext = buildSystemContext(user);
            Optional<String> genAiReply = genAiClient.generateContent(
                    systemContext,
                    "Greet me as Kura, acknowledge my role at peopleFirst, and briefly offer your concierge services."
            );
            if (genAiReply.isPresent()) {
                AgentChatResponseDto response = new AgentChatResponseDto(genAiReply.get(), AgentIntent.GREETING.name());
                response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Explore amenities"));
                return response;
            }
        }

        String roleText = user.isContractor() ? "Contractor Partner" : user.getRole().name();
        String reply = "Hello " + user.getFullName() + "! I am **Kura**, your dedicated AI leave management and wellbeing concierge at peopleFirst.\n\n" +
                "As a " + roleText + ", how can I assist you today?\n" +
                "• Check your current leave balances\n" +
                "• Apply for eligible leave\n" +
                "• Review company leave rules & cutoffs\n" +
                "• Explore workplace wellness perks (Gym, Sick Room, Massage Chairs, Healthcare discounts)";

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.GREETING.name());
        response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Explore amenities"));
        return response;
    }

    private List<LeaveBalance> fetchBalances(User user) {
        int year = LocalDate.now().getYear();
        leaveBalanceService.initializeUserBalancesIfAbsent(user, year);
        return leaveBalanceService.getUserBalances(user.getId(), year);
    }

    private AgentChatResponseDto handleCheckBalance(String message, User user) {
        int year = LocalDate.now().getYear();
        List<LeaveBalance> balances = fetchBalances(user);

        LeaveType requestedType = intentParser.extractLeaveType(message);
        StringBuilder sb = new StringBuilder();

        if (requestedType != null) {
            Optional<LeaveBalance> match = balances.stream()
                    .filter(b -> b.getLeaveType() == requestedType)
                    .findFirst();

            if (match.isPresent()) {
                LeaveBalance b = match.get();
                sb.append("Here is your **").append(requestedType.getDisplayName()).append("** balance for ").append(year).append(":\n\n")
                        .append("• **Remaining:** ").append(b.getRemainingDays()).append(" days\n")
                        .append("• **Used:** ").append(b.getUsedDays()).append(" days\n")
                        .append("• **Pending Approval:** ").append(b.getPendingDays()).append(" days\n")
                        .append("• **Annual Allocation:** ").append(b.getAllocatedDays()).append(" days");
            } else {
                sb.append("You are not allocated or eligible for ").append(requestedType.getDisplayName()).append(".");
            }
        } else {
            sb.append("Here is an overview of your leave balances for ").append(year).append(":\n\n");
            for (LeaveBalance b : balances) {
                sb.append("• **").append(b.getLeaveType().getDisplayName()).append("**: ")
                        .append(b.getRemainingDays()).append(" days remaining (")
                        .append(b.getUsedDays()).append(" used, ")
                        .append(b.getPendingDays()).append(" pending of ")
                        .append(b.getAllocatedDays()).append(")\n");
            }
        }

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.CHECK_BALANCE.name());
        response.setActionExecuted(true);
        response.setActionName("CHECK_BALANCE");
        response.setActionData(balances.stream().map(b -> leaveMapper.toBalanceDto(b, user)).collect(Collectors.toList()));
        response.setQuickReplies(List.of("Apply for leave", "View leave policies", "Check recent leaves"));
        return response;
    }

    private AgentChatResponseDto continueLeaveDraft(String message, PendingLeaveDraft draft, User user) {
        String lower = message.toLowerCase().trim();

        // 1. If user confirms a previously suggested date (e.g. "Yes, apply from 2026-09-07" or "yes")
        if (lower.startsWith("yes") || lower.contains("confirm") || lower.contains("proceed") || lower.contains("apply from")) {
            LocalDate[] suggestedDates = intentParser.extractDates(message);
            if (suggestedDates[0] != null) {
                draft.setStartDate(suggestedDates[0]);
                draft.setEndDate(suggestedDates[1] != null ? suggestedDates[1] : suggestedDates[0]);
            }
            if (draft.getLeaveType() != null && draft.getStartDate() != null) {
                userDrafts.remove(user.getId());
                return executeLeaveApplication(draft, user);
            }
        }

        LeaveType extractedType = intentParser.extractLeaveType(message);
        LeaveType extractedCombined = intentParser.extractCombinedType(message);
        LocalDate[] dates = intentParser.extractDates(message);
        boolean isHalfDay = intentParser.extractHalfDay(message);
        boolean docAttached = intentParser.extractDocumentAttached(message);
        String extractedSession = intentParser.extractHalfDaySession(message);

        if (extractedType != null) {
            draft.setLeaveType(extractedType);
        }
        if (extractedCombined != null) {
            draft.setCombinedWithType(extractedCombined);
        }
        if (dates[0] != null) {
            draft.setStartDate(dates[0]);
            draft.setEndDate(dates[1] != null ? dates[1] : dates[0]);
        }
        if (isHalfDay) {
            draft.setHalfDay(true);
        }
        if (extractedSession != null) {
            draft.setHalfDaySession(extractedSession);
        }
        if (docAttached) {
            draft.setDocAttached(true);
        }

        // If draft still missing leave type
        if (draft.getLeaveType() == null) {
            return promptForLeaveType(user);
        }

        // If draft still missing dates
        if (draft.getStartDate() == null) {
            return promptForDates(draft.getLeaveType(), user);
        }

        // Half-day without a session -> ask which half
        if (draft.isHalfDay() && draft.getHalfDaySession() == null) {
            userDrafts.put(user.getId(), draft);
            return promptForHalfDaySession(user);
        }

        // Both present -> execute application!
        userDrafts.remove(user.getId());
        return executeLeaveApplication(draft, user);
    }

    private AgentChatResponseDto handleApplyLeave(String message, User user) {
        LeaveType leaveType = intentParser.extractLeaveType(message);
        LeaveType combinedWithType = intentParser.extractCombinedType(message);
        LocalDate[] dates = intentParser.extractDates(message);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];
        boolean isHalfDay = intentParser.extractHalfDay(message);
        boolean docAttached = intentParser.extractDocumentAttached(message);

        PendingLeaveDraft draft = new PendingLeaveDraft();
        draft.setLeaveType(leaveType);
        draft.setHalfDaySession(intentParser.extractHalfDaySession(message));
        draft.setCombinedWithType(combinedWithType);
        draft.setStartDate(startDate);
        draft.setEndDate(endDate != null ? endDate : startDate);
        draft.setHalfDay(isHalfDay);
        draft.setDocAttached(docAttached);
        draft.setReason("Applied via Kura AI Agent: " + message);

        if (leaveType == null) {
            userDrafts.put(user.getId(), draft);
            return promptForLeaveType(user);
        }

        if (draft.getStartDate() == null) {
            userDrafts.put(user.getId(), draft);
            return promptForDates(leaveType, user);
        }

        // Half-day without a session -> ask which half
        if (draft.isHalfDay() && draft.getHalfDaySession() == null) {
            userDrafts.put(user.getId(), draft);
            return promptForHalfDaySession(user);
        }

        // Both present in single turn -> execute
        userDrafts.remove(user.getId());
        return executeLeaveApplication(draft, user);
    }

    private AgentChatResponseDto promptForLeaveType(User user) {
        String roleNote = user.isContractor()
                ? "As a contractor partner, you are eligible for **Sick Leave**, **Paid Leave**, or **Loss of Pay (LOP)**."
                : "You can apply for **Casual Leave**, **Sick Leave**, **Paid Leave**, **Work From Home (WFH)**, or **Loss of Pay (LOP)**.";

        String reply = "I would be glad to help you submit a leave request!\n\n" + roleNote + "\n\nWhich type of leave would you like to apply for?";
        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(getEligibleLeaveTypeChips(user));
        return response;
    }

    private List<String> getEligibleLeaveTypeChips(User user) {
        if (user.isContractor()) {
            return List.of("Sick Leave", "Paid Leave", "Loss of Pay (LOP)", "Cancel");
        } else {
            return List.of("Casual Leave", "Sick Leave", "Paid Leave", "Work From Home (WFH)", "Loss of Pay (LOP)", "Cancel");
        }
    }

    private AgentChatResponseDto promptForDates(LeaveType leaveType, User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Got it! You're applying for **").append(leaveType.getDisplayName()).append("**.\n\n");

        if (leaveType == LeaveType.PAID) {
            LocalDate earliestValid = LocalDate.now().plusDays(3);
            sb.append("⚠️ *Notice rule:* Paid Leave requires more than 2 days advance notice (earliest valid date is **")
                    .append(earliestValid).append("**).\n\n");
        } else if (leaveType == LeaveType.SICK) {
            sb.append("💡 *Tip:* Sick Leave exceeding 2 consecutive days requires a medical certificate.\n\n");
        } else if (leaveType == LeaveType.CASUAL || leaveType == LeaveType.WFH) {
            sb.append("💡 *Tip:* Casual / WFH must be submitted before the end of the current week.\n\n");
        }

        sb.append("When would you like your leave to begin? (e.g., 'Tomorrow', 'Next week', 'for 3 days starting tomorrow', or 'YYYY-MM-DD').");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(getDateRecommendationChips(leaveType));
        return response;
    }

    private AgentChatResponseDto promptForHalfDaySession(User user) {
        AgentChatResponseDto response = new AgentChatResponseDto(
                "Got it — **First half** (morning) or **Second half** (afternoon)?",
                AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(List.of("First half (morning)", "Second half (afternoon)", "Cancel"));
        return response;
    }

    private List<String> getDateRecommendationChips(LeaveType leaveType) {
        if (leaveType == LeaveType.PAID) {
            LocalDate earliestValid = LocalDate.now().plusDays(3);
            return List.of(
                    "In 3 Days (" + earliestValid + ")",
                    "Next Week",
                    "For 5 Days from " + earliestValid,
                    "Cancel"
            );
        } else {
            return List.of(
                    "Tomorrow",
                    "Next 2 Days",
                    "Next 3 Days",
                    "Next Week",
                    "Cancel"
            );
        }
    }

    private AgentChatResponseDto executeLeaveApplication(PendingLeaveDraft draft, User user) {
        LeaveType leaveType = draft.getLeaveType();
        LeaveType combinedWithType = draft.getCombinedWithType();
        LocalDate startDate = draft.getStartDate();
        LocalDate endDate = draft.getEndDate() != null ? draft.getEndDate() : startDate;
        boolean isHalfDay = draft.isHalfDay();

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Auto-attach digital document placeholder for Sick Leave > 2 days via agent
        boolean docAttached = draft.isDocAttached();
        String docUrl = null;
        if (leaveType == LeaveType.SICK && daysBetween > 2) {
            docAttached = true;
            docUrl = "https://documents.peoplefirst.internal/agent-upload-" + UUID.randomUUID() + ".pdf";
        }

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setLeaveType(leaveType);
        dto.setCombinedWithType(combinedWithType);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setHalfDay(isHalfDay);
        dto.setHalfDaySession(isHalfDay ? (draft.getHalfDaySession() != null ? draft.getHalfDaySession() : "FIRST_HALF") : null);
        dto.setReason(draft.getReason() != null ? draft.getReason() : "Applied via Kura AI Agent");
        dto.setDocumentAttached(docAttached);
        dto.setDocumentUrl(docUrl);

        try {
            LeaveResponseDto created = leaveService.applyLeave(dto, user);

            StringBuilder sb = new StringBuilder();
            sb.append("✅ **Leave Request Submitted Successfully!**\n\n")
                    .append("• **Type:** ").append(created.getLeaveTypeDisplayName()).append("\n")
                    .append("• **Dates:** ").append(created.getStartDate()).append(" to ").append(created.getEndDate())
                    .append(" (").append(created.getTotalDays()).append(" day").append(created.getTotalDays() > 1 ? "s" : "").append(")\n")
                    .append("• **Status:** ").append(created.getStatus()).append("\n");

            if (created.getCombinedWithType() != null) {
                sb.append("• **Combined With:** ").append(created.getCombinedWithType().getDisplayName()).append("\n");
            }

            if (leaveType == LeaveType.SICK && daysBetween > 2) {
                sb.append("• **Medical Certificate:** Digital placeholder attached for manager review (`DOC-")
                        .append(UUID.randomUUID().toString().substring(0, 8).toUpperCase()).append("`)\n");
            }

            if (leaveType == LeaveType.SICK && isHalfDay) {
                sb.append("\n\n🛏️ If you're unwell and nearby, you can rest in the office sick room (**Floor 6, Room 7**) before heading home — just let reception know.");
            }

            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("APPLY_LEAVE");
            response.setActionData(created);

            // Layer wellbeing suggestions (§6)
            try {
                List<WellbeingSuggestionDto> wellbeingSuggestions = wellbeingService.evaluateLeaveWellbeing(
                        leaveService.getLeaveEntityById(created.getId()), user);
                response.setWellbeingSuggestions(wellbeingSuggestions);
            } catch (Exception ignored) {}

            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;

        } catch (PolicyViolationException pve) {
            String msg = pve.getMessage();
            StringBuilder sb = new StringBuilder("❌ **Policy Check Notice:** ").append(msg);
            AgentChatResponseDto response = new AgentChatResponseDto();
            response.setIntent(AgentIntent.APPLY_LEAVE.name());

            // Handle Paid Leave notice violation constructively with auto-suggestion
            if (leaveType == LeaveType.PAID && msg.contains("advance notice")) {
                LocalDate earliestValid = LocalDate.now().plusDays(3);
                sb.append("\n\n💡 Would you like me to submit this Paid Leave starting on the earliest permitted date (**")
                        .append(earliestValid).append("**)?");

                // Save draft ready for confirmation
                PendingLeaveDraft retryDraft = new PendingLeaveDraft();
                retryDraft.setLeaveType(LeaveType.PAID);
                retryDraft.setStartDate(earliestValid);
                long dur = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
                retryDraft.setEndDate(earliestValid.plusDays(dur - 1));
                userDrafts.put(user.getId(), retryDraft);

                response.setQuickReplies(List.of(
                        "Yes, apply from " + earliestValid,
                        "Sick Leave instead",
                        "Cancel"
                ));
            } else {
                response.setQuickReplies(List.of("Raise a support ticket", "Read company leave policies", "Check balance"));
            }

            response.setReply(sb.toString());
            return response;
        }
    }

    private List<String> getPostActionQuickReplies(User user) {
        return List.of("Check my balances", "View my leaves", "Company leave policies", "Explore amenities");
    }

    private AgentChatResponseDto handleApprovalInbox(String message, User user) {
        List<LeaveResponseDto> pendings = approvalService.getPendingApprovals(user);
        if (pendings.isEmpty()) {
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You have no pending approvals.", AgentIntent.APPROVE_LEAVES.name());
            response.setActionExecuted(false);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        }

        int ordinal = intentParser.parseApprovalOrdinal(message);
        if (ordinal >= 1) {
            if (ordinal > pendings.size()) {
                return approvalListReply(pendings, user, "I couldn't find #" + ordinal + ".");
            }
            LeaveResponseDto target = pendings.get(ordinal - 1);
            boolean approve = message.toLowerCase().trim().startsWith("approve");
            try {
                ApprovalActionDto action = new ApprovalActionDto();
                action.setComment(approve ? "Approved via Kura" : "Rejected via Kura");
                LeaveResponseDto result = approve
                        ? approvalService.approveLeave(target.getId(), action, user)
                        : approvalService.rejectLeave(target.getId(), action, user);
                String verb = approve ? "Approved" : "Rejected";
                String reply = "✅ " + verb + " " + result.getEmployeeName() + "'s "
                        + result.getLeaveTypeDisplayName() + " (" + result.getStartDate()
                        + " to " + result.getEndDate() + ").";
                AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.APPROVE_LEAVES.name());
                response.setActionExecuted(true);
                response.setActionName(approve ? "APPROVE_LEAVE" : "REJECT_LEAVE");
                response.setActionData(result);
                response.setQuickReplies(getPostActionQuickReplies(user));
                return response;
            } catch (AccessDeniedException e) {
                AgentChatResponseDto denied = new AgentChatResponseDto(
                        "You can only act on your direct reportees' requests.",
                        AgentIntent.APPROVE_LEAVES.name());
                denied.setActionExecuted(false);
                denied.setQuickReplies(getPostActionQuickReplies(user));
                return denied;
            }
        }

        return approvalListReply(pendings, user, null);
    }

    private AgentChatResponseDto approvalListReply(List<LeaveResponseDto> pendings, User user, String note) {
        StringBuilder sb = new StringBuilder("Here are the pending team leave requests:\n\n");
        for (int i = 0; i < pendings.size(); i++) {
            LeaveResponseDto l = pendings.get(i);
            sb.append(i + 1).append(". ").append(l.getEmployeeName()).append(" — ")
                    .append(l.getLeaveTypeDisplayName()).append(" ")
                    .append(l.getStartDate()).append(" to ").append(l.getEndDate())
                    .append(" (").append(formatDays(l.getTotalDays())).append("d)\n");
        }
        sb.append("\nReply `approve 1` or `reject 2`.");
        if (note != null && !note.isBlank()) {
            sb.append("\n").append(note);
        }
        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPROVE_LEAVES.name());
        response.setActionExecuted(false);
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private String formatDays(double days) {
        return (days == Math.floor(days) && !Double.isInfinite(days))
                ? String.valueOf((long) days) : String.valueOf(days);
    }

    private AgentChatResponseDto executeApprovalAction(PendingAgentAction pending, User user) {
        boolean approve = AgentTool.APPROVE_LEAVE.getName().equals(pending.getToolName());
        UUID leaveId = null;
        String comment = null;
        try {
            JsonNode args = new ObjectMapper()
                    .readTree(pending.getArgumentsJson() != null ? pending.getArgumentsJson() : "{}");
            String idText = args.path("leaveId").asText(null);
            if (idText != null && !idText.isBlank()) {
                leaveId = UUID.fromString(idText.trim());
            }
            comment = args.path("comment").asText(null);
        } catch (Exception e) {
            leaveId = null;
        }
        if (leaveId == null) {
            AgentChatResponseDto invalid = new AgentChatResponseDto(
                    "That leave ID didn't look valid — please try again.",
                    AgentIntent.APPROVE_LEAVES.name());
            invalid.setActionExecuted(false);
            invalid.setQuickReplies(getPostActionQuickReplies(user));
            return invalid;
        }
        ApprovalActionDto action = new ApprovalActionDto();
        action.setComment((comment != null && !comment.isBlank())
                ? comment : (approve ? "Approved via Kura" : "Rejected via Kura"));
        LeaveResponseDto result = approve
                ? approvalService.approveLeave(leaveId, action, user)
                : approvalService.rejectLeave(leaveId, action, user);
        String verb = approve ? "Approved" : "Rejected";
        String reply = "✅ " + verb + " " + result.getEmployeeName() + "'s "
                + result.getLeaveTypeDisplayName() + " (" + result.getStartDate()
                + " to " + result.getEndDate() + ").";
        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.APPROVE_LEAVES.name());
        response.setActionExecuted(true);
        response.setActionName(approve ? "APPROVE_LEAVE" : "REJECT_LEAVE");
        response.setActionData(result);
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private AgentChatResponseDto handleCancelLeave(String message, User user) {
        List<LeaveResponseDto> leaves = leaveService.getLeavesForUser(user.getId());
        List<LeaveResponseDto> cancellable = leaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.PENDING || l.getStatus() == LeaveStatus.APPROVED) &&
                        LocalDate.now().isBefore(l.getStartDate()))
                .collect(Collectors.toList());

        if (cancellable.isEmpty()) {
            return new AgentChatResponseDto(
                    "You do not have any upcoming pending or approved leave requests that can be cancelled before start date.",
                    AgentIntent.CANCEL_LEAVE.name()
            );
        }

        // Cancel the earliest upcoming cancellable leave
        LeaveResponseDto target = cancellable.get(0);
        LeaveResponseDto cancelled = leaveService.cancelLeave(target.getId(), user, "Cancelled via Kura AI Agent");

        String reply = "✅ Your " + cancelled.getLeaveTypeDisplayName() + " from " + cancelled.getStartDate() +
                " to " + cancelled.getEndDate() + " has been cancelled. Your leave balance has been restored.";

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.CANCEL_LEAVE.name());
        response.setActionExecuted(true);
        response.setActionName("CANCEL_LEAVE");
        response.setActionData(cancelled);
        response.setQuickReplies(List.of("Check my balances", "Apply for new leave"));
        return response;
    }

    private AgentChatResponseDto handleViewLeaves(User user) {
        List<LeaveResponseDto> leaves = leaveService.getLeavesForUser(user.getId());
        if (leaves.isEmpty()) {
            return new AgentChatResponseDto("You haven't submitted any leave requests yet.", AgentIntent.VIEW_LEAVES.name());
        }

        StringBuilder sb = new StringBuilder("Here are your recent leave applications:\n\n");
        int count = 0;
        for (LeaveResponseDto l : leaves) {
            if (count++ >= 5) break;
            sb.append("• **").append(l.getLeaveTypeDisplayName()).append("**: ")
                    .append(l.getStartDate()).append(" to ").append(l.getEndDate())
                    .append(" (").append(l.getTotalDays()).append(" days) — **")
                    .append(l.getStatus()).append("**\n");
        }

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.VIEW_LEAVES.name());
        response.setActionExecuted(true);
        response.setActionName("VIEW_LEAVES");
        response.setActionData(leaves);
        return response;
    }

    private AgentChatResponseDto handleCheckPolicy(User user) {
        PolicyResponseDto policy = policyService.getCompanyPolicies();
        StringBuilder sb = new StringBuilder("📋 **Company Leave Policies & Rules**:\n\n");

        sb.append("**Key Deadlines & Notice Periods:**\n");
        for (String r : policy.getDeadlineRules()) {
            sb.append("• ").append(r).append("\n");
        }

        sb.append("\n**Leave Combination Rules:**\n");
        for (String r : policy.getCombinationRules()) {
            sb.append("• ").append(r).append("\n");
        }

        if (user.isContractor()) {
            sb.append("\n⚠️ **Contractor Guidelines:**\n")
                    .append("• Eligible types: Sick (16 days), Paid (24 days), LOP (30 days).\n")
                    .append("• Casual, WFH, Maternity, and Volunteering are not applicable.\n")
                    .append("• No combination rights permitted.\n")
                    .append("• Interaction is exclusively supported through the Kura AI Agent.");
        }

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.CHECK_POLICY.name());
        response.setActionExecuted(true);
        response.setActionName("CHECK_POLICY");
        response.setActionData(policy);
        response.setQuickReplies(List.of("Check my balances", "Apply for leave"));
        return response;
    }

    private AgentChatResponseDto handleStressExpression(String message, User user) {
        WellbeingSuggestionDto stressSuggestion = wellbeingService.evaluateStressMessage(message);
        String reply;

        if (genAiClient.isConfigured()) {
            String systemContext = buildSystemContext(user);
            String prompt = "The user expressed stress or fatigue: \"" + message + "\". Provide a compassionate, supportive response acknowledging their pressure, and warmly advise taking a break to use our on-campus relaxation facilities (Zero-Gravity Massage Recliners in Bldg 1 4th floor, Recreational Lounge in Bldg 3, or on-site Psychologist counseling in Bldg 2).";
            Optional<String> genAiReply = genAiClient.generateContent(systemContext, prompt);
            reply = genAiReply.orElseGet(() -> getDefaultStressReply());
        } else {
            reply = getDefaultStressReply();
        }

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.STRESS_EXPRESSION.name());
        if (stressSuggestion != null) {
            response.setWellbeingSuggestions(List.of(stressSuggestion));
        }
        response.setQuickReplies(List.of("Apply for leave", "View partner resorts", "Healthcare discounts"));
        return response;
    }

    private String getDefaultStressReply() {
        return "I hear you, and please remember your wellbeing is our top priority. " +
                "Taking pause during stressful sprints helps restore mental balance.\n\n" +
                "Here are a few on-site amenities you can access right now:\n" +
                "• **Zero-Gravity Massage Recliners** (Building 1, 4th Floor Relaxation Pod)\n" +
                "• **Recreational Lounge** (Table tennis, snooker, carrom, and chess in Building 3, 3rd Floor)\n" +
                "• **Confidential Psychological Counseling** with our on-site psychologist (Building 2, 2nd Floor)\n" +
                "• **Guided Yoga Sessions** (Building 1, Terrace Hall)";
    }

    private AgentChatResponseDto handleWellbeingInquiry(String message, User user) {
        String lower = message.toLowerCase();
        StringBuilder sb = new StringBuilder();
        AgentChatResponseDto response = new AgentChatResponseDto();

        if (lower.contains("hospital") || lower.contains("doctor") || lower.contains("medical")) {
            List<HospitalPartnerDto> hospitals = wellbeingService.getHospitalPartners(user.getBaseLocation());
            sb.append("🏥 **Partner Hospitals & Healthcare Discounts (").append(user.getBaseLocation()).append(")**:\n\n");
            for (HospitalPartnerDto h : hospitals) {
                sb.append("• **").append(h.getName()).append("** (").append(h.getAddress()).append(")\n")
                        .append("   - OPD Discount: ").append(h.getOpdDiscount()).append("\n")
                        .append("   - Lab / Diagnostic: ").append(h.getLabTestDiscount()).append("\n")
                        .append("   - Phone: ").append(h.getContactNumber()).append("\n\n");
            }
            sb.append("💡 Note: For insurance reimbursement claims, submit OPD/hospital bills within 90 days.");
            response.setActionData(hospitals);
        } else if (lower.contains("resort") || lower.contains("vacation") || lower.contains("hotel")) {
            sb.append("🌴 **Partner Resorts & Corporate Vacation Getaways**:\n\n");
            wellbeingService.getResortPartners().forEach(r -> {
                sb.append("• **").append(r.getName()).append("** (").append(r.getDestination()).append(")\n")
                        .append("   - Benefit: ").append(r.getDiscount()).append(" | Code: `").append(r.getCouponCode()).append("`\n\n");
            });
            response.setActionData(wellbeingService.getResortPartners());
        } else {
            sb.append("✨ **peopleFirst Campus Amenities Catalog**:\n\n");
            wellbeingService.getAllAmenities().forEach(a -> {
                sb.append("• **").append(a.getName()).append("** (").append(a.getLocation()).append(")\n")
                        .append("   - Hours: ").append(a.getTiming()).append(" | ").append(a.getDescription()).append("\n\n");
            });
            response.setActionData(wellbeingService.getAllAmenities());
        }

        response.setReply(sb.toString());
        response.setIntent(AgentIntent.WELLBEING_INQUIRY.name());
        response.setActionExecuted(true);
        response.setQuickReplies(List.of("Check leave balance", "Apply for leave", "Leave policies"));
        return response;
    }

    private AgentChatResponseDto handleTicketInquiry(User user) {
        String reply = "🎫 **Support Tickets & Policy Exception Desk**\n\n" +
                "You can raise a support ticket for:\n" +
                "• Late Casual/WFH submissions after the end of the leave week\n" +
                "• Late Sick/Paid/LOP requests submitted after the 25th of the month\n" +
                "• Post-date adjustments and corrections for leaves whose dates have already passed\n" +
                "• Technical errors encountered during leave submission\n\n" +
                "Would you like to open the support ticket submission form?";

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.TICKET_INQUIRY.name());
        response.setQuickReplies(List.of("Raise a support ticket", "Check leave balance", "Leave policies"));
        return response;
    }

    private AgentChatResponseDto handleUnknown(String message, User user) {
        if (genAiClient.isConfigured()) {
            String systemContext = buildSystemContext(user);
            Optional<String> genAiReply = genAiClient.generateContent(systemContext, message);
            if (genAiReply.isPresent()) {
                AgentChatResponseDto response = new AgentChatResponseDto(genAiReply.get(), AgentIntent.UNKNOWN.name());
                response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
                return response;
            }
        }

        String reply = "I didn't quite catch that. As **Kura**, I can help you check your balances, apply for leave, view company rules, or recommend wellbeing amenities. What would you like to do?";
        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.UNKNOWN.name());
        response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
        return response;
    }

    public Map<String, Object> getAgentStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("agentName", "Kura");
        status.put("role", "Autonomous Leave Management & Wellbeing Concierge");
        status.put("genAiConfigured", genAiClient.isConfigured());
        status.put("genAiModel", genAiClient.getModel());
        status.put("architecture", "Hybrid: Google Generative AI (Gemini) + Grounded Spring Boot Policy Engine");
        status.put("agentMode", genAiClient.isConfigured() ? "agentic" : "rule-based");
        String provider = genAiClient.getProvider();
        status.put("genAiProvider", (provider != null && !provider.isBlank()) ? provider : "auto");
        status.put("genAiEndpointReachable", probeGenAiEndpointReachable());
        return status;
    }

    private boolean probeGenAiEndpointReachable() {
        try {
            String baseUrl = genAiClient.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                return false;
            }
            String url = baseUrl.trim().replaceAll("/+$", "") + "/models";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public void updateGenAiKey(String apiKey) {
        genAiClient.setApiKey(apiKey);
    }

    public static class PendingLeaveDraft {
        private LeaveType leaveType;
        private LeaveType combinedWithType;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean halfDay = false;
        private String halfDaySession;
        private boolean docAttached = false;
        private String reason;
        private long createdAt = System.currentTimeMillis();

        public boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > (15 * 60 * 1000L); // 15 mins expiry
        }

        public LeaveType getLeaveType() { return leaveType; }
        public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
        public LeaveType getCombinedWithType() { return combinedWithType; }
        public void setCombinedWithType(LeaveType combinedWithType) { this.combinedWithType = combinedWithType; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public boolean isHalfDay() { return halfDay; }
        public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
        public String getHalfDaySession() { return halfDaySession; }
        public void setHalfDaySession(String halfDaySession) { this.halfDaySession = halfDaySession; }
        public boolean isDocAttached() { return docAttached; }
        public void setDocAttached(boolean docAttached) { this.docAttached = docAttached; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    private static class PendingAgentAction {
        private final String toolName;
        private final String argumentsJson;
        private final long createdAt = System.currentTimeMillis();

        PendingAgentAction(String toolName, String argumentsJson) {
            this.toolName = toolName;
            this.argumentsJson = argumentsJson;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > (15 * 60 * 1000L); // 15 mins expiry
        }

        String getToolName() { return toolName; }
        String getArgumentsJson() { return argumentsJson; }
    }
}
