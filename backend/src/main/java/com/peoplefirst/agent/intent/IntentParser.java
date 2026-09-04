package com.peoplefirst.agent.intent;

import com.peoplefirst.policy.entity.LeaveType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntentParser {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})");
    private static final Pattern DAYS_COUNT_PATTERN = Pattern.compile("(?:for\\s+)?(\\d+)\\s*(?:work)?days?");

    public AgentIntent parseIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return AgentIntent.GREETING;
        }

        String lower = message.toLowerCase().trim();

        // 1. Admin direct DB edit
        if (lower.contains("direct edit") || lower.contains("direct-edit") || lower.contains("admin edit") ||
                lower.contains("db edit") || lower.contains("directly update")) {
            return AgentIntent.ADMIN_DIRECT_EDIT;
        }

        // 2. Manager / Supervisor Actions
        if (lower.startsWith("approve") || lower.contains("approve leave") || lower.contains("approve request") ||
                lower.contains("approve this")) {
            return AgentIntent.APPROVE_LEAVE;
        }

        if (lower.startsWith("reject") || lower.contains("reject leave") || lower.contains("reject request") ||
                lower.contains("decline leave")) {
            return AgentIntent.REJECT_LEAVE;
        }

        if (lower.startsWith("send back") || lower.contains("send back") || lower.contains("return leave") ||
                lower.contains("return request") || lower.contains("send-back")) {
            return AgentIntent.SEND_BACK_LEAVE;
        }

        if (lower.contains("pending approval") || lower.contains("requests to approve") ||
                lower.contains("team requests") || lower.contains("approval queue") ||
                lower.equals("approvals") || lower.contains("pending approvals") || lower.contains("approvals queue")) {
            return AgentIntent.VIEW_PENDING_APPROVALS;
        }

        if (lower.contains("team balance") || lower.contains("reportees balance") ||
                lower.contains("reportee balance") || lower.contains("my team's balance") ||
                lower.contains("team's leave") || lower.contains("team leave balance")) {
            return AgentIntent.CHECK_TEAM_BALANCES;
        }

        // 3. Stress Expression
        if (lower.contains("stress") || lower.contains("burnout") || lower.contains("exhausted") ||
                lower.contains("overwhelmed") || lower.contains("too much pressure") || lower.contains("drained")) {
            return AgentIntent.STRESS_EXPRESSION;
        }

        // 4. Edit Leave
        if (lower.startsWith("edit") || lower.contains("edit leave") || lower.contains("modify leave") ||
                lower.contains("update leave") || lower.contains("change leave dates") || lower.contains("change my leave") ||
                lower.contains("change dates of my leave")) {
            return AgentIntent.EDIT_LEAVE;
        }

        // 5. Leave Application
        if (lower.startsWith("apply") || lower.contains("apply for") || lower.contains("request leave") ||
                lower.contains("book leave") || lower.contains("take leave") || lower.contains("apply leave") ||
                lower.contains("need leave") || lower.contains("want leave") || lower.contains("want to apply") ||
                lower.contains("need to apply") || lower.contains("take a leave") || lower.contains("take off") ||
                lower.contains("submit leave") || lower.contains("unable to apply") || lower.contains("cannot apply") ||
                lower.contains("how to apply") ||
                (extractLeaveType(lower) != null && (extractDates(lower)[0] != null || lower.contains("leave") || lower.contains("off")))) {
            return AgentIntent.APPLY_LEAVE;
        }

        // 6. Cancel Leave
        if (lower.contains("cancel leave") || lower.contains("cancel my leave") || lower.startsWith("cancel")) {
            return AgentIntent.CANCEL_LEAVE;
        }

        // 7. Check Balance
        if (lower.contains("balance") || lower.contains("remaining") || lower.contains("how many days") ||
                lower.contains("leave quota") || lower.contains("available leaves")) {
            return AgentIntent.CHECK_BALANCE;
        }

        // 7.5 Raise Ticket / Support (Explicit action takes precedence over informational queries)
        if (lower.startsWith("raise ticket") || lower.startsWith("create ticket") || lower.contains("raise a ticket") ||
                lower.contains("raise a support ticket") || lower.contains("open ticket") || lower.contains("submit ticket") ||
                lower.contains("create a ticket") || lower.contains("raise ticket for")) {
            return AgentIntent.RAISE_TICKET;
        }

        // 8. Check Policy
        if (lower.contains("policy") || lower.contains("policies") || lower.contains("rule") ||
                lower.contains("can i combine") || lower.contains("eligib") || lower.contains("cutoff") ||
                lower.contains("deadline") || lower.contains("combination")) {
            return AgentIntent.CHECK_POLICY;
        }

        // 9. View Leaves / Status
        if (lower.contains("my leaves") || lower.contains("leave status") || lower.contains("history") ||
                lower.contains("my applications") || lower.contains("my requests")) {
            return AgentIntent.VIEW_LEAVES;
        }

        // 10. Ticket inquiry
        if (lower.contains("ticket") || lower.contains("support desk") || lower.contains("helpdesk") ||
                lower.contains("technical error") || lower.contains("support")) {
            return AgentIntent.TICKET_INQUIRY;
        }

        // 12. Wellbeing inquiry
        if (lower.contains("amenit") || lower.contains("gym") || lower.contains("doctor") ||
                lower.contains("psychologist") || lower.contains("massage") || lower.contains("yoga") ||
                lower.contains("zumba") || lower.contains("hospital") || lower.contains("resort") ||
                lower.contains("sick room") || lower.contains("benefits") || lower.contains("lawyer") ||
                lower.contains("legal advisor")) {
            return AgentIntent.WELLBEING_INQUIRY;
        }

        // 13. Greeting / Help
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") || lower.contains("help") ||
                lower.contains("who are you") || lower.contains("what can you do") || lower.contains("start")) {
            return AgentIntent.GREETING;
        }

        return AgentIntent.UNKNOWN;
    }

    public LeaveType extractLeaveType(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase();

        if (lower.contains("casual")) return LeaveType.CASUAL;
        if (lower.contains("sick")) return LeaveType.SICK;
        if (lower.contains("paid") || lower.contains("privilege") || lower.contains("annual")) return LeaveType.PAID;
        if (lower.contains("lop") || lower.contains("loss of pay") || lower.contains("unpaid")) return LeaveType.LOP;
        if (lower.contains("wfh") || lower.contains("work from home")) return LeaveType.WFH;
        if (lower.contains("maternity")) return LeaveType.MATERNITY;
        if (lower.contains("volunteering") || lower.contains("volunteer")) return LeaveType.VOLUNTEERING;

        return null;
    }

    public LeaveType extractCombinedType(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase();

        if (lower.contains("combine with wfh") || lower.contains("+ wfh") || lower.contains("and wfh") ||
                lower.contains("with work from home")) {
            return LeaveType.WFH;
        }
        if (lower.contains("combine with sick") || lower.contains("+ sick")) {
            return LeaveType.SICK;
        }
        if (lower.contains("combine with paid") || lower.contains("+ paid")) {
            return LeaveType.PAID;
        }
        if (lower.contains("combine with casual") || lower.contains("+ casual")) {
            return LeaveType.CASUAL;
        }
        return null;
    }

    public LocalDate[] extractDates(String message) {
        if (message == null) return new LocalDate[]{null, null};
        String lower = message.toLowerCase().trim();

        LocalDate firstDate = null;
        LocalDate secondDate = null;

        // 1. Try ISO date regex (2026-09-08 or 2026/09/08)
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(message);
        if (isoMatcher.find()) {
            firstDate = parseFlexibleIsoDate(isoMatcher.group(1));
            if (isoMatcher.find()) {
                secondDate = parseFlexibleIsoDate(isoMatcher.group(1));
            }
        }

        // 2. Try DD/MM/YYYY regex if not found
        if (firstDate == null) {
            Matcher dmyMatcher = DMY_DATE_PATTERN.matcher(message);
            if (dmyMatcher.find()) {
                firstDate = parseFlexibleDmyDate(dmyMatcher.group(1));
                if (dmyMatcher.find()) {
                    secondDate = parseFlexibleDmyDate(dmyMatcher.group(1));
                }
            }
        }

        // 3. Natural relative expressions
        if (firstDate == null) {
            Matcher inDaysMatcher = Pattern.compile("in\\s+(\\d+)\\s*days?").matcher(lower);
            if (inDaysMatcher.find()) {
                try {
                    int daysAhead = Integer.parseInt(inDaysMatcher.group(1));
                    firstDate = LocalDate.now().plusDays(daysAhead);
                } catch (NumberFormatException ignored) {}
            } else if (lower.contains("day after tomorrow")) {
                firstDate = LocalDate.now().plusDays(2);
            } else if (lower.contains("tomorrow")) {
                firstDate = LocalDate.now().plusDays(1);
            } else if (lower.contains("today")) {
                firstDate = LocalDate.now();
            } else if (lower.contains("next week")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
                secondDate = firstDate.plusDays(4); // 5 days (Mon-Fri)
            } else if (lower.contains("next monday") || lower.contains("monday")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
            } else if (lower.contains("tuesday")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.TUESDAY));
            } else if (lower.contains("wednesday")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.WEDNESDAY));
            } else if (lower.contains("thursday")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.THURSDAY));
            } else if (lower.contains("friday")) {
                firstDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY));
            }
        }

        // 4. Check for day counts / durations: "3 days", "2 days", "for 5 days", "next 3 days"
        if (secondDate == null) {
            Matcher durationMatcher = Pattern.compile("(?:for|next)\\s+(\\d+)\\s*(?:work)?days?").matcher(lower);
            if (durationMatcher.find()) {
                try {
                    int count = Integer.parseInt(durationMatcher.group(1));
                    if (count > 0) {
                        if (firstDate == null) {
                            firstDate = LocalDate.now().plusDays(1);
                        }
                        secondDate = firstDate.plusDays(count - 1);
                    }
                } catch (NumberFormatException ignored) {}
            } else {
                Matcher standaloneDays = Pattern.compile("\\b(\\d+)\\s*(?:work)?days?\\b").matcher(lower);
                if (standaloneDays.find() && !lower.contains("in " + standaloneDays.group(1))) {
                    try {
                        int count = Integer.parseInt(standaloneDays.group(1));
                        if (count > 0) {
                            if (firstDate == null) {
                                firstDate = LocalDate.now().plusDays(1);
                            }
                            secondDate = firstDate.plusDays(count - 1);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (firstDate != null && secondDate == null) {
            secondDate = firstDate;
        }

        return new LocalDate[]{firstDate, secondDate};
    }

    private LocalDate parseFlexibleIsoDate(String s) {
        try {
            String sanitized = s.replace('/', '-');
            String[] parts = sanitized.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int d = Integer.parseInt(parts[2]);
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseFlexibleDmyDate(String s) {
        try {
            String sanitized = s.replace('/', '-');
            String[] parts = sanitized.split("-");
            int d = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean extractHalfDay(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("half day") || lower.contains("half-day") || lower.contains("0.5 day");
    }

    public boolean extractDocumentAttached(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("document") || lower.contains("certificate") || lower.contains("prescription") ||
                lower.contains("attached") || lower.contains("doctor note");
    }

    public java.util.UUID extractUuid(String message) {
        if (message == null) return null;
        Matcher m = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})", Pattern.CASE_INSENSITIVE).matcher(message);
        if (m.find()) {
            try {
                return java.util.UUID.fromString(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }
}
