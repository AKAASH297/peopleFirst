package com.peoplefirst.agent.service;

import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.AgentIntent;
import com.peoplefirst.agent.intent.ConversationContext;
import com.peoplefirst.agent.intent.FuzzyMatcher;
import com.peoplefirst.agent.intent.IntentParser;
import com.peoplefirst.approval.dto.ApprovalActionDto;
import com.peoplefirst.approval.service.ApprovalService;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.AdminDirectEditDto;
import com.peoplefirst.leave.dto.CreateLeaveRequestDto;
import com.peoplefirst.leave.dto.LeaveBalanceDto;
import com.peoplefirst.leave.dto.LeaveResponseDto;
import com.peoplefirst.leave.dto.UpdateLeaveRequestDto;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.policy.dto.PolicyResponseDto;
import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.policy.service.PolicyService;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.ticket.dto.CreateTicketRequestDto;
import com.peoplefirst.ticket.dto.TicketResponseDto;
import com.peoplefirst.ticket.service.TicketService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import com.peoplefirst.volunteering.service.VolunteeringService;
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.ResortPartnerDto;
import com.peoplefirst.wellbeing.dto.WeeklyWellbeingDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.service.WellbeingService;
import com.peoplefirst.agent.tools.AgentTool;
import com.peoplefirst.agent.tools.AgentToolCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final TicketService ticketService;
    private final UserService userService;
    private final VolunteeringService volunteeringService;

    // Multi-turn conversational leave draft store keyed by User UUID
    private final Map<UUID, PendingLeaveDraft> userDrafts = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEditDraft> userEditDrafts = new ConcurrentHashMap<>();
    private final Map<UUID, ConversationContext> userContexts = new ConcurrentHashMap<>();

    // Agentic loop state: per-conversation message history and pending write confirmations
    private final Map<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingAgentAction> pendingActions = new ConcurrentHashMap<>();

    // Post-apply volunteering CSR signup state keyed by User UUID
    private final Map<UUID, PendingVolunteeringSignup> volunteeringSignups = new ConcurrentHashMap<>();

    // Exact CSR chapter names from VolunteeringWellbeingRule (groupSuggestions)
    private static final List<String> CSR_GROUPS = List.of(
            "Green Earth Afforestation Drive",
            "Code & Tech Literacy for Underprivileged Youth",
            "Community Food Bank & Kitchen Support",
            "Paws & Care Animal Rescue");
    private static final String CSR_ENROLL_URL = "https://csr.peoplefirst.internal/enroll";

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
                        ApprovalService approvalService,
                        TicketService ticketService,
                        UserService userService,
                        VolunteeringService volunteeringService) {
        this.intentParser = intentParser;
        this.currentUserProvider = currentUserProvider;
        this.leaveService = leaveService;
        this.leaveBalanceService = leaveBalanceService;
        this.policyService = policyService;
        this.wellbeingService = wellbeingService;
        this.leaveMapper = leaveMapper;
        this.genAiClient = genAiClient;
        this.approvalService = approvalService;
        this.ticketService = ticketService;
        this.userService = userService;
        this.volunteeringService = volunteeringService;
    }

    public void clearDrafts() {
        userDrafts.clear();
        userEditDrafts.clear();
        userContexts.clear();
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

        // 1. Load or create conversation context for this user
        ConversationContext ctx = userContexts.computeIfAbsent(user.getId(), k -> new ConversationContext());
        if (ctx.isExpired()) {
            ctx = new ConversationContext();
            userContexts.put(user.getId(), ctx);
        }

        // 2. Manage active drafts
        PendingLeaveDraft draft = userDrafts.get(user.getId());
        if (draft != null && draft.isExpired()) {
            userDrafts.remove(user.getId());
            draft = null;
        }

        PendingEditDraft editDraft = userEditDrafts.get(user.getId());
        if (editDraft != null && editDraft.isExpired()) {
            userEditDrafts.remove(user.getId());
            editDraft = null;
        }

        // Check if user explicitly wants to cancel an active draft
        if ((draft != null || editDraft != null) && (lower.equals("cancel") || lower.equals("stop") || lower.equals("abort") ||
                lower.equals("never mind") || lower.equals("nevermind") || lower.equals("cancel draft") || lower.equals("keep my leaves") ||
                lower.equals("nahi") || lower.equals("rehne do") || lower.equals("mat karo"))) {
            userDrafts.remove(user.getId());
            userEditDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "Your active draft session has been closed. What else can I assist you with today?",
                    AgentIntent.GREETING.name()
            );
            response.setQuickReplies(getPostActionQuickReplies(user));
            return updateContextAndReturn(response, user, AgentIntent.GREETING, ConversationContext.PromptType.GENERAL);
        }

        // 3. Context-aware intent parsing (3-tier: exact → fuzzy → context)
        AgentIntent intent = intentParser.parseIntent(message, ctx);

        // Explicit command/inquiry intents switch context and clear stale in-flight drafts
        boolean isExplicitOtherIntent = (intent == AgentIntent.CHECK_BALANCE ||
                intent == AgentIntent.CHECK_TEAM_BALANCES ||
                intent == AgentIntent.VIEW_LEAVES ||
                intent == AgentIntent.VIEW_PENDING_APPROVALS ||
                intent == AgentIntent.APPROVE_LEAVE ||
                intent == AgentIntent.REJECT_LEAVE ||
                (intent == AgentIntent.WELLBEING_INQUIRY && (draft == null || !draft.isAwaitingReason())) ||
                intent == AgentIntent.CHECK_POLICY ||
                intent == AgentIntent.RAISE_TICKET ||
                intent == AgentIntent.ADMIN_DIRECT_EDIT ||
                intent == AgentIntent.CANCEL_LEAVE ||
                intent == AgentIntent.EDIT_LEAVE ||
                intent == AgentIntent.GREETING);

        if (isExplicitOtherIntent) {
            userDrafts.remove(user.getId());
            userEditDrafts.remove(user.getId());
            userContexts.remove(user.getId());
            draft = null;
            editDraft = null;
        }

        PendingVolunteeringSignup signup = volunteeringSignups.get(user.getId());
        if (signup != null && signup.isExpired()) {
            volunteeringSignups.remove(user.getId());
            signup = null;
        }
        if (signup != null) {
            if (isExplicitOtherIntent) {
                volunteeringSignups.remove(user.getId());
            } else {
                return continueVolunteeringSignup(message, signup, user);
            }
        }

        // If user explicitly asks to start a new/fresh leave application, discard any old stale draft
        boolean isFreshApply = lower.equals("apply for a leave") || lower.equals("apply for leave") ||
                lower.equals("apply leave") || lower.equals("i want to apply leave") ||
                lower.equals("i want to apply for a leave") || lower.equals("i want to apply for leave") ||
                lower.equals("apply a leave") || lower.equals("apply") || lower.equals("new leave") ||
                lower.equals("request leave") || lower.equals("request a leave") || lower.equals("apply for another leave") ||
                lower.equals("apply for new leave") || lower.equals("start over") || lower.equals("restart") ||
                lower.equals("chutti chahiye") || lower.equals("chutti lena hai") || lower.equals("leave chahiye") ||
                lower.matches("^(?:i want to |i would like to |can i |please |kura )?(?:apply|request|take|book)(?: for)?(?: a| another| new)? leave.*$");

        // Discard draft if:
        // a) User issues a fresh apply phrase, or
        // b) User provided a complete fresh application with both leave type and dates, or
        // c) User was awaiting reason or prompt confirmation, but issued an APPLY command instead of a reason
        if (isFreshApply ||
                (intent == AgentIntent.APPLY_LEAVE && intentParser.extractLeaveType(message) != null && intentParser.extractDates(message)[0] != null) ||
                (draft != null && draft.isAwaitingReason() && (isFreshApply || lower.startsWith("apply") || lower.startsWith("request leave")))) {
            userDrafts.remove(user.getId());
            draft = null;
        }

        if (editDraft != null && !isExplicitOtherIntent) {
            return continueEditDraft(message, editDraft, user);
        }

        if (draft != null) {
            return continueLeaveDraft(message, draft, user);
        }

        switch (intent) {
            case GREETING:
                return handleGreeting(user);
            case CHECK_BALANCE:
                return handleCheckBalance(message, user);
            case CHECK_TEAM_BALANCES:
                return handleCheckTeamBalances(message, user);
            case APPLY_LEAVE:
                return handleApplyLeave(message, user);
            case CANCEL_LEAVE:
                return handleCancelLeave(message, user);
            case EDIT_LEAVE:
                return handleEditLeave(message, user);
            case VIEW_LEAVES:
                return handleViewLeaves(user);
            case VIEW_PENDING_APPROVALS:
                return handleViewPendingApprovals(user);
            case APPROVE_LEAVE:
                return handleApproveLeave(message, user);
            case REJECT_LEAVE:
                return handleRejectLeave(message, user);
            case SEND_BACK_LEAVE:
                return handleSendBackLeave(message, user);
            case CHECK_POLICY:
                return handleCheckPolicy(user);
            case STRESS_EXPRESSION:
                return handleStressExpression(message, user);
            case WELLBEING_INQUIRY:
                return handleWellbeingInquiry(message, user);
            case TICKET_INQUIRY:
                return handleTicketInquiry(user);
            case RAISE_TICKET:
                return handleRaiseTicket(message, user);
            case ADMIN_DIRECT_EDIT:
                return handleAdminDirectEdit(message, user);
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
                return agentUnavailable();
            }

            JsonNode assistant;
            try {
                assistant = mapper.readTree(raw.get());
            } catch (Exception e) {
                return agentUnavailable();
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
                    case LIST_PENDING_APPROVALS -> {
                        AgentChatResponseDto toolResponse = handleListPendingApprovals(user);
                        lastToolResponse = toolResponse;
                        appendToHistory(history, Map.of("role", "tool",
                                "tool_call_id", toolCallId, "content", toCompactJson(toCompactApprovalList(toolResponse.getActionData()))));
                    }
                    case APPLY_LEAVE, CANCEL_LEAVE, APPROVE_LEAVE, REJECT_LEAVE -> {
                        pendingActions.put(user.getId(), new PendingAgentAction(tool.getName(), argumentsJson));
                        String intent = tool == AgentTool.APPLY_LEAVE
                                ? AgentIntent.APPLY_LEAVE.name()
                                : tool == AgentTool.CANCEL_LEAVE
                                ? AgentIntent.CANCEL_LEAVE.name()
                                : tool == AgentTool.APPROVE_LEAVE
                                ? AgentIntent.APPROVE_LEAVE.name() : AgentIntent.REJECT_LEAVE.name();
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
        return agentUnavailable();
    }

    private AgentChatResponseDto agentUnavailable() {
        AgentChatResponseDto response = new AgentChatResponseDto(
                "Kura's AI brain isn't reachable right now — please try again in a moment. Nothing was submitted.",
                AgentIntent.UNKNOWN.name());
        response.setActionExecuted(false);
        response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies"));
        return response;
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
            sb.append("To approve: FIRST call list_pending_approvals to get real request IDs, present them, then approve/reject ONLY a listed ID after the user confirms. NEVER invent IDs; NEVER claim the inbox is empty without calling it.\n");
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
        return updateContextAndReturn(response, user, AgentIntent.CHECK_BALANCE, ConversationContext.PromptType.GENERAL);
    }

    private AgentChatResponseDto continueLeaveDraft(String message, PendingLeaveDraft draft, User user) {
        String lower = message.toLowerCase().trim();

        // 1. Check if user specified today, a backdate or past date
        LocalDate[] dates = intentParser.extractDates(message);
        if (lower.contains("back date") || lower.contains("backdate") || lower.contains("past date") ||
                lower.contains("past dates") || lower.contains("retroactiv") || lower.contains("today") ||
                (dates[0] != null && !dates[0].isAfter(LocalDate.now()))) {
            draft.setStartDate(null);
            draft.setEndDate(null);
            userDrafts.put(user.getId(), draft);
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You can't apply leave for backdate.",
                    AgentIntent.APPLY_LEAVE.name()
            );
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
            return response;
        }

        // 2. If user cancels draft
        if (lower.equals("cancel") || lower.equals("cancel draft") || lower.contains("nevermind") || lower.contains("abort")) {
            userDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "Leave application draft cancelled. Let me know if you would like to do anything else!",
                    AgentIntent.APPLY_LEAVE.name()
            );
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        }

        // 2.5 If user previously offered stress intervention and inquires about amenities
        if (draft.isStressInterventionOffered()) {
            if (lower.contains("massage") || lower.contains("recliner")) {
                StringBuilder sb = new StringBuilder();
                sb.append("🛋️ **Zero-Gravity Recliner Massage Chairs**\n")
                        .append("• **Location:** Building 1, 4th Floor Relaxation Pod\n")
                        .append("• **Hours:** 8:00 AM - 8:00 PM daily\n")
                        .append("• **Features:** Acoustic-dampened pod with heated ergonomic zero-gravity massage recliners. Walk-ins welcome for 30-minute relaxation slots!\n\n")
                        .append("💡 Your staged leave request for **").append(draft.getLeaveType().getDisplayName())
                        .append("** (").append(draft.getStartDate()).append(" to ").append(draft.getEndDate()).append(") is still saved.\n")
                        .append("Would you like to submit your leave request now?");
                AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("✅ Yes, proceed with leave", "🎱 Go to Game Room", "Cancel"));
                return response;
            }
            if (lower.contains("game") || lower.contains("recreation") || lower.contains("snooker") || lower.contains("tennis") || lower.contains("chess") || lower.contains("carrom")) {
                StringBuilder sb = new StringBuilder();
                sb.append("🎱 **Recreational Lounge**\n")
                        .append("• **Location:** Building 3, 3rd Floor\n")
                        .append("• **Hours:** Open 24/7\n")
                        .append("• **Games Available:** Table Tennis, Professional Snooker, Chess, and Carrom boards.\n\n")
                        .append("💡 Your staged leave request for **").append(draft.getLeaveType().getDisplayName())
                        .append("** (").append(draft.getStartDate()).append(" to ").append(draft.getEndDate()).append(") is still saved.\n")
                        .append("Would you like to submit your leave request now?");
                AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("✅ Yes, proceed with leave", "🛋️ Visit Massage Recliners", "Cancel"));
                return response;
            }
        }

        // 3. If user confirms auto-suggestion from Paid Leave notice or prompt or stress intervention
        if (lower.startsWith("yes") || lower.contains("confirm") || lower.contains("proceed") || lower.contains("apply from") ||
                lower.equals("yes, apply") || lower.equals("confirm & apply") || lower.equals("confirm and apply")) {
            if (dates[0] != null) {
                draft.setStartDate(dates[0]);
                draft.setEndDate(dates[1] != null ? dates[1] : dates[0]);
            }
            if (draft.getLeaveType() != null && draft.getStartDate() != null) {
                if (draft.getRawReason() == null || draft.getRawReason().isBlank()) {
                    draft.setRawReason("Scheduled leave");
                    draft.setRefinedReason(intelligentlyRefineReason(draft.getLeaveType(), "Scheduled leave", user));
                }
                userDrafts.remove(user.getId());
                return executeLeaveApplication(draft, user);
            }
        }

        // 4. Awaiting Reason state: User provides reason!
        if (draft.isAwaitingReason()) {
            String reasonInput = (lower.contains("skip") || lower.equals("no") || lower.contains("no reason") ||
                    lower.contains("none") || lower.contains("same reason") || lower.equals("same") || lower.contains("as before"))
                    ? "Personal commitments"
                    : message.trim();
            draft.setRawReason(reasonInput);
            draft.setRefinedReason(intelligentlyRefineReason(draft.getLeaveType(), reasonInput, user));
            draft.setAwaitingReason(false);

            // Pre-application stress intervention on reason provided
            if (isStressExpression(reasonInput) && !draft.isStressInterventionOffered()) {
                draft.setStressInterventionOffered(true);
                userDrafts.put(user.getId(), draft);
                return promptStressIntervention(draft, user);
            }

            userDrafts.remove(user.getId());
            return executeLeaveApplication(draft, user);
        }

        // 5. Update leave type if provided
        LeaveType extractedType = intentParser.extractLeaveType(message);
        if (extractedType != null) {
            draft.setLeaveType(extractedType);
        }
        LeaveType extractedCombined = intentParser.extractCombinedType(message);
        if (extractedCombined != null) {
            draft.setCombinedWithType(extractedCombined);
        }

        // 6. Update dates if provided
        if (dates[0] != null) {
            draft.setStartDate(dates[0]);
            draft.setEndDate(dates[1] != null ? dates[1] : dates[0]);
        }

        if (intentParser.extractHalfDay(message)) {
            draft.setHalfDay(true);
        }
        String session = intentParser.extractHalfDaySession(message);
        if (session != null) {
            draft.setHalfDaySession(session);
        }
        if (intentParser.extractDocumentAttached(message)) {
            draft.setDocAttached(true);
        }

        String rawReason = intentParser.extractRawReason(message);
        if (rawReason != null) {
            draft.setRawReason(rawReason);
            draft.setRefinedReason(intelligentlyRefineReason(draft.getLeaveType(), rawReason, user));
        }

        // If draft still missing leave type
        if (draft.getLeaveType() == null) {
            return promptForLeaveType(user);
        }

        // Check role eligibility & combination rules IMMEDIATELY when leave type is known!
        AgentChatResponseDto policyViolationInDraft = checkPolicyEligibilityAndCombinations(draft, user);
        if (policyViolationInDraft != null) {
            return policyViolationInDraft;
        }

        // If draft is half day and still missing session (morning vs afternoon)
        if (draft.isHalfDay() && draft.getHalfDaySession() == null) {
            return promptForHalfDaySession(draft, user);
        }

        // If draft still missing dates
        if (draft.getStartDate() == null) {
            return promptForDates(draft.getLeaveType(), user);
        }

        // Check Paid Leave advance notice violation
        AgentChatResponseDto noticeViolation = checkPaidLeaveNoticeViolation(draft, user);
        if (noticeViolation != null) {
            return noticeViolation;
        }

        // Check weekend restriction immediately when dates are known
        AgentChatResponseDto weekendViolationInDraft = checkWeekendViolation(draft, user);
        if (weekendViolationInDraft != null) {
            return weekendViolationInDraft;
        }

        // Check Date Overlap violation immediately when dates are known
        AgentChatResponseDto overlapViolationInDraft = checkDateOverlapViolation(draft, user);
        if (overlapViolationInDraft != null) {
            return overlapViolationInDraft;
        }

        // If draft missing reason
        if (draft.getRawReason() == null || draft.getRawReason().isBlank()) {
            draft.setAwaitingReason(true);
            return promptForReason(draft, user);
        }

        // Pre-application stress intervention
        if ((isStressExpression(message) || isStressExpression(draft.getRawReason())) && !draft.isStressInterventionOffered()) {
            draft.setStressInterventionOffered(true);
            userDrafts.put(user.getId(), draft);
            return promptStressIntervention(draft, user);
        }

        // Both present with reason -> execute!
        userDrafts.remove(user.getId());
        return executeLeaveApplication(draft, user);
    }

    private AgentChatResponseDto handleApplyLeave(String message, User user) {
        String lower = message.toLowerCase().trim();
        LeaveType leaveType = intentParser.extractLeaveType(message);
        LeaveType combinedWithType = intentParser.extractCombinedType(message);
        LocalDate[] dates = intentParser.extractDates(message);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];
        boolean isHalfDay = intentParser.extractHalfDay(message);
        String halfDaySession = intentParser.extractHalfDaySession(message);
        boolean docAttached = intentParser.extractDocumentAttached(message);

        // Check if user requested today, a backdate or past date
        if (lower.contains("back date") || lower.contains("backdate") || lower.contains("past date") ||
                lower.contains("past dates") || lower.contains("retroactiv") || lower.contains("today") ||
                (startDate != null && !startDate.isAfter(LocalDate.now()))) {
            PendingLeaveDraft draft = new PendingLeaveDraft();
            draft.setLeaveType(leaveType);
            draft.setCombinedWithType(combinedWithType);
            userDrafts.put(user.getId(), draft);
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You can't apply leave for backdate.",
                    AgentIntent.APPLY_LEAVE.name()
            );
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
            return response;
        }

        PendingLeaveDraft draft = new PendingLeaveDraft();
        draft.setLeaveType(leaveType);
        draft.setCombinedWithType(combinedWithType);
        draft.setStartDate(startDate);
        draft.setEndDate(endDate != null ? endDate : startDate);
        draft.setHalfDay(isHalfDay);
        draft.setHalfDaySession(halfDaySession);
        draft.setDocAttached(docAttached);

        String rawReason = intentParser.extractRawReason(message);
        if (rawReason != null) {
            draft.setRawReason(rawReason);
            draft.setRefinedReason(intelligentlyRefineReason(leaveType, rawReason, user));
        }

        if (leaveType == null) {
            userDrafts.put(user.getId(), draft);
            return promptForLeaveType(user);
        }

        // Check role eligibility & combination rules IMMEDIATELY when leave type is known!
        AgentChatResponseDto policyViolation = checkPolicyEligibilityAndCombinations(draft, user);
        if (policyViolation != null) {
            return policyViolation;
        }

        if (draft.isHalfDay() && draft.getHalfDaySession() == null) {
            userDrafts.put(user.getId(), draft);
            return promptForHalfDaySession(draft, user);
        }

        if (draft.getStartDate() == null) {
            userDrafts.put(user.getId(), draft);
            return promptForDates(leaveType, user);
        }

        // Check Paid Leave advance notice violation
        AgentChatResponseDto noticeViolation = checkPaidLeaveNoticeViolation(draft, user);
        if (noticeViolation != null) {
            return noticeViolation;
        }

        // Check weekend restriction immediately when dates are known
        AgentChatResponseDto weekendViolation = checkWeekendViolation(draft, user);
        if (weekendViolation != null) {
            return weekendViolation;
        }

        // Check Date Overlap violation immediately when dates are known
        AgentChatResponseDto overlapViolation = checkDateOverlapViolation(draft, user);
        if (overlapViolation != null) {
            return overlapViolation;
        }

        // If reason was NOT provided, ask for reason interactively!
        if (draft.getRawReason() == null || draft.getRawReason().isBlank()) {
            draft.setAwaitingReason(true);
            userDrafts.put(user.getId(), draft);
            return promptForReason(draft, user);
        }

        // Pre-application stress intervention
        if ((isStressExpression(message) || isStressExpression(rawReason)) && !draft.isStressInterventionOffered()) {
            draft.setStressInterventionOffered(true);
            userDrafts.put(user.getId(), draft);
            return promptStressIntervention(draft, user);
        }

        // Both present with reason -> execute immediately!
        userDrafts.remove(user.getId());
        return executeLeaveApplication(draft, user);
    }

    private AgentChatResponseDto checkWeekendViolation(PendingLeaveDraft draft, User user) {
        if (draft == null || draft.getStartDate() == null) {
            return null;
        }
        LocalDate start = draft.getStartDate();
        LocalDate end = draft.getEndDate() != null ? draft.getEndDate() : start;

        if (start.getDayOfWeek() == DayOfWeek.SATURDAY || start.getDayOfWeek() == DayOfWeek.SUNDAY ||
                end.getDayOfWeek() == DayOfWeek.SATURDAY || end.getDayOfWeek() == DayOfWeek.SUNDAY) {
            draft.setStartDate(null);
            draft.setEndDate(null);
            userDrafts.put(user.getId(), draft);
            String msg = "❌ **Policy Check Notice:** Leaves cannot be applied on weekends (Saturday or Sunday). Please select working days (Monday to Friday).\n\n" +
                    "When would you like your leave to begin?";
            AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
            response.setQuickReplies(List.of("Next Monday", "Next Week", "Check my balances", "Read company leave policies"));
            return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.DATE);
        }
        return null;
    }

    private AgentChatResponseDto checkPolicyEligibilityAndCombinations(PendingLeaveDraft draft, User user) {
        if (draft == null || draft.getLeaveType() == null) {
            return null;
        }

        LeaveType leaveType = draft.getLeaveType();
        LeaveType combinedType = draft.getCombinedWithType();

        // 1. Role eligibility check (Contractor vs Employee)
        if (!leaveType.isEligibleForUser(user.isContractor())) {
            userDrafts.remove(user.getId());
            String msg = "❌ **Policy Check Notice:** " +
                    (user.isContractor() ? "Contractors" : "Employees") +
                    " are not eligible for " + leaveType.getDisplayName() +
                    ".\n\nEligible leave types: **" +
                    (user.isContractor() ? "Sick Leave, Paid Leave, Loss of Pay (LOP)" : "Casual Leave, Sick Leave, Paid Leave, WFH, Maternity, Volunteering, LOP") +
                    "**.";
            AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
            if (user.isContractor()) {
                response.setQuickReplies(List.of("Sick Leave", "Paid Leave", "Loss of Pay (LOP)", "Read company leave policies"));
            } else {
                response.setQuickReplies(List.of("Casual Leave", "Sick Leave", "Paid Leave", "Work From Home (WFH)"));
            }
            return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.LEAVE_TYPE);
        }

        // 2. Combination rules check
        if (combinedType != null) {
            if (user.isContractor()) {
                userDrafts.remove(user.getId());
                String msg = "❌ **Policy Check Notice:** Contractors do not have access to apply combinations of different leave types.\n\nWhich single leave type would you like to apply for?";
                AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("Sick Leave", "Paid Leave", "Loss of Pay (LOP)"));
                return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.LEAVE_TYPE);
            }

            if (leaveType == LeaveType.CASUAL && combinedType != LeaveType.WFH) {
                userDrafts.remove(user.getId());
                String msg = "❌ **Policy Check Notice:** Casual Leave may only be combined with Work From Home (WFH). Combinations with other leave types are not permitted.";
                AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("Casual Leave + WFH", "Casual Leave only", "Work From Home only", "Read company leave policies"));
                return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.LEAVE_TYPE);
            }

            if (combinedType == LeaveType.CASUAL && leaveType != LeaveType.WFH) {
                userDrafts.remove(user.getId());
                String msg = "❌ **Policy Check Notice:** Casual Leave may only be combined with Work From Home (WFH). Combinations with other leave types are not permitted.";
                AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("Casual Leave + WFH", "Casual Leave only", "Work From Home only", "Read company leave policies"));
                return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.LEAVE_TYPE);
            }
        }

        return null;
    }

    private AgentChatResponseDto checkDateOverlapViolation(PendingLeaveDraft draft, User user) {
        if (draft == null || draft.getStartDate() == null) {
            return null;
        }
        LocalDate start = draft.getStartDate();
        LocalDate end = draft.getEndDate() != null ? draft.getEndDate() : start;

        try {
            List<com.peoplefirst.leave.dto.LeaveResponseDto> userLeaves = leaveService.getLeavesForUser(user.getId());
            if (userLeaves == null || userLeaves.isEmpty()) {
                return null;
            }

            for (com.peoplefirst.leave.dto.LeaveResponseDto existing : userLeaves) {
                if (existing.getStatus() != LeaveStatus.PENDING && existing.getStatus() != LeaveStatus.APPROVED) {
                    continue;
                }

                // An overlap occurs if neither range is completely before or after the other
                boolean rangesOverlap = !(end.isBefore(existing.getStartDate()) || start.isAfter(existing.getEndDate()));
                if (!rangesOverlap) {
                    continue;
                }

                // Special case: both are half-days on the exact same date with different sessions
                if (draft.isHalfDay() && existing.isHalfDay() &&
                        start.equals(end) && existing.getStartDate().equals(existing.getEndDate()) &&
                        start.equals(existing.getStartDate())) {
                    String existingSession = existing.getHalfDaySession() != null ? existing.getHalfDaySession().toUpperCase() : "";
                    String candidateSession = draft.getHalfDaySession() != null ? draft.getHalfDaySession().toUpperCase() : "";
                    if (!existingSession.isEmpty() && !candidateSession.isEmpty() && !existingSession.equals(candidateSession)) {
                        continue;
                    }
                }

                userDrafts.remove(user.getId());
                String typeName = existing.getLeaveType() != null ? existing.getLeaveType().getDisplayName() : "Leave";
                String msg = "❌ You already have an active " + typeName +
                        " (" + existing.getStatus() + ") scheduled from " + existing.getStartDate() +
                        " to " + existing.getEndDate() + ". Overlapping leave requests on the same date are not permitted.\n\n" +
                        "Would you like to choose different dates, or edit/cancel your existing leave?";

                AgentChatResponseDto response = new AgentChatResponseDto(msg, AgentIntent.APPLY_LEAVE.name());
                response.setQuickReplies(List.of("Tomorrow", "Next Week", "Edit my leave", "Cancel my leave", "View my leaves"));
                return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.DATE);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AgentChatResponseDto checkPaidLeaveNoticeViolation(PendingLeaveDraft draft, User user) {
        if (draft.getLeaveType() == LeaveType.PAID && draft.getStartDate() != null &&
                !draft.getStartDate().isAfter(LocalDate.now().plusDays(2))) {
            LocalDate earliestValid = LocalDate.now().plusDays(3);
            while (earliestValid.getDayOfWeek() == DayOfWeek.SATURDAY || earliestValid.getDayOfWeek() == DayOfWeek.SUNDAY) {
                earliestValid = earliestValid.plusDays(1);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("❌ **Policy Check Notice:** Paid Leave requires more than 2 days advance notice.\n\n")
                    .append("💡 Would you like me to submit this Paid Leave starting on the earliest permitted date (**")
                    .append(earliestValid).append("**)?");

            PendingLeaveDraft retryDraft = new PendingLeaveDraft();
            retryDraft.setLeaveType(LeaveType.PAID);
            retryDraft.setStartDate(earliestValid);
            long dur = Math.max(1, ChronoUnit.DAYS.between(draft.getStartDate(), draft.getEndDate() != null ? draft.getEndDate() : draft.getStartDate()) + 1);
            retryDraft.setEndDate(earliestValid.plusDays(dur - 1));
            retryDraft.setRawReason("Annual vacation");
            retryDraft.setRefinedReason(intelligentlyRefineReason(LeaveType.PAID, "Annual vacation", user));
            userDrafts.put(user.getId(), retryDraft);

            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
            response.setQuickReplies(List.of(
                    "Yes, apply from " + earliestValid,
                    "Sick Leave instead",
                    "Cancel"
            ));
            return response;
        }
        return null;
    }

    private AgentChatResponseDto promptForReason(PendingLeaveDraft draft, User user) {
        StringBuilder sb = new StringBuilder();
        double days = draft.isHalfDay() ? 0.5 : (draft.getStartDate() != null && draft.getEndDate() != null
                ? ChronoUnit.DAYS.between(draft.getStartDate(), draft.getEndDate()) + 1
                : 1);

        sb.append("📋 **Leave Details Staged:**\n")
                .append("• **Type:** ").append(draft.getLeaveType().getDisplayName()).append("\n")
                .append("• **Dates:** ").append(IntentParser.formatDate(draft.getStartDate())).append(" to ").append(IntentParser.formatDate(draft.getEndDate()))
                .append(" (").append(draft.isHalfDay() ? "0.5 day" : ((long)days + " day" + (days > 1 ? "s" : ""))).append(")\n");
        if (draft.isHalfDay()) {
            sb.append("• **Duration:** Half Day (")
                    .append(draft.getHalfDaySession() != null ? draft.getHalfDaySession() : "FIRST_HALF")
                    .append(")\n");
        }
        sb.append("\nCould you share the **reason** for your leave? (Just mention a few words like *'fever'*, *'family wedding'*, *'doctor visit'*, or *'personal work'*, and I will polish it into a formal corporate justification for your manager!)");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(getReasonChips(draft.getLeaveType()));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.REASON);
    }

    private List<String> getReasonChips(LeaveType leaveType) {
        if (leaveType == LeaveType.SICK) {
            return List.of("Viral fever & rest", "Severe headache / migraine", "Doctor consultation", "Family medical care", "Skip Reason");
        } else if (leaveType == LeaveType.PAID) {
            return List.of("Annual family vacation", "Visiting hometown", "Personal celebration", "Rest & recharge", "Skip Reason");
        } else if (leaveType == LeaveType.CASUAL || leaveType == LeaveType.WFH) {
            return List.of("Personal work & bank errand", "Family function / wedding", "Home maintenance", "Personal emergency", "Skip Reason");
        } else {
            return List.of("Personal commitment", "Family care", "Wellness rest", "Skip Reason");
        }
    }

    private boolean isStressExpression(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return lower.contains("stress") || lower.contains("burnout") || lower.contains("pressure") ||
                lower.contains("exhaust") || lower.contains("overwhelm") || lower.contains("tired") ||
                lower.contains("fatigue") || lower.contains("drained") || lower.contains("overworked");
    }

    private AgentChatResponseDto promptStressIntervention(PendingLeaveDraft draft, User user) {
        long days = draft.getStartDate() != null && draft.getEndDate() != null
                ? ChronoUnit.DAYS.between(draft.getStartDate(), draft.getEndDate()) + 1
                : 1;
        String typeName = draft.getLeaveType() != null ? draft.getLeaveType().getDisplayName() : "Leave";

        StringBuilder sb = new StringBuilder();
        sb.append("💙 **Your Wellbeing Comes First**\n\n")
                .append("I understand that you are experiencing stress, pressure, or fatigue. ")
                .append("Before proceeding with your leave, remember peopleFirst offers several immediate on-campus wellness amenities to help you relax and recharge:\n\n")
                .append("• 🛋️ **Zero-Gravity Massage Recliners** (Building 1, 4th Floor Relaxation Pod — acoustic-dampened room with heated ergonomic massage slots)\n")
                .append("• 🎱 **Recreational Lounge** (Building 3, 3rd Floor — Table Tennis, Snooker, Carrom & Chess open 24/7)\n")
                .append("• 🏋️ **Gym & Fitness Hub / Zumba Studio** (Building 1, Basement Level & 5th Floor Terrace)\n")
                .append("• 🧠 **Confidential Psychologist Consultation** (Building 2, 2nd Floor Quiet Zone)\n")
                .append("• 🧘 **Yoga & Mindfulness Studio** (Building 1, 5th Floor Terrace Hall)\n")
                .append("• 🩺 **On-Site General Physician (GP)** (Building 2, 1st Floor Health Center, 9:00 AM - 5:00 PM)\n\n")
                .append("📋 **Staged Leave Request:** ").append(typeName).append(" (").append(IntentParser.formatDate(draft.getStartDate()))
                .append(draft.getEndDate() != null && !draft.getEndDate().equals(draft.getStartDate()) ? " to " + IntentParser.formatDate(draft.getEndDate()) : "")
                .append(", ").append(days).append(" day").append(days > 1 ? "s" : "").append(")\n");
        if (draft.getRefinedReason() != null && !draft.getRefinedReason().isBlank()) {
            sb.append("• **Corporate Justification:** *\"").append(draft.getRefinedReason()).append("\"*\n\n");
        } else {
            sb.append("\n");
        }
        sb.append("Would you still like to proceed with submitting your leave request?");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(List.of("✅ Yes, proceed with leave", "🛋️ Visit Massage Recliners", "🎱 Go to Game Room", "Cancel"));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.STRESS_FOLLOWUP);
    }

    private AgentChatResponseDto promptConfirmation(PendingLeaveDraft draft, User user) {
        long days = ChronoUnit.DAYS.between(draft.getStartDate(), draft.getEndDate()) + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("✨ **Leave Request Ready for Review:**\n\n")
                .append("• **Leave Type:** ").append(draft.getLeaveType().getDisplayName()).append("\n")
                .append("• **Dates:** ").append(IntentParser.formatDate(draft.getStartDate())).append(" to ").append(IntentParser.formatDate(draft.getEndDate()))
                .append(" (").append(days).append(" day").append(days > 1 ? "s" : "").append(")\n");
        if (draft.isHalfDay()) {
            sb.append("• **Duration:** Half Day (").append(draft.getHalfDaySession() != null ? draft.getHalfDaySession() : "FIRST_HALF").append(")\n");
        }
        if (draft.getRawReason() != null && !draft.getRawReason().isBlank()) {
            sb.append("• **Your Input:** *\"").append(draft.getRawReason()).append("\"*\n");
        }
        sb.append("• 📝 **Polished Corporate Justification:**\n")
                .append("  > *\"").append(draft.getRefinedReason()).append("\"*\n\n")
                .append("Shall I submit this request to your manager?");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(List.of("✅ Confirm & Apply", "✏️ Edit Reason", "📅 Change Dates", "Cancel"));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.CONFIRMATION);
    }

    private String intelligentlyRefineReason(LeaveType type, String rawReason, User user) {
        if (rawReason == null || rawReason.isBlank()) {
            return "Applying for scheduled " + (type != null ? type.getDisplayName() : "leave") + " due to personal commitments.";
        }

        String rawClean = rawReason.trim().replaceAll("(?i)^(because of|due to|reason:|for|my reason is)\\s*", "");

        // 1. Try GenAI if configured
        if (genAiClient.isConfigured()) {
            try {
                String prompt = "You are an HR corporate communications specialist at peopleFirst.\n" +
                        "Improve this raw leave reason into a single professional, polite, 1-sentence corporate leave justification for official company records and manager review.\n" +
                        "Leave Type: " + (type != null ? type.getDisplayName() : "Leave") + "\n" +
                        "Employee: " + user.getFullName() + "\n" +
                        "Raw input: \"" + rawClean + "\"\n" +
                        "Rules: Return ONLY the refined 1-sentence reason without quotes, explanations, or prefixes.";
                Optional<String> aiText = genAiClient.generateContent(buildSystemContext(user), prompt);
                if (aiText.isPresent() && !aiText.get().isBlank()) {
                    return aiText.get().trim().replaceAll("^\"|\"$", "");
                }
            } catch (Exception ignored) {}
        }

        // 2. Deterministic intelligent refiner
        String lower = rawClean.toLowerCase();
        if (lower.contains("fever") || lower.contains("temperature") || lower.contains("viral")) {
            return "Requesting medical leave due to a viral fever. I will rest to recover, consult a doctor if needed, and keep the team updated.";
        }
        if (lower.contains("headache") || lower.contains("migraine")) {
            return "Requesting sick leave due to an acute migraine and need to rest to recuperate.";
        }
        if (lower.contains("cold") || lower.contains("cough") || lower.contains("flu") || lower.contains("throat")) {
            return "Taking leave to recover from acute cold and flu symptoms.";
        }
        if (lower.contains("stomach") || lower.contains("food poison") || lower.contains("gastric") || lower.contains("vomiting") || lower.contains("nausea")) {
            return "Taking medical leave due to sudden gastrointestinal illness and need for medical care.";
        }
        if (lower.contains("doctor") || lower.contains("appointment") || lower.contains("clinic") || lower.contains("hospital") || lower.contains("consult")) {
            return "Attending a scheduled medical consultation and health assessment.";
        }
        if (lower.contains("emergency") || lower.contains("urgent")) {
            return "Taking urgent leave to attend to an unforeseen family situation and urgent caregiving responsibilities.";
        }
        if (lower.contains("wedding") || lower.contains("marriage") || lower.contains("reception")) {
            return "Attending a close family wedding celebration and attending to ceremonial family commitments.";
        }
        if (lower.contains("family function") || lower.contains("family event") || lower.contains("ceremony") || lower.contains("festival") || lower.contains("pooja")) {
            return "Attending an important family function and traditional gathering.";
        }
        if (lower.contains("travel") || lower.contains("trip") || lower.contains("vacation") || lower.contains("hometown") || lower.contains("native") || lower.contains("village")) {
            return "Taking scheduled leave for pre-planned travel to visit family in my hometown.";
        }
        if (lower.contains("tired") || lower.contains("burnout") || lower.contains("exhaust") || lower.contains("mental health") || lower.contains("stress") || lower.contains("recharge")) {
            return "Taking personal wellbeing time off to rest, recharge, and maintain optimal work-life balance.";
        }
        if (lower.contains("bank") || lower.contains("errand") || lower.contains("paperwork") || lower.contains("license") || lower.contains("passport")) {
            return "Attending to official administrative and banking errands requiring personal presence during business hours.";
        }
        if (lower.contains("home") || lower.contains("renovation") || lower.contains("plumb") || lower.contains("electric") || lower.contains("internet") || lower.contains("wifi")) {
            return "Taking leave to address unavoidable residential maintenance and utility repairs.";
        }
        if (lower.contains("personal") || lower.contains("work")) {
            return "Attending to urgent personal matters that require dedicated attention during regular working hours.";
        }

        return "Requesting " + (type != null ? type.getDisplayName() : "leave") + " to attend to " + rawClean + ", ensuring urgent project handovers are addressed.";
    }

    private AgentChatResponseDto promptForLeaveType(User user) {
        String roleNote = user.isContractor()
                ? "As a contractor partner, you are eligible for **Sick Leave**, **Paid Leave**, or **Loss of Pay (LOP)**."
                : "You can apply for **Casual Leave**, **Sick Leave**, **Paid Leave**, **Work From Home (WFH)**, **Loss of Pay (LOP)**, or **Volunteering Leave**.";

        String reply = "I would be glad to help you submit a leave request!\n\n" + roleNote + "\n\nWhich type of leave would you like to apply for?";
        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(getEligibleLeaveTypeChips(user));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.LEAVE_TYPE);
    }

    private AgentChatResponseDto promptForHalfDaySession(PendingLeaveDraft draft, User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Would you like to take the **Morning** or **Afternoon** session off?\n\n")
                .append("• 🌅 **First Half (Morning)**\n")
                .append("• 🌇 **Second Half (Afternoon)**\n\n")
                .append("Please select or type your preferred session:");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(List.of("🌅 First Half (Morning)", "🌇 Second Half (Afternoon)", "Cancel"));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.HALF_DAY_SESSION);
    }

    private List<String> getEligibleLeaveTypeChips(User user) {
        if (user.isContractor()) {
            return List.of("Sick Leave", "Paid Leave", "Loss of Pay (LOP)", "Cancel");
        } else {
            return List.of("Casual Leave", "Sick Leave", "Paid Leave", "Work From Home (WFH)", "Loss of Pay (LOP)", "Volunteering Leave", "Cancel");
        }
    }

    private AgentChatResponseDto promptForDates(LeaveType leaveType, User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Got it! You're applying for **").append(leaveType.getDisplayName()).append("**.\n\n");

        if (leaveType == LeaveType.PAID) {
            LocalDate earliestValid = LocalDate.now().plusDays(3);
            sb.append("⚠️ *Notice rule:* Paid Leave requires more than 2 days advance notice (earliest valid date is **")
                    .append(IntentParser.formatDate(earliestValid)).append("**)..\n\n");
        } else if (leaveType == LeaveType.SICK) {
            sb.append("💡 *Tip:* Sick Leave exceeding 2 consecutive days requires a medical certificate.\n\n");
        } else if (leaveType == LeaveType.CASUAL || leaveType == LeaveType.WFH) {
            sb.append("💡 *Tip:* Casual / WFH must be submitted before the end of the current week.\n\n");
        }

        sb.append("When would you like your leave to begin? (e.g., 'Tomorrow', '10th Sep', 'next Monday', 'for 3 days', or any date like 10-09-2026 or 2026-09-10).");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.APPLY_LEAVE.name());
        response.setQuickReplies(getDateRecommendationChips(leaveType));
        return updateContextAndReturn(response, user, AgentIntent.APPLY_LEAVE, ConversationContext.PromptType.DATE);
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
                    .append("• **Dates:** ").append(IntentParser.formatDate(created.getStartDate())).append(" to ").append(IntentParser.formatDate(created.getEndDate()))
                    .append(" (").append(created.getTotalDays() == 0.5 ? "0.5 day" : (created.getTotalDays() + " day" + (created.getTotalDays() > 1 ? "s" : ""))).append(")\n");
            if (created.isHalfDay()) {
                String sessionName = "SECOND_HALF".equalsIgnoreCase(created.getHalfDaySession()) ? "Second Half (Afternoon)" : "First Half (Morning)";
                sb.append("• **Session:** ").append(sessionName).append("\n");
            }
            sb.append("• **Reason:** ").append(created.getReason()).append("\n")
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

            if (leaveType == LeaveType.VOLUNTEERING && created.getId() != null) {
                volunteeringSignups.put(user.getId(), new PendingVolunteeringSignup(created.getId()));
                sb.append("\n\n🌱 **CSR chapters you can join:** ")
                        .append(String.join(", ", CSR_GROUPS))
                        .append(".\nWant me to enroll you in one — and feature you on the company intranet banner? Reply with the group name (add \"and feature me\" for the banner).");
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

            response.setQuickReplies(leaveType == LeaveType.VOLUNTEERING
                    ? csrQuickReplies()
                    : getPostActionQuickReplies(user));
            return response;

        } catch (PolicyViolationException pve) {
            userDrafts.remove(user.getId());
            String msg = pve.getMessage();
            if (msg != null && (msg.contains("backdate") || msg.contains("back date") ||
                    msg.contains("retroactiv") || msg.contains("after the leave date has passed"))) {
                AgentChatResponseDto response = new AgentChatResponseDto(
                        "You can't apply leave for backdate.",
                        AgentIntent.APPLY_LEAVE.name()
                );
                response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
                return response;
            }

            if (msg != null && (msg.toLowerCase().contains("overlap") || msg.toLowerCase().contains("already have an active") ||
                    msg.toLowerCase().contains("already applied") || msg.toLowerCase().contains("active leave request"))) {
                userDrafts.remove(user.getId());
                AgentChatResponseDto response = new AgentChatResponseDto(
                        "❌ " + msg + "\n\nWould you like to choose different dates, or edit/cancel your existing leave?",
                        AgentIntent.APPLY_LEAVE.name()
                );
                response.setQuickReplies(List.of("Tomorrow", "Next Week", "Edit my leave", "Cancel my leave", "View my leaves"));
                return response;
            }

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
        } catch (Exception e) {
            userDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto();
            response.setIntent(AgentIntent.APPLY_LEAVE.name());
            response.setReply("❌ Leave request could not be processed: " + e.getMessage() +
                    "\n\nIf you need assistance, I can help you raise a support ticket to our backend support team.");
            response.setQuickReplies(List.of("Raise a support ticket", "Check my balances", "Company leave policies"));
            return response;
        }
    }

    private List<String> getPostActionQuickReplies(User user) {
        return List.of("Check my balances", "View my leaves", "Company leave policies", "Explore amenities");
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
                    approve ? AgentIntent.APPROVE_LEAVE.name() : AgentIntent.REJECT_LEAVE.name());
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
        AgentChatResponseDto response = new AgentChatResponseDto(reply,
                approve ? AgentIntent.APPROVE_LEAVE.name() : AgentIntent.REJECT_LEAVE.name());
        response.setActionExecuted(true);
        response.setActionName(approve ? "APPROVE_LEAVE" : "REJECT_LEAVE");
        response.setActionData(result);
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private AgentChatResponseDto handleListPendingApprovals(User user) {
        List<LeaveResponseDto> pendings = approvalService.getPendingApprovals(user);
        AgentChatResponseDto response = new AgentChatResponseDto(
                pendings.isEmpty()
                        ? "There are no leave requests awaiting your approval."
                        : "Found " + pendings.size() + " leave request(s) awaiting your approval.",
                AgentIntent.VIEW_PENDING_APPROVALS.name());
        response.setActionExecuted(true);
        response.setActionName("VIEW_PENDING_APPROVALS");
        response.setActionData(pendings);
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private List<Map<String, Object>> toCompactApprovalList(Object actionData) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (actionData instanceof List) {
            for (Object item : (List<?>) actionData) {
                if (item instanceof LeaveResponseDto) {
                    LeaveResponseDto dto = (LeaveResponseDto) item;
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", dto.getId() != null ? dto.getId().toString() : null);
                    entry.put("employeeName", dto.getEmployeeName());
                    entry.put("leaveType", dto.getLeaveType() != null ? dto.getLeaveType().name() : null);
                    entry.put("startDate", dto.getStartDate() != null ? dto.getStartDate().toString() : null);
                    entry.put("endDate", dto.getEndDate() != null ? dto.getEndDate().toString() : null);
                    entry.put("totalDays", dto.getTotalDays());
                    entry.put("status", dto.getStatus() != null ? dto.getStatus().name() : null);
                    compact.add(entry);
                }
            }
        }
        return compact;
    }

    private AgentChatResponseDto continueVolunteeringSignup(String message, PendingVolunteeringSignup signup, User user) {
        String lower = message.toLowerCase().trim();
        if (lower.equals("no thanks") || lower.equals("no") || lower.equals("cancel") ||
                lower.equals("stop") || lower.equals("discard") || lower.equals("never mind") ||
                lower.equals("nevermind")) {
            volunteeringSignups.remove(user.getId());
            AgentChatResponseDto declined = new AgentChatResponseDto(
                    "No problem — enjoy your volunteering leave!", AgentIntent.APPLY_LEAVE.name());
            declined.setQuickReplies(getPostActionQuickReplies(user));
            return declined;
        }

        String matched = matchCsrGroup(message);
        if (matched != null) {
            boolean banner = lower.contains("feature");
            volunteeringService.enroll(user.getId(), matched, signup.getLeaveRequestId(), banner);
            volunteeringSignups.remove(user.getId());
            String reply = banner
                    ? "You're enrolled in **" + matched + "**! You'll be featured on the intranet banner. Reach out to CSR at " + CSR_ENROLL_URL + " for onboarding."
                    : "You're enrolled in **" + matched + "**!";
            AgentChatResponseDto enrolled = new AgentChatResponseDto(reply, AgentIntent.APPLY_LEAVE.name());
            enrolled.setActionExecuted(true);
            enrolled.setActionName("VOLUNTEER_ENROLL");
            enrolled.setQuickReplies(getPostActionQuickReplies(user));
            return enrolled;
        }

        volunteeringSignups.put(user.getId(), signup);
        AgentChatResponseDto reprompt = new AgentChatResponseDto(
                "Which CSR chapter would you like to join? Reply with the group name (add \"and feature me\" for the intranet banner).",
                AgentIntent.APPLY_LEAVE.name());
        reprompt.setQuickReplies(csrQuickReplies());
        return reprompt;
    }

    private String matchCsrGroup(String message) {
        if (message == null) {
            return null;
        }
        String lower = message.toLowerCase();
        for (String group : CSR_GROUPS) {
            if (lower.contains(group.toLowerCase())) {
                return group;
            }
        }
        return null;
    }

    private List<String> csrQuickReplies() {
        List<String> replies = new ArrayList<>(CSR_GROUPS);
        replies.add("No thanks");
        return replies;
    }

    private AgentChatResponseDto handleCancelLeave(String message, User user) {
        String lower = message.toLowerCase().trim();
        List<LeaveResponseDto> leaves = leaveService.getLeavesForUser(user.getId());

        // Find active leaves that have not already ended in the past
        List<LeaveResponseDto> cancellable = leaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.PENDING || l.getStatus() == LeaveStatus.APPROVED) &&
                        (l.getEndDate() == null || !l.getEndDate().isBefore(LocalDate.now())))
                .sorted(Comparator.comparing(LeaveResponseDto::getStartDate))
                .collect(Collectors.toList());

        if (cancellable.isEmpty()) {
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You do not have any active (pending or approved) leave requests eligible for cancellation.\n\n" +
                            "• For leaves whose dates have already passed, please raise a support ticket if retroactive adjustment is needed.",
                    AgentIntent.CANCEL_LEAVE.name()
            );
            response.setQuickReplies(List.of("Check my balances", "View my leaves", "Apply for leave", "Raise a support ticket"));
            return response;
        }

        // 1. If UUID is specified in the message
        UUID targetId = intentParser.extractUuid(message);
        LeaveResponseDto target = null;
        if (targetId != null) {
            target = cancellable.stream().filter(l -> l.getId().equals(targetId)).findFirst().orElse(null);
        }

        // 2. If leave type is specified (e.g. "cancel my sick leave")
        if (target == null) {
            LeaveType specifiedType = intentParser.extractLeaveType(message);
            if (specifiedType != null) {
                target = cancellable.stream().filter(l -> l.getLeaveType() == specifiedType).findFirst().orElse(null);
            }
        }

        // 3. If multiple leaves exist and neither UUID nor specific type matched:
        if (target == null && cancellable.size() > 1 && !lower.contains("yes") && !lower.contains("confirm")) {
            StringBuilder sb = new StringBuilder("You have **").append(cancellable.size())
                    .append("** active leaves. Which one would you like to cancel?\n\n");
            List<String> chips = new ArrayList<>();
            for (LeaveResponseDto l : cancellable) {
                String id8 = l.getId().toString().substring(0, 8);
                sb.append("• **").append(l.getLeaveTypeDisplayName()).append("** (`").append(id8).append("`): ")
                        .append(l.getStartDate()).append(" to ").append(l.getEndDate())
                        .append(" [").append(l.getStatus()).append("]\n");
                chips.add("Cancel " + l.getLeaveTypeDisplayName() + " (" + l.getStartDate() + ")");
            }
            chips.add("Don't cancel");
            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.CANCEL_LEAVE.name());
            response.setQuickReplies(chips);
            return response;
        }

        // 4. Default to the earliest upcoming active leave
        if (target == null) {
            target = cancellable.get(0);
        }

        try {
            LeaveResponseDto cancelled = leaveService.cancelLeave(target.getId(), user, "Cancelled via Kura AI Agent");

            StringBuilder sb = new StringBuilder();
            sb.append("✅ **Leave Request Cancelled Successfully!**\n\n")
                    .append("• **Type:** ").append(cancelled.getLeaveTypeDisplayName()).append("\n")
                    .append("• **Dates:** ").append(cancelled.getStartDate()).append(" to ").append(cancelled.getEndDate())
                    .append(" (").append(cancelled.getTotalDays()).append(" day").append(cancelled.getTotalDays() > 1 ? "s" : "").append(")\n")
                    .append("• **Previous Status:** ").append(target.getStatus()).append(" ➔ **CANCELLED**\n\n")
                    .append("Your leave balance has been restored.");

            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.CANCEL_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("CANCEL_LEAVE");
            response.setActionData(cancelled);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (Exception e) {
            AgentChatResponseDto response = new AgentChatResponseDto("❌ Cancellation could not be processed: " + e.getMessage(), AgentIntent.CANCEL_LEAVE.name());
            response.setQuickReplies(List.of("View my leaves", "Raise a support ticket"));
            return response;
        }
    }

    private AgentChatResponseDto handleEditLeave(String message, User user) {
        String lower = message.toLowerCase().trim();
        List<LeaveResponseDto> userLeaves = leaveService.getLeavesForUser(user.getId());
        List<LeaveResponseDto> editable = userLeaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.PENDING || l.getStatus() == LeaveStatus.RETURNED) &&
                        (l.getEndDate() == null || !l.getEndDate().isBefore(LocalDate.now())))
                .sorted(Comparator.comparing(LeaveResponseDto::getStartDate))
                .collect(Collectors.toList());

        if (editable.isEmpty()) {
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You do not have any upcoming **PENDING** or **RETURNED** leave requests eligible for editing.\n\n" +
                            "• Approved leaves cannot be edited directly; you can cancel them and re-apply, or raise a support ticket.",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(List.of("Apply for leave", "View my leaves", "Cancel leave", "Raise a support ticket"));
            return response;
        }

        // Identify target leave
        UUID reqUuid = intentParser.extractUuid(message);
        LeaveResponseDto target = null;
        if (reqUuid != null) {
            target = editable.stream().filter(l -> l.getId().equals(reqUuid)).findFirst().orElse(null);
        }
        if (target == null) {
            LeaveType specifiedType = intentParser.extractLeaveType(message);
            if (specifiedType != null) {
                target = editable.stream().filter(l -> l.getLeaveType() == specifiedType).findFirst().orElse(null);
            }
        }
        if (target == null) {
            target = editable.get(0);
        }

        PendingEditDraft editDraft = new PendingEditDraft();
        editDraft.setLeaveId(target.getId());
        editDraft.setLeaveType(target.getLeaveType());
        editDraft.setStartDate(target.getStartDate());
        editDraft.setEndDate(target.getEndDate());
        editDraft.setHalfDay(target.isHalfDay());
        editDraft.setHalfDaySession(target.getHalfDaySession());
        editDraft.setRawReason(target.getReason());
        editDraft.setRefinedReason(target.getReason());

        // Check if message directly provided new dates or new reason
        LocalDate[] dates = intentParser.extractDates(message);
        String extractedReason = intentParser.extractRawReason(message);

        // Check if pattern is: "change/move/reschedule ... from <oldDate> to <newDate>"
        Matcher moveMatcher = Pattern.compile("(?i)(?:change|move|reschedule|shift|postpone).*?\\bfrom\\s+(\\S+)\\s+to\\s+(\\S+)").matcher(message);
        if (moveMatcher.find()) {
            LocalDate[] dFrom = intentParser.extractDates(moveMatcher.group(1));
            LocalDate[] dTo = intentParser.extractDates(moveMatcher.group(2));
            if (dTo[0] != null) {
                if (dFrom[0] != null && target.getStartDate().equals(dFrom[0])) {
                    long originalDuration = ChronoUnit.DAYS.between(target.getStartDate(), target.getEndDate());
                    dates[0] = dTo[0];
                    dates[1] = dTo[0].plusDays(originalDuration);
                } else if (dFrom[0] != null) {
                    dates[0] = dFrom[0];
                    dates[1] = dTo[0];
                }
            }
        }

        if (extractedReason == null && (lower.contains("same reason") || lower.equals("same") || lower.contains("as before") || lower.contains("keep same"))) {
            extractedReason = target.getReason();
        }

        // Check for backdate / today
        if ((dates[0] != null && !dates[0].isAfter(LocalDate.now())) || lower.contains("back date") || lower.contains("backdate") || lower.contains("past date") || lower.contains("today")) {
            userEditDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You can't apply leave for backdate.",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
            return response;
        }

        boolean hasNewDates = (dates[0] != null);
        boolean hasNewReason = (extractedReason != null);

        if (hasNewDates) {
            editDraft.setStartDate(dates[0]);
            editDraft.setEndDate(dates[1] != null ? dates[1] : dates[0]);
        }
        if (hasNewReason) {
            editDraft.setRawReason(extractedReason);
            editDraft.setRefinedReason(intelligentlyRefineReason(editDraft.getLeaveType(), extractedReason, user));
        }

        // If neither new dates nor new reason was supplied, ask user interactively!
        if (!hasNewDates && !hasNewReason) {
            userEditDrafts.put(user.getId(), editDraft);
            String id8 = target.getId().toString().substring(0, 8);
            StringBuilder sb = new StringBuilder();
            sb.append("✏️ **Editing Leave Request** (`").append(id8).append("` - ")
                    .append(target.getLeaveTypeDisplayName()).append(" from ").append(target.getStartDate())
                    .append(" to ").append(target.getEndDate()).append("):\n\n")
                    .append("What would you like to update?\n")
                    .append("• **New Dates**: Provide dates like 'Tomorrow', 'Next week', or '2026-09-10 to 2026-09-12'\n")
                    .append("• **New Reason**: Tell me your reason and I will refine it professionally");

            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.EDIT_LEAVE.name());
            response.setQuickReplies(List.of("Tomorrow", "Next 2 Days", "Next Week", "Update Reason", "Cancel"));
            return response;
        }

        // If new dates or reason were supplied in the same turn, execute update!
        return applyEditChanges(editDraft, user);
    }

    private AgentChatResponseDto continueEditDraft(String message, PendingEditDraft editDraft, User user) {
        String lower = message.toLowerCase().trim();

        if (lower.equals("cancel") || lower.contains("cancel edit") || lower.contains("nevermind")) {
            userEditDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "Leave update cancelled. Your request remains unchanged.",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        }

        if (editDraft.isAwaitingReason()) {
            String r = (lower.contains("same reason") || lower.equals("same") || lower.contains("as before") || lower.contains("skip"))
                    ? editDraft.getRawReason()
                    : message.trim();
            editDraft.setRawReason(r);
            editDraft.setRefinedReason(intelligentlyRefineReason(editDraft.getLeaveType(), r, user));
            editDraft.setAwaitingReason(false);
            return applyEditChanges(editDraft, user);
        }

        if (lower.contains("update reason") || lower.contains("change reason")) {
            editDraft.setAwaitingReason(true);
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "What is the updated reason for your leave? I will polish it into a formal corporate justification.",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(getReasonChips(editDraft.getLeaveType()));
            return response;
        }

        LocalDate[] dates = intentParser.extractDates(message);
        if ((dates[0] != null && dates[0].isBefore(LocalDate.now())) || lower.contains("back date") || lower.contains("backdate") || lower.contains("past date")) {
            userEditDrafts.remove(user.getId());
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You can't apply leave for backdate.",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
            return response;
        }

        if (dates[0] != null) {
            editDraft.setStartDate(dates[0]);
            editDraft.setEndDate(dates[1] != null ? dates[1] : dates[0]);
        }

        String rawReason = intentParser.extractRawReason(message);
        if (rawReason != null) {
            editDraft.setRawReason(rawReason);
            editDraft.setRefinedReason(intelligentlyRefineReason(editDraft.getLeaveType(), rawReason, user));
        }

        LeaveType newType = intentParser.extractLeaveType(message);
        if (newType != null) {
            editDraft.setLeaveType(newType);
        }

        return applyEditChanges(editDraft, user);
    }

    private AgentChatResponseDto applyEditChanges(PendingEditDraft editDraft, User user) {
        UpdateLeaveRequestDto updateDto = new UpdateLeaveRequestDto();
        updateDto.setLeaveType(editDraft.getLeaveType());
        updateDto.setStartDate(editDraft.getStartDate());
        updateDto.setEndDate(editDraft.getEndDate());
        updateDto.setHalfDay(editDraft.isHalfDay());
        updateDto.setHalfDaySession(editDraft.getHalfDaySession());
        updateDto.setReason(editDraft.getRefinedReason() != null ? editDraft.getRefinedReason() :
                (editDraft.getRawReason() != null ? editDraft.getRawReason() : "Updated via Kura AI Agent"));

        long dur = ChronoUnit.DAYS.between(editDraft.getStartDate(), editDraft.getEndDate()) + 1;
        if (editDraft.getLeaveType() == LeaveType.SICK && dur > 2) {
            updateDto.setDocumentAttached(true);
            updateDto.setDocumentUrl("https://documents.peoplefirst.internal/agent-upload-" + UUID.randomUUID() + ".pdf");
        }

        try {
            LeaveResponseDto updated = leaveService.editLeave(editDraft.getLeaveId(), updateDto, user);
            userEditDrafts.remove(user.getId());

            StringBuilder sb = new StringBuilder();
            sb.append("✏️ **Leave Request Updated Successfully!**\n\n")
                    .append("• **Type:** ").append(updated.getLeaveTypeDisplayName()).append("\n")
                    .append("• **New Dates:** ").append(updated.getStartDate()).append(" to ").append(updated.getEndDate())
                    .append(" (").append(updated.getTotalDays()).append(" day").append(updated.getTotalDays() > 1 ? "s" : "").append(")\n")
                    .append("• **Updated Reason:** ").append(updated.getReason()).append("\n")
                    .append("• **Status:** ").append(updated.getStatus()).append("\n\n")
                    .append("Your manager will review the updated request.");

            AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.EDIT_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("EDIT_LEAVE");
            response.setActionData(updated);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (PolicyViolationException pve) {
            String msg = pve.getMessage();
            if (msg != null && (msg.contains("backdate") || msg.contains("back date") ||
                    msg.contains("retroactiv") || msg.contains("after the leave date has passed"))) {
                userEditDrafts.remove(user.getId());
                AgentChatResponseDto response = new AgentChatResponseDto(
                        "You can't apply leave for backdate.",
                        AgentIntent.EDIT_LEAVE.name()
                );
                response.setQuickReplies(List.of("Tomorrow", "Next Week", "Check my balances", "Raise a support ticket"));
                return response;
            }
            if (msg != null && (msg.toLowerCase().contains("overlap") || msg.toLowerCase().contains("already have an active") ||
                    msg.toLowerCase().contains("already applied") || msg.toLowerCase().contains("active leave request"))) {
                userEditDrafts.remove(user.getId());
                AgentChatResponseDto response = new AgentChatResponseDto(
                        "❌ " + msg + "\n\nPlease choose non-overlapping dates.",
                        AgentIntent.EDIT_LEAVE.name()
                );
                response.setQuickReplies(List.of("Tomorrow", "Next Week", "Cancel"));
                return response;
            }
            AgentChatResponseDto response = new AgentChatResponseDto("❌ **Update Policy Notice:** " + msg, AgentIntent.EDIT_LEAVE.name());
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Raise a support ticket"));
            return response;
        } catch (Exception e) {
            userEditDrafts.remove(user.getId());
            return new AgentChatResponseDto("❌ Update failed: " + e.getMessage(), AgentIntent.EDIT_LEAVE.name());
        }
    }

    private AgentChatResponseDto handleViewPendingApprovals(User user) {
        if (user.getRole() == Role.EMPLOYEE && !user.isContractor()) {
            return new AgentChatResponseDto(
                    "Leave approval access is reserved for Managers, Supervisors, and Administrators.",
                    AgentIntent.VIEW_PENDING_APPROVALS.name()
            );
        }

        List<LeaveResponseDto> pending = approvalService.getPendingApprovals(user);

        if (pending.isEmpty()) {
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You do not have any pending leave requests awaiting approval at this time. 🎉",
                    AgentIntent.VIEW_PENDING_APPROVALS.name()
            );
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        }

        StringBuilder sb = new StringBuilder("📋 **Pending Leave Requests Awaiting Your Review (")
                .append(pending.size()).append(")**:\n\n");

        List<String> chips = new ArrayList<>();
        int count = 0;
        for (LeaveResponseDto l : pending) {
            if (count++ < 5) {
                String id8 = l.getId().toString().substring(0, 8);
                sb.append("• **").append(l.getEmployeeName()).append("** (`").append(id8).append("`)\n")
                        .append("   - ").append(l.getLeaveTypeDisplayName()).append(": ").append(l.getStartDate()).append(" to ").append(l.getEndDate())
                        .append(" (").append(l.getTotalDays()).append(" day").append(l.getTotalDays() > 1 ? "s" : "").append(")\n")
                        .append("   - Reason: _").append(l.getReason() != null ? l.getReason() : "No reason provided").append("_\n\n");
            }
            if (chips.size() < 4) {
                String id8 = l.getId().toString().substring(0, 8);
                chips.add("Approve " + id8);
                chips.add("Reject " + id8);
            }
        }
        chips.add("Team balances");

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.VIEW_PENDING_APPROVALS.name());
        response.setActionExecuted(true);
        response.setActionName("VIEW_PENDING_APPROVALS");
        response.setActionData(pending);
        response.setQuickReplies(chips);
        return response;
    }

    private AgentChatResponseDto handleApproveLeave(String message, User user) {
        if (user.getRole() == Role.EMPLOYEE && !user.isContractor()) {
            return new AgentChatResponseDto("You do not have permission to approve leave requests.", AgentIntent.APPROVE_LEAVE.name());
        }

        LeaveResponseDto target = resolveTargetLeave(message, user);
        if (target == null) {
            return new AgentChatResponseDto(
                    "Please specify which leave request you would like to approve (e.g. 'Approve leave <id>' or check 'Pending approvals').",
                    AgentIntent.APPROVE_LEAVE.name()
            );
        }

        ApprovalActionDto dto = new ApprovalActionDto("Approved via Kura AI Agent by " + user.getFullName());
        try {
            LeaveResponseDto approved = approvalService.approveLeave(target.getId(), dto, user);
            String reply = "✅ Leave request for **" + approved.getEmployeeName() + "** (" +
                    approved.getLeaveTypeDisplayName() + " from " + approved.getStartDate() + " to " +
                    approved.getEndDate() + ") has been **APPROVED**.\n\n" +
                    "Leave balance has been committed and an audit log recorded.";

            AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.APPROVE_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("APPROVE_LEAVE");
            response.setActionData(approved);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (Exception e) {
            return new AgentChatResponseDto("❌ Approval failed: " + e.getMessage(), AgentIntent.APPROVE_LEAVE.name());
        }
    }

    private AgentChatResponseDto handleRejectLeave(String message, User user) {
        if (user.getRole() == Role.EMPLOYEE && !user.isContractor()) {
            return new AgentChatResponseDto("You do not have permission to reject leave requests.", AgentIntent.REJECT_LEAVE.name());
        }

        LeaveResponseDto target = resolveTargetLeave(message, user);
        if (target == null) {
            return new AgentChatResponseDto(
                    "Please specify which leave request to reject (e.g. 'Reject leave <id>').",
                    AgentIntent.REJECT_LEAVE.name()
            );
        }

        String comment = extractActionComment(message, "Rejected via Kura AI Agent");
        ApprovalActionDto dto = new ApprovalActionDto(comment);

        try {
            LeaveResponseDto rejected = approvalService.rejectLeave(target.getId(), dto, user);
            String reply = "❌ Leave request for **" + rejected.getEmployeeName() + "** (" +
                    rejected.getLeaveTypeDisplayName() + ") has been **REJECTED**.\n" +
                    "• Reason: _" + comment + "_\n" +
                    "• The reserved quota has been released back to their available balance.";

            AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.REJECT_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("REJECT_LEAVE");
            response.setActionData(rejected);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (Exception e) {
            return new AgentChatResponseDto("❌ Rejection failed: " + e.getMessage(), AgentIntent.REJECT_LEAVE.name());
        }
    }

    private AgentChatResponseDto handleSendBackLeave(String message, User user) {
        if (user.getRole() == Role.EMPLOYEE && !user.isContractor()) {
            return new AgentChatResponseDto("You do not have permission to send back leave requests.", AgentIntent.SEND_BACK_LEAVE.name());
        }

        LeaveResponseDto target = resolveTargetLeave(message, user);
        if (target == null) {
            return new AgentChatResponseDto(
                    "Please specify which leave request to send back (e.g. 'Send back leave <id>').",
                    AgentIntent.SEND_BACK_LEAVE.name()
            );
        }

        String comment = extractActionComment(message, "Sent back for modification via Kura AI Agent");
        ApprovalActionDto dto = new ApprovalActionDto(comment);

        try {
            LeaveResponseDto returned = approvalService.sendBackLeave(target.getId(), dto, user);
            String reply = "↩️ Leave request for **" + returned.getEmployeeName() + "** has been **SENT BACK** for revision.\n" +
                    "• Note for employee: _" + comment + "_\n" +
                    "• The employee can now edit their dates or upload documents and resubmit.";

            AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.SEND_BACK_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("SEND_BACK_LEAVE");
            response.setActionData(returned);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (Exception e) {
            return new AgentChatResponseDto("❌ Send-back failed: " + e.getMessage(), AgentIntent.SEND_BACK_LEAVE.name());
        }
    }

    private AgentChatResponseDto handleCheckTeamBalances(String message, User user) {
        if (user.getRole() == Role.EMPLOYEE && !user.isContractor()) {
            return new AgentChatResponseDto("Team balance oversight is accessible to Managers, Supervisors, and Administrators.", AgentIntent.CHECK_TEAM_BALANCES.name());
        }

        int year = LocalDate.now().getYear();
        List<User> reports = userService.getDirectReportEntities(user.getId());
        if (reports.isEmpty() && user.getRole() == Role.ADMIN) {
            reports = userService.getAllUserEntities().stream()
                    .filter(u -> u.getRole() == Role.EMPLOYEE)
                    .limit(5)
                    .collect(Collectors.toList());
        }

        if (reports.isEmpty()) {
            return new AgentChatResponseDto("You do not currently have any direct reportees assigned.", AgentIntent.CHECK_TEAM_BALANCES.name());
        }

        StringBuilder sb = new StringBuilder("👥 **Direct Reportees Leave Balances (")
                .append(year).append(")**:\n\n");

        for (User r : reports) {
            leaveBalanceService.initializeUserBalancesIfAbsent(r, year);
            List<LeaveBalance> balances = leaveBalanceService.getUserBalances(r.getId(), year);

            sb.append("• **").append(r.getFullName()).append("** (").append(r.getDepartment()).append("):\n");
            for (LeaveBalance b : balances) {
                sb.append("   - ").append(b.getLeaveType().getDisplayName()).append(": ")
                        .append(b.getRemainingDays()).append(" remaining (")
                        .append(b.getUsedDays()).append(" used, ")
                        .append(b.getPendingDays()).append(" pending)\n");
            }
            sb.append("\n");
        }

        AgentChatResponseDto response = new AgentChatResponseDto(sb.toString(), AgentIntent.CHECK_TEAM_BALANCES.name());
        response.setActionExecuted(true);
        response.setActionName("CHECK_TEAM_BALANCES");
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private AgentChatResponseDto handleRaiseTicket(String message, User user) {
        String cleanSubject;
        String lower = message.toLowerCase();
        if (lower.contains("cutoff")) {
            cleanSubject = "Submission after cutoff exception";
        } else if (lower.contains("error") || lower.contains("technical")) {
            cleanSubject = "Technical issue during leave application";
        } else if (lower.contains("retro") || lower.contains("correction") || lower.contains("past")) {
            cleanSubject = "Post-date retrospective leave adjustment";
        } else {
            cleanSubject = "Leave policy exception / assistance";
        }

        CreateTicketRequestDto ticketDto = new CreateTicketRequestDto(
                "POLICY_EXCEPTION",
                cleanSubject,
                message,
                null
        );

        TicketResponseDto created = ticketService.createTicket(ticketDto, user);

        String reply = "🎫 **Support Ticket Created Successfully!**\n\n" +
                "• **Ticket Ref:** `" + created.getTicketNumber() + "`\n" +
                "• **Subject:** " + created.getSubject() + "\n" +
                "• **Status:** " + created.getStatus() + "\n\n" +
                "Our HR Operations & Policy Exceptions desk has received your ticket and will assist you.";

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.RAISE_TICKET.name());
        response.setActionExecuted(true);
        response.setActionName("RAISE_TICKET");
        response.setActionData(created);
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private AgentChatResponseDto handleAdminDirectEdit(String message, User user) {
        if (user.getRole() != Role.ADMIN) {
            return new AgentChatResponseDto("Direct database edits are restricted strictly to Administrators.", AgentIntent.ADMIN_DIRECT_EDIT.name());
        }

        UUID leaveId = intentParser.extractUuid(message);
        if (leaveId == null) {
            List<LeaveResponseDto> all = leaveService.getAllLeavesOrgWide();
            if (!all.isEmpty()) {
                leaveId = all.get(0).getId();
            } else {
                return new AgentChatResponseDto("Please provide the leave UUID to update directly (e.g. 'direct edit <UUID> to APPROVED').", AgentIntent.ADMIN_DIRECT_EDIT.name());
            }
        }

        String lower = message.toLowerCase();
        LeaveStatus targetStatus = LeaveStatus.APPROVED;
        if (lower.contains("reject")) targetStatus = LeaveStatus.REJECTED;
        else if (lower.contains("cancel")) targetStatus = LeaveStatus.CANCELLED;
        else if (lower.contains("pending")) targetStatus = LeaveStatus.PENDING;

        AdminDirectEditDto dto = new AdminDirectEditDto();
        dto.setStatus(targetStatus);
        dto.setAuditComment("Direct database status override performed via Kura AI Agent by " + user.getFullName());

        try {
            LeaveResponseDto updated = leaveService.adminDirectEdit(leaveId, dto, user);
            String reply = "🛠️ **Admin Direct-DB-Edit Completed!**\n\n" +
                    "• **Leave ID:** `" + updated.getId() + "`\n" +
                    "• **Employee:** " + updated.getEmployeeName() + "\n" +
                    "• **New Status:** **" + updated.getStatus() + "**\n" +
                    "• **Audit Trail:** Distinctly audited with tag `ADMIN_DIRECT_EDIT` (`adminDirectEdit = true`).";

            AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.ADMIN_DIRECT_EDIT.name());
            response.setActionExecuted(true);
            response.setActionName("ADMIN_DIRECT_EDIT");
            response.setActionData(updated);
            response.setQuickReplies(List.of("Pending approvals", "Org-wide leaves", "Check my balances"));
            return response;
        } catch (Exception e) {
            return new AgentChatResponseDto("❌ Admin direct-DB-edit failed: " + e.getMessage(), AgentIntent.ADMIN_DIRECT_EDIT.name());
        }
    }

    private LeaveResponseDto resolveTargetLeave(String message, User user) {
        UUID uuid = intentParser.extractUuid(message);
        if (uuid != null) {
            try {
                return leaveService.getLeaveById(uuid);
            } catch (Exception ignored) {}
        }
        // Check 8-char hex prefix
        Matcher m8 = Pattern.compile("([a-f0-9]{8})", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m8.find()) {
            String prefix = m8.group(1).toLowerCase();
            List<LeaveResponseDto> all = approvalService.getPendingApprovals(user);
            for (LeaveResponseDto l : all) {
                if (l.getId().toString().toLowerCase().startsWith(prefix)) {
                    return l;
                }
            }
        }
        // Fallback: earliest pending request
        List<LeaveResponseDto> pending = approvalService.getPendingApprovals(user);
        return pending.isEmpty() ? null : pending.get(0);
    }

    private String extractActionComment(String message, String defaultComment) {
        String lower = message.toLowerCase();
        int idx = lower.indexOf("because");
        if (idx != -1 && idx + 7 < message.length()) {
            return message.substring(idx + 7).trim();
        }
        idx = lower.indexOf("reason:");
        if (idx != -1 && idx + 7 < message.length()) {
            return message.substring(idx + 7).trim();
        }
        idx = lower.indexOf("comment:");
        if (idx != -1 && idx + 8 < message.length()) {
            return message.substring(idx + 8).trim();
        }
        return defaultComment;
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
            String prompt = "The user expressed stress or pressure: \"" + message + "\". Provide a compassionate, supportive response acknowledging their pressure, and warmly advise taking a break to use our on-campus relaxation facilities (Zero-Gravity Massage Recliners in Bldg 1 4th floor for 30 mins, Recreational Lounge in Bldg 3 for carrom/snooker/chess/TT, or on-site Psychologist counseling in Bldg 2).";
            Optional<String> genAiReply = genAiClient.generateContent(systemContext, prompt);
            reply = genAiReply.orElseGet(() -> getDefaultStressReply());
        } else {
            reply = getDefaultStressReply();
        }

        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.STRESS_EXPRESSION.name());
        if (stressSuggestion != null) {
            response.setWellbeingSuggestions(List.of(stressSuggestion));
        }
        response.setQuickReplies(List.of("Apply for leave", "View partner resorts", "Healthcare discounts", "Campus amenities"));
        return response;
    }

    private String getDefaultStressReply() {
        return "I hear you, and please remember your wellbeing is our top priority. " +
                "Taking a pause during heavy sprints helps restore mental balance and clarity.\n\n" +
                "Here are immediate on-site relaxation amenities available to you:\n" +
                "• 🛋️ **Zero-Gravity Recliner Massage Chairs** (Building 1, 4th Floor Relaxation Pod — recharge with a 30-minute quiet session)\n" +
                "• 🎱 **Recreational Lounge** (Play games like table tennis, snooker, carrom, and chess in Building 3, 3rd Floor — open 24/7)\n" +
                "• 🧠 **Confidential Psychological Counseling** with our on-site psychologist (Building 2, 2nd Floor — Mon-Fri by appt)\n" +
                "• 🧘 **Guided Yoga & Zumba Studios** (Building 1, 5th Floor Terrace Hall)";
    }

    private AgentChatResponseDto handleWellbeingInquiry(String message, User user) {
        String lower = message.toLowerCase();
        StringBuilder sb = new StringBuilder();
        AgentChatResponseDto response = new AgentChatResponseDto();

        if (lower.contains("weekly") || lower.contains("status") || lower.contains("report") || lower.contains("health check")) {
            List<LeaveRequest> userLeaves = leaveService.getLeaveEntitiesForUser(user.getId());
            WeeklyWellbeingDto report = wellbeingService.getWeeklyWellbeingReport(user, userLeaves);

            sb.append("📊 **Weekly Wellbeing & Benefits Status for ").append(user.getFullName()).append("**\n\n")
                    .append("• **Overall Status:** ").append(report.getStatus().equals("HEALTHY") ? "🟢 Healthy & Balanced" : (report.getStatus().equals("RECHARGE_RECOMMENDED") ? "🟡 Recharge Recommended" : "🔵 Health Follow-up")).append("\n")
                    .append("• **Summary:** ").append(report.getSummary()).append("\n")
                    .append("• **Leaves Taken (Last 30 Days):** ").append(report.getLeavesTakenThisMonth()).append(" day(s)\n")
                    .append("• **Leaves Taken (Last 90 Days):** ").append(report.getLeavesTakenLastQuarter()).append(" day(s)\n\n");

            if (report.isRecentSickLeave() && report.getOpdClaimReminder() != null) {
                sb.append("🩺 **Health & Insurance Action:**\n")
                        .append("• ").append(report.getOpdClaimReminder()).append(" ([Insurance Claims Portal](").append(report.getInsuranceClaimsPortalUrl()).append("))\n\n");
            }

            if (report.isVacationNudge()) {
                sb.append("🌴 **Vacation Getaway Suggestion:**\n")
                        .append("• You haven't taken time off in the last quarter! Enjoy exclusive corporate discounts at partner resorts like The Tamara Coorg (25% off with code `PEOPLEFIRST-TAMARA25`) or Angsana Oasis Spa.\n\n");
            }

            sb.append("🏢 **On-Campus Wellbeing Programs Available Today:**\n")
                    .append("• 🛋️ **Zero-Gravity Massage Recliners** (Building 1, 4th Floor Pod — 8:00 AM to 8:00 PM)\n")
                    .append("• 🎱 **Recreational Lounge** (TT, Snooker, Chess, Carrom in Building 3, 3rd Floor — 24/7)\n")
                    .append("• 🏋️ **Gym & Zumba Studio** (Building 1, Basement Level & 5th Floor Terrace)\n")
                    .append("• 🧠 **Psychologist Consultation** (Building 2, 2nd Floor — Mon-Fri by appt)\n")
                    .append("• 🩺 **General Physician (GP)** (Building 2, 1st Floor Health Center — 9:00 AM to 5:00 PM)\n")
                    .append("• ⚖️ **Legal Advisor Hotline** (Over-call 24/7 corporate support)\n")
                    .append("• 🧘 **Yoga Studio** (Building 1, 5th Floor Terrace Hall — 7:00 AM & 5:30 PM)\n");

            response.setReply(sb.toString());
            response.setIntent(AgentIntent.WELLBEING_INQUIRY.name());
            response.setActionExecuted(true);
            response.setActionData(report);
            response.setQuickReplies(List.of("Apply for leave", "View partner hospitals", "View partner resorts", "Check leave balance"));
            return response;
        } else if (lower.contains("sick room") || lower.contains("rest room") || lower.contains("take rest")) {
            String sickRoomDetails;
            String loc = user.getBaseLocation() != null ? user.getBaseLocation().toLowerCase() : "";
            if (loc.contains("hyderabad")) {
                sickRoomDetails = "Building 3, 2nd Floor, Room 208 (First Aid & Rest Bay)";
            } else if (loc.contains("san jose")) {
                sickRoomDetails = "Building A, 1st Floor, Room 114 (Wellness Suite)";
            } else {
                sickRoomDetails = "Building 2, 3rd Floor, Room 304 (Medical Bay & Resting Room)";
            }
            sb.append("🏥 **On-Campus Sick Room & Rest Bay (").append(user.getBaseLocation()).append(")**:\n\n")
                    .append("• **Location:** ").append(sickRoomDetails).append("\n")
                    .append("• **Facilities:** Ergonomic resting recliners, medical first-aid kit, sanitized beds, blood pressure/temperature monitors, and direct line to the on-site physician.\n")
                    .append("• **Access:** Open to all employees feeling unwell during work hours before heading home.");
            response.setQuickReplies(List.of("Apply Sick Leave", "Consult Doctor", "Campus Amenities"));
        } else if (lower.contains("volunteer") || lower.contains("csr") || lower.contains("community group") || lower.contains("banner")) {
            sb.append("🤝 **Corporate CSR & Volunteering Initiatives**:\n\n")
                    .append("Thank you for your passion to give back! Here are the active employee volunteering chapters you can join under the **peopleFirst company banner**:\n\n")
                    .append("1. 🌱 **Green Earth Afforestation Drive** — Urban tree plantation & lake rejuvenation.\n")
                    .append("2. 💻 **Code & Tech Literacy for Youth** — Weekend programming & STEM coaching for students.\n")
                    .append("3. 🍲 **Community Food Bank Support** — Weekend nutrition & meal distribution.\n")
                    .append("4. 🐾 **Paws & Care Animal Rescue** — Shelter support, vaccination drives & fostering assistance.\n\n")
                    .append("✨ **Intranet Banner:** Complete your volunteering activity and share photos to get featured on the corporate intranet banner!\n")
                    .append("🔗 **Enrollment Portal:** [CSR Volunteer Enrollment](https://csr.peoplefirst.internal/enroll)");
            response.setQuickReplies(List.of("Apply Volunteering Leave", "Check leave balance", "Campus Amenities"));
        } else if (lower.contains("hospital") || lower.contains("doctor") || lower.contains("physician") || lower.contains("medical") || lower.contains("opd") || lower.contains("consult")) {
            List<HospitalPartnerDto> hospitals = wellbeingService.getHospitalPartners(user.getBaseLocation());
            sb.append("🏥 **Doctor Consultation & Partner Hospitals (").append(user.getBaseLocation()).append(")**:\n\n")
                    .append("• **On-Site General Physician (GP):** Building 2, 1st Floor Health Center (Mon-Fri 9:00 AM - 5:00 PM)\n\n")
                    .append("• **Network Partner Hospitals with Exclusive Discounts:**\n");
            for (HospitalPartnerDto h : hospitals) {
                sb.append("  - **").append(h.getName()).append("** (").append(h.getAddress()).append(")\n")
                        .append("     OPD Discount: ").append(h.getOpdDiscount()).append(" | Diagnostics: ").append(h.getLabTestDiscount()).append(" | Tel: ").append(h.getContactNumber()).append("\n");
            }
            sb.append("\n📄 **Insurance OPD & Hospitalization Reimbursement:**\n")
                    .append("Did you consult a doctor? Remember to retain and submit all OPD consultation and hospitalization bills within **90 days** on the [Insurance Claims Portal](https://insurance.peoplefirst.internal/claims) for full corporate reimbursement.");
            response.setActionData(hospitals);
            response.setQuickReplies(List.of("Insurance Claims Portal", "Apply Sick Leave", "Campus Amenities"));
        } else if (lower.contains("resort") || lower.contains("vacation") || lower.contains("hotel") || lower.contains("getaway") || lower.contains("break")) {
            sb.append("🌴 **Partner Resorts & Corporate Vacation Getaways**:\n\n");
            wellbeingService.getResortPartners().forEach(r -> {
                sb.append("• **").append(r.getName()).append("** (").append(r.getDestination()).append(" — ").append(r.getType()).append(")\n")
                        .append("   - Benefit: ").append(r.getDiscount()).append(" | Corporate Promo Code: `").append(r.getCouponCode()).append("`\n\n");
            });

            if (lower.contains("email") || lower.contains("mail") || lower.contains("send")) {
                wellbeingService.sendVacationNudgeEmail(user);
                sb.append("📧 **Email Dispatched:** I have sent a vacation reminder email with all resort details and corporate discount codes directly to your registered email (`").append(user.getEmail()).append("`)!\n");
            }
            response.setActionData(wellbeingService.getResortPartners());
            response.setQuickReplies(List.of("Apply for leave", "Check leave balance", "Send vacation email"));
        } else if (lower.contains("legal") || lower.contains("lawyer") || lower.contains("law advisor")) {
            sb.append("⚖️ **Over-Call Legal Advisor Support**:\n\n")
                    .append("• **Hotline:** 24/7 Corporate Confidential Legal Helpline\n")
                    .append("• **Coverage:** Civil law, real-estate/property advisory, family matters, and general personal legal guidance.\n")
                    .append("• **Access:** Call toll-free `1800-PEOPLE-LEGAL` or schedule a private phone consultation via HR portal.");
            response.setQuickReplies(List.of("Campus Amenities", "Weekly Wellbeing Status", "Check balance"));
        } else if (lower.contains("psychologist") || lower.contains("counsel") || lower.contains("mental")) {
            sb.append("🧠 **On-Site Psychologist & Mental Wellbeing Support**:\n\n")
                    .append("• **Location:** Building 2, 2nd Floor, Quiet Zone\n")
                    .append("• **Timings:** 10:00 AM - 6:00 PM (Monday to Friday, by appointment)\n")
                    .append("• **Scope:** 100% confidential one-on-one stress management, career wellness, and psychological counseling.");
            response.setQuickReplies(List.of("Campus Amenities", "Recliner Massage Chairs", "Recreational Lounge"));
        } else if (lower.contains("recreation") || lower.contains("game") || lower.contains("snooker") || lower.contains("carrom") || lower.contains("chess") || lower.contains("table tennis") || lower.contains("tt")) {
            sb.append("🎱 **Recreational Lounge & Games Area**:\n\n")
                    .append("• **Location:** Building 3, 3rd Floor\n")
                    .append("• **Hours:** Open 24/7\n")
                    .append("• **Games Available:** Table Tennis tables, professional Snooker tables, Chess boards, and Carrom boards.\n")
                    .append("• **Access:** Walk in any time to take a mental break or challenge your colleagues!");
            response.setQuickReplies(List.of("Recliner Massage Chairs", "Campus Amenities", "Apply for leave"));
        } else if (lower.contains("massage") || lower.contains("recliner")) {
            sb.append("🛋️ **Zero-Gravity Recliner Massage Pods**:\n\n")
                    .append("• **Location:** Building 1, 4th Floor Relaxation Pod\n")
                    .append("• **Hours:** 8:00 AM - 8:00 PM daily\n")
                    .append("• **Features:** Acoustic-dampened room with heated ergonomic zero-gravity massage recliners. Recommended for 30-minute recharge power sessions.");
            response.setQuickReplies(List.of("Recreational Lounge", "Campus Amenities", "Apply for leave"));
        } else {
            sb.append("✨ **peopleFirst Campus Amenities & Wellbeing Catalog**:\n\n");
            wellbeingService.getAllAmenities().forEach(a -> {
                sb.append("• **").append(a.getName()).append("** (").append(a.getLocation()).append(")\n")
                        .append("   - Category: ").append(a.getCategory()).append(" | Hours: ").append(a.getTiming()).append("\n")
                        .append("   - ").append(a.getDescription()).append("\n\n");
            });
            response.setActionData(wellbeingService.getAllAmenities());
            response.setQuickReplies(List.of("Weekly Wellbeing Status", "Check leave balance", "Apply for leave", "Partner hospitals"));
        }

        response.setReply(sb.toString());
        response.setIntent(AgentIntent.WELLBEING_INQUIRY.name());
        response.setActionExecuted(true);
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
        // 1. Try GenAI intent classification as last resort
        if (genAiClient.isConfigured()) {
            Optional<AgentIntent> aiIntent = classifyIntentWithGenAi(message, user);
            if (aiIntent.isPresent() && aiIntent.get() != AgentIntent.UNKNOWN) {
                // Re-route to the classified intent handler
                AgentIntent resolved = aiIntent.get();
                switch (resolved) {
                    case CHECK_BALANCE: return handleCheckBalance(message, user);
                    case APPLY_LEAVE: return handleApplyLeave(message, user);
                    case CANCEL_LEAVE: return handleCancelLeave(message, user);
                    case EDIT_LEAVE: return handleEditLeave(message, user);
                    case VIEW_LEAVES: return handleViewLeaves(user);
                    case CHECK_POLICY: return handleCheckPolicy(user);
                    case WELLBEING_INQUIRY: return handleWellbeingInquiry(message, user);
                    case STRESS_EXPRESSION: return handleStressExpression(message, user);
                    case GREETING: return handleGreeting(user);
                    default: break; // fall through to conversational GenAI
                }
            }

            // 2. Try conversational GenAI response
            String systemContext = buildSystemContext(user);
            Optional<String> genAiReply = genAiClient.generateContent(systemContext, message);
            if (genAiReply.isPresent()) {
                AgentChatResponseDto response = new AgentChatResponseDto(genAiReply.get(), AgentIntent.UNKNOWN.name());
                response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
                return updateContextAndReturn(response, user, AgentIntent.UNKNOWN, ConversationContext.PromptType.GENERAL);
            }
        }

        // 3. Context-aware smart fallback (no GenAI available)
        String reply = buildSmartFallback(message, user);
        AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.UNKNOWN.name());
        response.setQuickReplies(List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities"));
        return updateContextAndReturn(response, user, AgentIntent.UNKNOWN, ConversationContext.PromptType.GENERAL);
    }

    /**
     * Attempts to classify user intent using GenAI as a last-resort fallback.
     * Returns Optional.empty() if GenAI is unavailable or fails.
     */
    private Optional<AgentIntent> classifyIntentWithGenAi(String message, User user) {
        try {
            String prompt = "You are an intent classifier for a corporate Leave Management system called peopleFirst.\n" +
                    "Given the user message below, classify it into EXACTLY ONE of these intents:\n" +
                    "APPLY_LEAVE, CANCEL_LEAVE, EDIT_LEAVE, CHECK_BALANCE, VIEW_LEAVES, " +
                    "CHECK_POLICY, WELLBEING_INQUIRY, STRESS_EXPRESSION, GREETING, UNKNOWN\n\n" +
                    "Important rules:\n" +
                    "- If the user wants to take leave, apply for leave, get time off, or mentions 'chutti' → APPLY_LEAVE\n" +
                    "- If the user asks about remaining days, how many leaves, quota → CHECK_BALANCE\n" +
                    "- If the user wants to cancel/withdraw a leave → CANCEL_LEAVE\n" +
                    "- If the user wants to edit/update/change/reschedule a leave → EDIT_LEAVE\n" +
                    "- If the user asks about rules, policies, eligibility → CHECK_POLICY\n" +
                    "- If the user mentions gym, doctor, yoga, massage, amenities, wellness → WELLBEING_INQUIRY\n" +
                    "- If the user expresses stress, burnout, exhaustion → STRESS_EXPRESSION\n" +
                    "- If the user greets or asks for help → GREETING\n" +
                    "- ONLY return UNKNOWN if you truly cannot determine the intent\n\n" +
                    "User message: \"" + message + "\"\n\n" +
                    "Respond with ONLY the intent name, nothing else.";

            Optional<String> aiResponse = genAiClient.generateContent(buildSystemContext(user), prompt);
            if (aiResponse.isPresent()) {
                String intentStr = aiResponse.get().trim().toUpperCase()
                        .replaceAll("[^A-Z_]", ""); // Strip any non-enum characters
                try {
                    return Optional.of(AgentIntent.valueOf(intentStr));
                } catch (IllegalArgumentException ignored) {
                    // GenAI returned something we can't parse
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /**
     * Builds a context-aware fallback message instead of the generic "I didn't quite catch that".
     */
    private String buildSmartFallback(String message, User user) {
        ConversationContext ctx = userContexts.get(user.getId());
        StringBuilder sb = new StringBuilder();

        // Check if user might be trying to say something related to their last context
        if (ctx != null && !ctx.isExpired() && ctx.getLastIntent() != null) {
            switch (ctx.getLastPromptType()) {
                case LEAVE_TYPE:
                    sb.append("I couldn't identify the leave type from your message. ");
                    sb.append("You can choose from: **Casual**, **Sick**, **Paid**, **WFH**, or **LOP**.\n\n");
                    sb.append("Just type the leave type name, or say **cancel** to stop.");
                    return sb.toString();
                case HALF_DAY_SESSION:
                    sb.append("Please let me know if you prefer **First Half (Morning)** or **Second Half (Afternoon)** for your half-day leave.\n\n");
                    sb.append("Or say **cancel** to stop.");
                    return sb.toString();
                case DATE:
                    sb.append("I couldn't parse a date from your message. ");
                    sb.append("Try saying something like **tomorrow**, **next Monday**, **10th Sep**, **for 3 days**, or any date format (e.g. **10-09-2026** or **2026-09-10**).\n\n");
                    sb.append("Or say **cancel** to stop.");
                    return sb.toString();
                case REASON:
                    sb.append("I'm waiting for your leave reason. Just type your reason ");
                    sb.append("(e.g., *\"fever\"*, *\"family function\"*, *\"personal work\"*), ");
                    sb.append("or say **skip** to use a default reason.");
                    return sb.toString();
                case CONFIRMATION:
                    sb.append("I'm waiting for your confirmation. Say **yes** to submit or **no** to cancel.");
                    return sb.toString();
                default:
                    break;
            }
        }

        // Check if user has active leaves they might want to manage
        try {
            List<LeaveResponseDto> activeLeaves = leaveService.getLeavesForUser(user.getId()).stream()
                    .filter(l -> l.getStatus() == LeaveStatus.PENDING || l.getStatus() == LeaveStatus.APPROVED)
                    .filter(l -> l.getEndDate() == null || !l.getEndDate().isBefore(LocalDate.now()))
                    .collect(Collectors.toList());

            if (!activeLeaves.isEmpty()) {
                sb.append("I'm not sure what you mean, but I noticed you have **")
                        .append(activeLeaves.size()).append(" upcoming leave(s)**. ")
                        .append("Did you want to:\n\n")
                        .append("• **Edit** or **cancel** an existing leave?\n")
                        .append("• **Apply** for a new leave?\n")
                        .append("• **Check** your leave balance?\n\n")
                        .append("Just let me know! 💬");
                return sb.toString();
            }
        } catch (Exception ignored) {}

        // Generic but friendly fallback
        sb.append("I'm not sure I understood that. As **Kura**, I can help you with:\n\n");
        sb.append("• 📋 **Check your leave balance** — *\"How many leaves do I have?\"*\n");
        sb.append("• ✈️ **Apply for leave** — *\"Apply for casual leave tomorrow\"*\n");
        sb.append("• ✏️ **Edit or cancel leave** — *\"Edit my leave\"* or *\"Cancel my leave\"*\n");
        sb.append("• 📜 **Company policies** — *\"Show me leave policies\"*\n");
        sb.append("• 🏥 **Wellness amenities** — *\"What amenities are available?\"*\n\n");
        sb.append("Try rephrasing, or tap one of the quick replies below! 👇");
        return sb.toString();
    }

    /**
     * Wraps any response to update conversation context before returning.
     * This enables context-aware intent resolution on the NEXT user message.
     */
    private AgentChatResponseDto updateContextAndReturn(AgentChatResponseDto response, User user,
                                                         AgentIntent intent, ConversationContext.PromptType promptType) {
        ConversationContext ctx = userContexts.computeIfAbsent(user.getId(), k -> new ConversationContext());
        ctx.update(intent, promptType, response.getReply());
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
        private String rawReason;
        private String refinedReason;
        private boolean awaitingReason = false;
        private boolean awaitingConfirmation = false;
        private boolean stressInterventionOffered = false;
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
        public String getRawReason() { return rawReason; }
        public void setRawReason(String rawReason) { this.rawReason = rawReason; }
        public String getRefinedReason() { return refinedReason; }
        public void setRefinedReason(String refinedReason) { this.refinedReason = refinedReason; }
        public boolean isAwaitingReason() { return awaitingReason; }
        public void setAwaitingReason(boolean awaitingReason) { this.awaitingReason = awaitingReason; }
        public boolean isAwaitingConfirmation() { return awaitingConfirmation; }
        public void setAwaitingConfirmation(boolean awaitingConfirmation) { this.awaitingConfirmation = awaitingConfirmation; }
        public boolean isStressInterventionOffered() { return stressInterventionOffered; }
        public void setStressInterventionOffered(boolean stressInterventionOffered) { this.stressInterventionOffered = stressInterventionOffered; }
        public String getReason() {
            if (refinedReason != null && !refinedReason.isBlank()) return refinedReason;
            if (rawReason != null && !rawReason.isBlank()) return rawReason;
            return "Applied via Kura AI Agent";
        }
        public void setReason(String reason) {
            this.rawReason = reason;
            this.refinedReason = reason;
        }
    }

    public static class PendingEditDraft {
        private UUID leaveId;
        private LeaveType leaveType;
        private LocalDate startDate;
        private LocalDate endDate;
        private String rawReason;
        private String refinedReason;
        private boolean halfDay = false;
        private String halfDaySession;
        private boolean awaitingReason = false;
        private long createdAt = System.currentTimeMillis();

        public boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > (15 * 60 * 1000L);
        }

        public UUID getLeaveId() { return leaveId; }
        public void setLeaveId(UUID leaveId) { this.leaveId = leaveId; }
        public LeaveType getLeaveType() { return leaveType; }
        public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public String getRawReason() { return rawReason; }
        public void setRawReason(String rawReason) { this.rawReason = rawReason; }
        public String getRefinedReason() { return refinedReason; }
        public void setRefinedReason(String refinedReason) { this.refinedReason = refinedReason; }
        public boolean isHalfDay() { return halfDay; }
        public void setHalfDay(boolean halfDay) { this.halfDay = halfDay; }
        public String getHalfDaySession() { return halfDaySession; }
        public void setHalfDaySession(String halfDaySession) { this.halfDaySession = halfDaySession; }
        public boolean isAwaitingReason() { return awaitingReason; }
        public void setAwaitingReason(boolean awaitingReason) { this.awaitingReason = awaitingReason; }
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

    private static class PendingVolunteeringSignup {
        private final UUID leaveRequestId;
        private final long createdAt = System.currentTimeMillis();

        PendingVolunteeringSignup(UUID leaveRequestId) {
            this.leaveRequestId = leaveRequestId;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > (15 * 60 * 1000L); // 15 mins expiry
        }

        UUID getLeaveRequestId() { return leaveRequestId; }
    }
}
