package com.peoplefirst.agent.service;

import com.peoplefirst.agent.client.GenAiClient;
import com.peoplefirst.agent.dto.AgentChatRequestDto;
import com.peoplefirst.agent.dto.AgentChatResponseDto;
import com.peoplefirst.agent.intent.AgentIntent;
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
import com.peoplefirst.wellbeing.dto.AmenityDto;
import com.peoplefirst.wellbeing.dto.HospitalPartnerDto;
import com.peoplefirst.wellbeing.dto.ResortPartnerDto;
import com.peoplefirst.wellbeing.dto.WellbeingSuggestionDto;
import com.peoplefirst.wellbeing.service.WellbeingService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // Multi-turn conversational leave draft store keyed by User UUID
    private final Map<UUID, PendingLeaveDraft> userDrafts = new ConcurrentHashMap<>();

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
                        UserService userService) {
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
    }

    public AgentChatResponseDto processMessage(AgentChatRequestDto request) {
        // Overriding rule: Identity comes strictly from SecurityContext -> DB
        User user = currentUserProvider.getCurrentUser();
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
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
                intent == AgentIntent.CHECK_TEAM_BALANCES ||
                intent == AgentIntent.VIEW_LEAVES ||
                intent == AgentIntent.VIEW_PENDING_APPROVALS ||
                intent == AgentIntent.APPROVE_LEAVE ||
                intent == AgentIntent.REJECT_LEAVE ||
                intent == AgentIntent.SEND_BACK_LEAVE ||
                intent == AgentIntent.WELLBEING_INQUIRY ||
                intent == AgentIntent.CANCEL_LEAVE ||
                intent == AgentIntent.EDIT_LEAVE ||
                intent == AgentIntent.CHECK_POLICY ||
                intent == AgentIntent.STRESS_EXPRESSION ||
                intent == AgentIntent.RAISE_TICKET ||
                intent == AgentIntent.ADMIN_DIRECT_EDIT);

        if (draft != null && !isExplicitOtherIntent) {
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

    private AgentChatResponseDto handleCheckBalance(String message, User user) {
        int year = LocalDate.now().getYear();
        leaveBalanceService.initializeUserBalancesIfAbsent(user, year);
        List<LeaveBalance> balances = leaveBalanceService.getUserBalances(user.getId(), year);

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

            if (leaveType == LeaveType.SICK) {
                if (daysBetween > 2) {
                    sb.append("• **Medical Certificate:** Digital placeholder attached for manager review (`DOC-")
                            .append(UUID.randomUUID().toString().substring(0, 8).toUpperCase()).append("`)\n");
                }
                sb.append("\n🏥 **Health & Medical Care Support (Kura Concierge):**\n")
                        .append("• Did you consult a doctor? If yes, remember to submit your OPD/Hospitalization bills within 90 days for corporate insurance reimbursement ([Insurance Claims Portal](https://insurance.peoplefirst.internal/claims)).\n")
                        .append("• Partner network hospitals in **").append(user.getBaseLocation()).append("** offering corporate OPD discounts are available in the amenities catalog.\n");

                if (isHalfDay) {
                    sb.append("• 🛌 **Office Sick Room:** Would you like to take rest in the office sick room before heading home? (Building 2, 1st Floor, Room 104 — on-duty nurse available).\n");
                }
            } else if (leaveType == LeaveType.VOLUNTEERING) {
                sb.append("\n🤝 **Corporate Volunteering:** Thank you for giving back! Active company CSR chapters: Corporate Green Earth Initiative, STEM Mentorship for Schools, and Blood Donation Drive. You are welcome to participate under the peopleFirst company banner!\n");
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
        response.setQuickReplies(getPostActionQuickReplies(user));
        return response;
    }

    private AgentChatResponseDto handleEditLeave(String message, User user) {
        List<LeaveResponseDto> userLeaves = leaveService.getLeavesForUser(user.getId());
        List<LeaveResponseDto> editable = userLeaves.stream()
                .filter(l -> (l.getStatus() == LeaveStatus.PENDING || l.getStatus() == LeaveStatus.RETURNED) &&
                        !l.getStartDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());

        if (editable.isEmpty()) {
            return new AgentChatResponseDto(
                    "You do not have any upcoming pending or returned leave requests eligible for editing. To request adjustments for leaves whose dates have passed, please raise a support ticket.",
                    AgentIntent.EDIT_LEAVE.name()
            );
        }

        // Identify target leave
        LeaveResponseDto target = editable.get(0);
        UUID reqUuid = intentParser.extractUuid(message);
        if (reqUuid != null) {
            for (LeaveResponseDto l : editable) {
                if (l.getId().equals(reqUuid)) {
                    target = l;
                    break;
                }
            }
        }

        LocalDate[] dates = intentParser.extractDates(message);
        LeaveType newType = intentParser.extractLeaveType(message);
        if (newType == null) {
            newType = target.getLeaveType();
        }

        if (dates[0] == null) {
            String id8 = target.getId().toString().substring(0, 8);
            AgentChatResponseDto response = new AgentChatResponseDto(
                    "You can edit your **" + target.getLeaveTypeDisplayName() + "** (`" + id8 + "` from " +
                            target.getStartDate() + " to " + target.getEndDate() + ").\n\n" +
                            "Please specify the new start and end dates (e.g. 'change dates to tomorrow' or 'YYYY-MM-DD to YYYY-MM-DD').",
                    AgentIntent.EDIT_LEAVE.name()
            );
            response.setQuickReplies(List.of("Tomorrow", "Next Week", "Cancel"));
            return response;
        }

        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1] != null ? dates[1] : startDate;

        UpdateLeaveRequestDto updateDto = new UpdateLeaveRequestDto();
        updateDto.setLeaveType(newType);
        updateDto.setStartDate(startDate);
        updateDto.setEndDate(endDate);
        updateDto.setHalfDay(intentParser.extractHalfDay(message));
        updateDto.setReason("Updated via Kura AI Agent: " + message);

        try {
            LeaveResponseDto updated = leaveService.editLeave(target.getId(), updateDto, user);
            String reply = "✏️ **Leave Request Updated Successfully!**\n\n" +
                    "• **Type:** " + updated.getLeaveTypeDisplayName() + "\n" +
                    "• **New Dates:** " + updated.getStartDate() + " to " + updated.getEndDate() + " (" + updated.getTotalDays() + " day" + (updated.getTotalDays() > 1 ? "s" : "") + ")\n" +
                    "• **Status:** " + updated.getStatus() + "\n\n" +
                    "Your manager will review the updated dates.";

            AgentChatResponseDto response = new AgentChatResponseDto(reply, AgentIntent.EDIT_LEAVE.name());
            response.setActionExecuted(true);
            response.setActionName("EDIT_LEAVE");
            response.setActionData(updated);
            response.setQuickReplies(getPostActionQuickReplies(user));
            return response;
        } catch (Exception e) {
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
        return status;
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
}
