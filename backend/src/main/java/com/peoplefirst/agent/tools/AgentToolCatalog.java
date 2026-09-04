package com.peoplefirst.agent.tools;

import java.util.List;
import java.util.Map;

public class AgentToolCatalog {

    public List<Map<String, Object>> getSchemas() {
        return List.of(
                schema(AgentTool.CHECK_BALANCE,
                        "Check the user's leave balances. Never invent balances; always call this tool when unsure.",
                        Map.of("leaveType", Map.of("type", "string",
                                "description", "Leave type to check, e.g. Sick, Paid, or LOP. Omit to return all balances.")),
                        List.of()),
                schema(AgentTool.APPLY_LEAVE,
                        "Apply for leave. Contractors: Sick/Paid/LOP only. Paid needs 3+ days notice. Sick > 2 days needs a certificate. Volunteering applicants can join CSR chapters after applying. Never invent balances; call check_balance first when unsure.",
                        Map.of(
                                "leaveType", Map.of("type", "string",
                                        "description", "Leave type, e.g. Sick, Paid, or LOP."),
                                "startDate", Map.of("type", "string",
                                        "description", "Start date in ISO YYYY-MM-DD format."),
                                "endDate", Map.of("type", "string",
                                        "description", "End date in ISO YYYY-MM-DD format."),
                                "halfDay", Map.of("type", "boolean",
                                        "description", "True for a half-day leave."),
                                "halfDaySession", Map.of("type", "string",
                                        "description", "Half-day session: FIRST_HALF or SECOND_HALF. Required when halfDay is true."),
                                "reason", Map.of("type", "string",
                                        "description", "Reason for the leave request.")),
                        List.of("leaveType", "startDate", "endDate")),
                schema(AgentTool.CANCEL_LEAVE,
                        "Cancel the user's pending leave request. Only pending requests can be cancelled; never invent request IDs.",
                        Map.of(),
                        List.of()),
                schema(AgentTool.VIEW_LEAVES,
                        "View the user's leave requests and their statuses. Never invent leave records.",
                        Map.of(),
                        List.of()),
                schema(AgentTool.CHECK_POLICY,
                        "Answer leave-policy questions using the grounded policy source only. Never invent policy rules.",
                        Map.of("topic", Map.of("type", "string",
                                "description", "Policy topic to look up, e.g. notice period or eligibility.")),
                        List.of()),
                schema(AgentTool.WELLBEING,
                        "Answer wellbeing questions about hospitals, resorts, or amenities using the grounded source only. Never invent listings.",
                        Map.of("topic", Map.of("type", "string",
                                "description", "Wellbeing topic: hospitals|resorts|amenities.")),
                        List.of()),
                schema(AgentTool.TICKET_INQUIRY,
                        "Get information about the user's support tickets. Never invent ticket details.",
                        Map.of(),
                        List.of()),
                schema(AgentTool.APPROVE_LEAVE,
                        "Approve a team member's pending leave request. Requires the pending leave ID. Never invent leave IDs; ask for pending approvals first.",
                        Map.of(
                                "leaveId", Map.of("type", "string",
                                        "description", "Pending leave request ID as a UUID string."),
                                "comment", Map.of("type", "string",
                                        "description", "Optional approval comment.")),
                        List.of("leaveId")),
                schema(AgentTool.REJECT_LEAVE,
                        "Reject a team member's pending leave request. Requires the pending leave ID. Never invent leave IDs; ask for pending approvals first.",
                        Map.of(
                                "leaveId", Map.of("type", "string",
                                        "description", "Pending leave request ID as a UUID string."),
                                "comment", Map.of("type", "string",
                                        "description", "Optional rejection comment.")),
                        List.of("leaveId")),
                schema(AgentTool.LIST_PENDING_APPROVALS,
                        "List leave requests awaiting YOUR approval as a manager/admin (employee name, type, dates, ID). Call this FIRST when asked about pending/team approvals, before approve/reject. Returns [] for non-managers.",
                        Map.of(),
                        List.of()));
    }

    private Map<String, Object> schema(AgentTool tool, String description,
            Map<String, Object> properties, List<String> required) {
        return Map.of("type", "function", "function", Map.of("name", tool.getName(),
                "description", description, "parameters", Map.of("type", "object",
                        "properties", properties, "required", required)));
    }
}
