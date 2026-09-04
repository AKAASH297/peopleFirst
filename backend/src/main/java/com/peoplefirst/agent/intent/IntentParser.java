package com.peoplefirst.agent.intent;

import com.peoplefirst.policy.entity.LeaveType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntentParser {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})");
    private static final Pattern DAYS_COUNT_PATTERN = Pattern.compile("(?:for\\s+)?(\\d+)\\s*(?:work)?days?");

    // --- Intent trigger keyword lists for fuzzy matching ---
    private static final List<String> APPLY_KEYWORDS = Arrays.asList(
            "apply", "request", "book", "submit", "take", "need", "want"
    );
    private static final List<String> CANCEL_KEYWORDS = Arrays.asList(
            "cancel", "cancle", "cancal", "withdraw", "drop", "delete", "remove", "revoke"
    );
    private static final List<String> EDIT_KEYWORDS = Arrays.asList(
            "edit", "update", "modify", "change", "reschedule", "move", "shift", "postpone"
    );
    private static final List<String> BALANCE_KEYWORDS = Arrays.asList(
            "balance", "remaining", "quota", "available", "left"
    );
    private static final List<String> LEAVE_KEYWORDS = Arrays.asList(
            "leave", "chutti", "chhutti", "off", "day off", "dayoff"
    );
    private static final List<String> GREETING_KEYWORDS = Arrays.asList(
            "hi", "hello", "hey", "hola", "namaste", "help", "start"
    );

    // --- Leave type names for fuzzy matching ---
    private static final List<String> CASUAL_NAMES = Arrays.asList("casual");
    private static final List<String> SICK_NAMES = Arrays.asList("sick", "medical");
    private static final List<String> PAID_NAMES = Arrays.asList("paid", "privilege", "annual", "earned");
    private static final List<String> LOP_NAMES = Arrays.asList("lop", "unpaid");
    private static final List<String> WFH_NAMES = Arrays.asList("wfh");
    private static final List<String> MATERNITY_NAMES = Arrays.asList("maternity");
    private static final List<String> VOLUNTEERING_NAMES = Arrays.asList("volunteering", "volunteer");

    /**
     * Original backward-compatible parseIntent (no context).
     */
    public AgentIntent parseIntent(String message) {
        return parseIntent(message, null);
    }

    /**
     * Context-aware intent parsing with 3-tier fallback:
     * 1. Exact keyword matching (fast, precise)
     * 2. Fuzzy matching with Levenshtein distance (typo tolerance)
     * 3. Contextual inference from previous conversation turn
     */
    public AgentIntent parseIntent(String message, ConversationContext context) {
        if (message == null || message.trim().isEmpty()) {
            return AgentIntent.GREETING;
        }

        String lower = message.toLowerCase().trim();

        // === TIER 1: Exact keyword matching (unchanged priority order) ===

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

        // 3. Cancel / Withdraw Leave
        if (lower.startsWith("cancel") || lower.startsWith("cancle") || lower.startsWith("cancal") || lower.startsWith("withdraw") ||
                lower.startsWith("drop leave") || lower.startsWith("delete leave") || lower.startsWith("remove leave") ||
                lower.contains("cancel leave") || lower.contains("cancle leave") || lower.contains("cancal leave") ||
                lower.contains("cancel my") || lower.contains("cancle my") || lower.contains("cancal my") ||
                lower.contains("cancel this") || lower.contains("cancle this") || lower.contains("cancal this") ||
                lower.contains("withdraw leave") || lower.contains("delete leave") || lower.contains("drop leave") ||
                lower.contains("remove leave") || lower.equals("cancel") || lower.equals("cancle") || lower.equals("cancal")) {
            return AgentIntent.CANCEL_LEAVE;
        }

        // === CONTEXT-AWARE RESOLUTION (Active prompt in progress) ===
        // If user is answering an active interactive prompt (session, reason, date, type, confirmation),
        // resolve in-context first before generic keyword matching (e.g. 'doctor' in a reason)
        if (context != null && !context.isExpired()) {
            AgentIntent contextResult = contextAwareResolve(lower, context);
            if (contextResult != AgentIntent.UNKNOWN) {
                return contextResult;
            }
        }

        // 4. Edit / Update Leave
        if (lower.startsWith("edit") || lower.startsWith("edidt") || lower.startsWith("update") ||
                lower.startsWith("modify") || lower.startsWith("reschedule") || lower.startsWith("change ") ||
                lower.startsWith("move ") || lower.startsWith("shift ") ||
                lower.contains("edit leave") || lower.contains("edit my") || lower.contains("edidt") ||
                lower.contains("update leave") || lower.contains("update my") || lower.contains("modify leave") ||
                lower.contains("modify my") || lower.contains("reschedule") ||
                (lower.contains("change") && (lower.contains("leave") || lower.contains("date") ||
                        extractLeaveType(lower) != null || extractDates(lower)[0] != null))) {
            return AgentIntent.EDIT_LEAVE;
        }

        // 5. Leave Application
        if (lower.startsWith("apply") || lower.contains("apply for") || lower.contains("request leave") ||
                lower.contains("book leave") || lower.contains("take leave") || lower.contains("apply leave") ||
                lower.contains("need leave") || lower.contains("want leave") || lower.contains("want to apply") ||
                lower.contains("need to apply") || lower.contains("take a leave") || lower.contains("take off") ||
                lower.contains("submit leave") || lower.contains("unable to apply") || lower.contains("cannot apply") ||
                lower.contains("chutti chahiye") || lower.contains("chhutti chahiye") || lower.contains("chhuti chahiye") ||
                lower.contains("chutti lena") || lower.contains("chhutti lena") || lower.contains("chutti leni") ||
                lower.contains("mujhe chutti") || lower.contains("leave chahiye") || lower.contains("leave lena") ||
                lower.contains("leave do") || lower.contains("leave dedo") || lower.contains("chutti do") || lower.contains("chhutti do") ||
                (extractLeaveType(lower) != null && (extractDates(lower)[0] != null || lower.contains("leave") || lower.contains("off") || lower.contains("chutti")))) {
            return AgentIntent.APPLY_LEAVE;
        }

        // 6. Standalone Stress Expression
        if (lower.contains("stress") || lower.contains("burnout") || lower.contains("exhausted") ||
                lower.contains("overwhelmed") || lower.contains("too much pressure") || lower.contains("lot of pressure") ||
                lower.contains("feeling pressure") || lower.contains("lots of pressure") || lower.contains("under pressure") ||
                lower.contains("feeling lot of pressure") || lower.contains("drained") || lower.contains("fatigue") ||
                lower.contains("anxiety") || lower.contains("thak gaya") || lower.contains("thak gayi") ||
                lower.contains("bahut pressure") || lower.contains("stressed out")) {
            return AgentIntent.STRESS_EXPRESSION;
        }

        // 7. Check Balance
        if (lower.contains("balance") || lower.contains("remaining") || lower.contains("how many days") ||
                lower.contains("how many leave") || lower.contains("how much leave") ||
                lower.contains("leaves do i have") || lower.contains("leaves left") ||
                lower.contains("leaves i have") || lower.contains("leave quota") || lower.contains("available leaves") ||
                lower.contains("kitni chutti") || lower.contains("kitne leave") || lower.contains("chutti bachi")) {
            return AgentIntent.CHECK_BALANCE;
        }

        // 7.5 Raise Ticket / Support
        if (lower.startsWith("raise ticket") || lower.startsWith("create ticket") || lower.contains("raise a ticket") ||
                lower.contains("raise a support ticket") || lower.contains("open ticket") || lower.contains("submit ticket") ||
                lower.contains("create a ticket") || lower.contains("raise ticket for")) {
            return AgentIntent.RAISE_TICKET;
        }

        // 8. Check Policy
        if (lower.contains("policy") || lower.contains("policies") || lower.contains("rule") ||
                lower.contains("can i combine") || lower.contains("eligib") || lower.contains("cutoff") ||
                lower.contains("deadline") || lower.contains("combination") || lower.contains("niyam")) {
            return AgentIntent.CHECK_POLICY;
        }

        // 9. View Leaves / Status
        if (lower.contains("my leaves") || lower.contains("leave status") || lower.contains("history") ||
                lower.contains("my applications") || lower.contains("my requests") ||
                lower.contains("meri chutti") || lower.contains("meri leaves")) {
            return AgentIntent.VIEW_LEAVES;
        }

        // 10. Ticket inquiry
        if (lower.contains("ticket") || lower.contains("support desk") || lower.contains("helpdesk") ||
                lower.contains("technical error") || lower.contains("support")) {
            return AgentIntent.TICKET_INQUIRY;
        }

        // 12. Wellbeing inquiry & Weekly Wellbeing Status
        if (lower.contains("amenit") || lower.contains("gym") || lower.contains("doctor") ||
                lower.contains("physician") || lower.contains("psychologist") || lower.contains("counsel") ||
                lower.contains("massage") || lower.contains("recliner") || lower.contains("yoga") ||
                lower.contains("zumba") || lower.contains("hospital") || lower.contains("resort") ||
                lower.contains("vacation") || lower.contains("hotel") || lower.contains("sick room") ||
                lower.contains("rest room") || lower.contains("take rest") || lower.contains("benefits") ||
                lower.contains("lawyer") || lower.contains("legal advisor") || lower.contains("law advisor") ||
                lower.contains("weekly wellbeing") || lower.contains("wellbeing status") || lower.contains("wellness status") ||
                lower.contains("health status") || lower.contains("weekly health") || lower.contains("weekly status") ||
                lower.contains("wellbeing report") || lower.contains("volunteer") || lower.contains("volunteering") ||
                lower.contains("csr") || lower.contains("community group") || lower.contains("recreation") ||
                lower.contains("snooker") || lower.contains("carrom") || lower.contains("chess") ||
                lower.contains("table tennis") || lower.contains("games") || lower.contains("insurance") ||
                lower.contains("opd") || lower.contains("medical bills") || lower.contains("consultation")) {
            return AgentIntent.WELLBEING_INQUIRY;
        }

        // 13. Greeting / Help
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey") ||
                lower.equals("namaste") || lower.equals("hola") ||
                lower.contains("help") || lower.contains("who are you") ||
                lower.contains("what can you do") || lower.contains("start")) {
            return AgentIntent.GREETING;
        }

        // Date-only fallback → treat as APPLY_LEAVE
        if (extractDates(message)[0] != null) {
            return AgentIntent.APPLY_LEAVE;
        }

        // === TIER 2: Fuzzy keyword matching (Levenshtein distance ≤ 2) ===
        AgentIntent fuzzyResult = fuzzyIntentMatch(lower);
        if (fuzzyResult != AgentIntent.UNKNOWN) {
            return fuzzyResult;
        }

        // === TIER 3: Context-aware inference ===
        if (context != null && !context.isExpired()) {
            AgentIntent contextResult = contextAwareResolve(lower, context);
            if (contextResult != AgentIntent.UNKNOWN) {
                return contextResult;
            }
        }

        return AgentIntent.UNKNOWN;
    }

    /**
     * TIER 2: Fuzzy intent matching using Levenshtein distance.
     * Checks if any token in the user's message fuzzy-matches known intent keywords.
     */
    private AgentIntent fuzzyIntentMatch(String lower) {
        // Check if message contains fuzzy matches for cancel + leave
        if (hasFuzzyKeyword(lower, CANCEL_KEYWORDS, 2) &&
                (hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2) || lower.length() < 12)) {
            return AgentIntent.CANCEL_LEAVE;
        }

        // Check fuzzy edit + leave
        if (hasFuzzyKeyword(lower, EDIT_KEYWORDS, 2) &&
                (hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2) || extractLeaveType(lower) != null || extractDates(lower)[0] != null)) {
            return AgentIntent.EDIT_LEAVE;
        }

        // Check fuzzy apply + leave
        if (hasFuzzyKeyword(lower, APPLY_KEYWORDS, 2) &&
                (hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2) || extractLeaveType(lower) != null)) {
            return AgentIntent.APPLY_LEAVE;
        }

        // Standalone fuzzy apply (short messages like "aply" or "aplly")
        String[] tokens = lower.split("\\s+");
        if (tokens.length <= 3) {
            for (String token : tokens) {
                String clean = token.replaceAll("[^a-z]", "");
                if (clean.length() >= 3) {
                    if (FuzzyMatcher.findBestMatch(clean, APPLY_KEYWORDS, 2).isPresent() &&
                            hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2)) {
                        return AgentIntent.APPLY_LEAVE;
                    }
                }
            }
        }

        // Check fuzzy balance
        if (hasFuzzyKeyword(lower, BALANCE_KEYWORDS, 2) &&
                (hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2) || lower.contains("how") || lower.contains("check") || lower.contains("my"))) {
            return AgentIntent.CHECK_BALANCE;
        }

        // Standalone fuzzy balance
        if (hasFuzzyKeyword(lower, BALANCE_KEYWORDS, 1)) {
            return AgentIntent.CHECK_BALANCE;
        }

        // Fuzzy leave type mentioned alone (potential apply)
        if (extractLeaveTypeFuzzy(lower) != null && hasFuzzyKeyword(lower, LEAVE_KEYWORDS, 2)) {
            return AgentIntent.APPLY_LEAVE;
        }

        // Fuzzy greeting
        if (tokens.length <= 2 && hasFuzzyKeyword(lower, GREETING_KEYWORDS, 1)) {
            return AgentIntent.GREETING;
        }

        return AgentIntent.UNKNOWN;
    }

    /**
     * TIER 3: Context-aware inference based on previous conversation turn.
     */
    private AgentIntent contextAwareResolve(String lower, ConversationContext context) {
        ConversationContext.PromptType lastPrompt = context.getLastPromptType();

        switch (lastPrompt) {
            case LEAVE_TYPE:
                // Agent just asked "which type of leave?" — try parsing as leave type
                if (extractLeaveType(lower) != null || extractLeaveTypeFuzzy(lower) != null) {
                    return AgentIntent.APPLY_LEAVE;
                }
                break;

            case HALF_DAY_SESSION:
                // Agent asked "morning or afternoon?" — resolve session
                if (extractHalfDaySession(lower) != null) {
                    return AgentIntent.APPLY_LEAVE;
                }
                break;

            case DATE:
                // Agent just asked for dates — try parsing as date
                if (extractDates(lower)[0] != null) {
                    return AgentIntent.APPLY_LEAVE;
                }
                break;

            case REASON:
                // Agent just asked for reason — free text should be handled as the reason for the leave application
                // Unless the user asks to check balance or view policy explicitly
                if (lower.contains("check balance") || lower.contains("my balance") || lower.contains("leave balance")) {
                    return AgentIntent.CHECK_BALANCE;
                }
                if (lower.contains("policy") || lower.contains("policies") || lower.contains("leave rule")) {
                    return AgentIntent.CHECK_POLICY;
                }
                return AgentIntent.APPLY_LEAVE;

            case CONFIRMATION:
                // Agent asked "shall I submit?" — yes/no/confirm/deny
                if (isAffirmative(lower) || isNegative(lower)) {
                    return context.getLastIntent() != null ? context.getLastIntent() : AgentIntent.APPLY_LEAVE;
                }
                break;

            case CANCEL_SELECT:
                // Agent asked "which leave to cancel?" — any response continues cancel flow
                return AgentIntent.CANCEL_LEAVE;

            case EDIT_SELECT:
            case EDIT_FIELD:
                // Agent asked "which leave to edit?" or "what to update?" — continues edit flow
                return AgentIntent.EDIT_LEAVE;

            case STRESS_FOLLOWUP:
                // Agent offered stress intervention — user responding
                if (context.getLastIntent() != null) {
                    return context.getLastIntent();
                }
                break;

            default:
                break;
        }

        return AgentIntent.UNKNOWN;
    }

    // ----- Leave Type Extraction -----

    public LeaveType extractLeaveType(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase();

        if (lower.contains("casual")) return LeaveType.CASUAL;
        if (lower.contains("sick")) return LeaveType.SICK;
        if (lower.contains("paid") || lower.contains("privilege") || lower.contains("annual") || lower.contains("earned")) return LeaveType.PAID;
        if (lower.contains("lop") || lower.contains("loss of pay") || lower.contains("lass of pay") || lower.contains("unpaid")) return LeaveType.LOP;
        if (lower.contains("wfh") || lower.contains("work from home") || lower.contains("ghar se kaam") || lower.contains("ghar se")) return LeaveType.WFH;
        if (lower.contains("maternity")) return LeaveType.MATERNITY;
        if (lower.contains("volunteering") || lower.contains("volunteer")) return LeaveType.VOLUNTEERING;

        return null;
    }

    /**
     * Fuzzy leave type extraction — catches misspellings like "casul", "sik", "payed" etc.
     */
    public LeaveType extractLeaveTypeFuzzy(String message) {
        if (message == null) return null;

        // Try exact first
        LeaveType exact = extractLeaveType(message);
        if (exact != null) return exact;

        String lower = message.toLowerCase();
        String[] tokens = lower.split("\\s+");

        for (String token : tokens) {
            String clean = token.replaceAll("[^a-z]", "");
            if (clean.length() < 2) continue;

            // Fuzzy match against each leave type's known names (distance ≤ 2)
            if (FuzzyMatcher.findBestMatch(clean, CASUAL_NAMES, 2).isPresent()) return LeaveType.CASUAL;
            if (FuzzyMatcher.findBestMatch(clean, SICK_NAMES, 1).isPresent()) return LeaveType.SICK;
            if (FuzzyMatcher.findBestMatch(clean, PAID_NAMES, 2).isPresent()) return LeaveType.PAID;
            if (FuzzyMatcher.findBestMatch(clean, LOP_NAMES, 1).isPresent()) return LeaveType.LOP;
            if (FuzzyMatcher.findBestMatch(clean, WFH_NAMES, 1).isPresent()) return LeaveType.WFH;
            if (FuzzyMatcher.findBestMatch(clean, MATERNITY_NAMES, 2).isPresent()) return LeaveType.MATERNITY;
            if (FuzzyMatcher.findBestMatch(clean, VOLUNTEERING_NAMES, 3).isPresent()) return LeaveType.VOLUNTEERING;
        }

        // Check multi-word phrases
        if (FuzzyMatcher.fuzzyContainsPhrase(lower, "loss of pay", 1)) return LeaveType.LOP;
        if (FuzzyMatcher.fuzzyContainsPhrase(lower, "work from home", 1)) return LeaveType.WFH;

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

    // ----- Date Extraction (with Hindi support + yyyy-mm-dd display) -----

    public LocalDate[] extractDates(String message) {
        if (message == null) return new LocalDate[]{null, null};
        String lower = message.toLowerCase().trim();

        LocalDate firstDate = null;
        LocalDate secondDate = null;

        // 1. Try ISO date regex (2026-09-08, 2026/09/08, 2026.09.08)
        Matcher isoMatcher = Pattern.compile("(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2})").matcher(message);
        if (isoMatcher.find()) {
            firstDate = parseFlexibleIsoDate(isoMatcher.group(1));
            if (isoMatcher.find()) {
                secondDate = parseFlexibleIsoDate(isoMatcher.group(1));
            }
        }

        // 2. Try DD/MM/YYYY or MM/DD/YYYY regex (08-09-2026, 8/9/2026, 8.9.2026)
        if (firstDate == null) {
            Matcher dmyMatcher = Pattern.compile("(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})").matcher(message);
            if (dmyMatcher.find()) {
                firstDate = parseFlexibleDmyDate(dmyMatcher.group(1));
                if (dmyMatcher.find()) {
                    secondDate = parseFlexibleDmyDate(dmyMatcher.group(1));
                }
            }
        }

        // 2.5 Try natural month names (e.g., "8th Sep", "Sep 8", "10 September 2026", "8 to 12 September")
        if (firstDate == null) {
            LocalDate[] monthDates = extractMonthBasedDates(lower);
            if (monthDates[0] != null) {
                firstDate = monthDates[0];
                secondDate = monthDates[1];
            }
        }

        // 2.6 Try natural day numbers without month (e.g., "from 8th to 9th", "8th to 9th", "from 9th to 11th", "from 8 to 12", "8th", "on 9th", "9th se 11th")
        if (firstDate == null) {
            LocalDate[] dayDates = extractDayNumberDates(lower);
            if (dayDates[0] != null) {
                firstDate = dayDates[0];
                secondDate = dayDates[1];
            }
        }

        // 3. Natural relative expressions (English + Hindi)
        if (firstDate == null) {
            Matcher inDaysMatcher = Pattern.compile("in\\s+(\\d+)\\s*days?").matcher(lower);
            if (inDaysMatcher.find()) {
                try {
                    int daysAhead = Integer.parseInt(inDaysMatcher.group(1));
                    firstDate = LocalDate.now().plusDays(daysAhead);
                } catch (NumberFormatException ignored) {}
            } else if (lower.contains("day after tomorrow") || lower.contains("parso") || lower.contains("parson") || lower.contains("parson ko")) {
                firstDate = LocalDate.now().plusDays(2);
            } else if (lower.contains("tomorrow") || lower.contains("tommrrow") || lower.contains("tomorow") ||
                    lower.contains("tommorow") || lower.contains("tmrw") || lower.contains("tomrrow") ||
                    lower.contains("tomorrw") || lower.contains("tmmrw") || lower.contains("2morrow") ||
                    lower.contains("kal") || lower.contains("kl")) {
                // "kal" = Hindi for "tomorrow" (or yesterday, but in leave context we treat as tomorrow)
                firstDate = LocalDate.now().plusDays(1);
            } else if (lower.contains("yesterday") || (lower.contains("kal") && (lower.contains("bita") || lower.contains("beeta")))) {
                // "kal beeta" = Hindi for "yesterday passed"
                firstDate = LocalDate.now().minusDays(1);
            } else if (lower.contains("today") || lower.equals("aaj") || lower.contains("aaj ") || lower.contains(" aaj")) {
                firstDate = LocalDate.now();
            } else if (lower.contains("next week") || lower.contains("agle hafte") || lower.contains("agle week")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                secondDate = firstDate.plusDays(4); // Mon-Fri
            } else if (lower.contains("this friday") || lower.contains("is friday") || lower.contains("coming friday")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
            } else if (lower.contains("this monday") || lower.contains("coming monday") || lower.contains("next monday") || lower.contains("monday") ||
                    lower.contains("somwar") || lower.contains("somvaar")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            } else if (lower.contains("tuesday") || lower.contains("mangalwar") || lower.contains("mangalvaar")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
            } else if (lower.contains("wednesday") || lower.contains("budhwar") || lower.contains("budhvaar")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
            } else if (lower.contains("thursday") || lower.contains("guruwar") || lower.contains("veervar") || lower.contains("guruvaar")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY));
            } else if (lower.contains("friday") || lower.contains("shukrawar") || lower.contains("shukravaar")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
            } else if (lower.contains("end of week") || lower.contains("week end") || lower.contains("weekend")) {
                firstDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
            }

            // Fuzzy match for tomorrow/today if still null
            if (firstDate == null) {
                if (FuzzyMatcher.fuzzyContains(lower, "tomorrow", 2)) {
                    firstDate = LocalDate.now().plusDays(1);
                } else if (FuzzyMatcher.fuzzyContains(lower, "yesterday", 2)) {
                    firstDate = LocalDate.now().minusDays(1);
                } else if (FuzzyMatcher.fuzzyContains(lower, "today", 1)) {
                    firstDate = LocalDate.now();
                }
            }
        }

        // 4. Check for day counts / durations
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
                // Hindi: "do din" (2 days), "teen din" (3 days), "ek din" (1 day)
                Matcher hindiDays = Pattern.compile("(?:ek|do|teen|char|paanch|panch|chhe|saat)\\s*(?:din|days?)").matcher(lower);
                if (hindiDays.find()) {
                    int count = hindiWordToNumber(hindiDays.group().split("\\s+")[0]);
                    if (count > 0) {
                        if (firstDate == null) firstDate = LocalDate.now().plusDays(1);
                        secondDate = firstDate.plusDays(count - 1);
                    }
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
        }

        if (firstDate != null && secondDate == null) {
            Matcher toWeekday = Pattern.compile("(?:to|-|till|until|se)\\s+(?:this\\s+|coming\\s+|next\\s+)?(monday|tuesday|wednesday|thursday|friday|somwar|mangalwar|budhwar|guruwar|shukrawar)").matcher(lower);
            if (toWeekday.find()) {
                DayOfWeek dow = parseDayOfWeek(toWeekday.group(1));
                if (dow != null) {
                    LocalDate target = firstDate.with(TemporalAdjusters.nextOrSame(dow));
                    if (target.isBefore(firstDate)) {
                        target = target.plusWeeks(1);
                    }
                    secondDate = target;
                }
            }
        }

        if (firstDate != null && secondDate == null) {
            secondDate = firstDate;
        }

        return new LocalDate[]{firstDate, secondDate};
    }

    /**
     * Extracts dates specified by day numbers without an explicit month name, e.g.:
     * "from 8th to 9th", "8th to 9th", "from 9th to 11th", "from 8 to 12", "8th", "on 9th", "9th se 11th", "8-10"
     * Resolves to current month if day >= today's day-of-month, or next month if day has already elapsed.
     */
    private LocalDate[] extractDayNumberDates(String lower) {
        // Exclude if it specifies a duration of days (e.g. "for 3 days", "2 to 3 days")
        if (lower.matches(".*\\b\\d{1,2}\\s*(?:to|-)\\s*\\d{1,2}\\s*(?:work)?days?\\b.*")) {
            return new LocalDate[]{null, null};
        }

        LocalDate today = LocalDate.now();

        // 1. Match day range: e.g. "from 8th to 9th", "8th to 9th", "from 9th to 11th", "from 8 to 12", "8th - 11th", "9th se 11th"
        Pattern rangePat = Pattern.compile(
                "\\b(?:(?:from|between|apply from|leave from)\\s+(?:the\\s+)?)?(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:to|-|till|until|se|and)\\s*(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b"
        );
        Matcher rangeMatcher = rangePat.matcher(lower);
        if (rangeMatcher.find()) {
            try {
                int d1 = Integer.parseInt(rangeMatcher.group(1));
                int d2 = Integer.parseInt(rangeMatcher.group(2));
                if (d1 >= 1 && d1 <= 31 && d2 >= 1 && d2 <= 31) {
                    LocalDate first = resolveDayOfMonth(d1, today);
                    LocalDate second;
                    if (d2 >= d1) {
                        second = first.withDayOfMonth(Math.min(d2, first.lengthOfMonth()));
                    } else {
                        LocalDate nextMonth = first.plusMonths(1);
                        second = nextMonth.withDayOfMonth(Math.min(d2, nextMonth.lengthOfMonth()));
                    }
                    return new LocalDate[]{first, second};
                }
            } catch (Exception ignored) {}
        }

        // 2. Match single ordinal day: e.g. "from 8th", "on 8th", "8th", "on the 9th", "9th", "from 15th"
        Pattern singlePat = Pattern.compile(
                "\\b(?:on|from|starting|for)?\\s*(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)\\b"
        );
        Matcher singleMatcher = singlePat.matcher(lower);
        if (singleMatcher.find()) {
            try {
                int d = Integer.parseInt(singleMatcher.group(1));
                if (d >= 1 && d <= 31) {
                    LocalDate first = resolveDayOfMonth(d, today);
                    return new LocalDate[]{first, null};
                }
            } catch (Exception ignored) {}
        }

        return new LocalDate[]{null, null};
    }

    private LocalDate resolveDayOfMonth(int day, LocalDate today) {
        LocalDate targetMonth = (day >= today.getDayOfMonth()) ? today : today.plusMonths(1);
        int validDay = Math.min(day, targetMonth.lengthOfMonth());
        return targetMonth.withDayOfMonth(validDay);
    }

    private DayOfWeek parseDayOfWeek(String str) {
        if (str == null) return null;
        String s = str.toLowerCase();
        if (s.contains("mon") || s.contains("somwar")) return DayOfWeek.MONDAY;
        if (s.contains("tue") || s.contains("mangalwar")) return DayOfWeek.TUESDAY;
        if (s.contains("wed") || s.contains("budhwar")) return DayOfWeek.WEDNESDAY;
        if (s.contains("thu") || s.contains("guruwar") || s.contains("veervar")) return DayOfWeek.THURSDAY;
        if (s.contains("fri") || s.contains("shukrawar")) return DayOfWeek.FRIDAY;
        return null;
    }

    /**
     * Extracts dates specified with natural month names, e.g. "8th Sep", "Sep 8", "8 to 12 September".
     */
    private LocalDate[] extractMonthBasedDates(String lower) {
        LocalDate first = null;
        LocalDate second = null;

        // Check range like "8 to 12 September" or "8th - 10th Sep 2026"
        Pattern rangePat = Pattern.compile(
                "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:to|-|till|until)\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?([a-zA-Z]+)(?:\\s+(\\d{4}))?\\b"
        );
        Matcher rangeMatcher = rangePat.matcher(lower);
        if (rangeMatcher.find()) {
            int d1 = Integer.parseInt(rangeMatcher.group(1));
            int d2 = Integer.parseInt(rangeMatcher.group(2));
            int m = parseMonthName(rangeMatcher.group(3));
            if (m > 0 && d1 >= 1 && d1 <= 31 && d2 >= 1 && d2 <= 31) {
                int y = rangeMatcher.group(4) != null ? Integer.parseInt(rangeMatcher.group(4)) : LocalDate.now().getYear();
                first = resolveYearForDate(y, m, d1, rangeMatcher.group(4) != null);
                second = resolveYearForDate(y, m, d2, rangeMatcher.group(4) != null);
                return new LocalDate[]{first, second};
            }
        }

        // Match individual dates with month names: "8th Sep", "Sep 8th", "8 September", "September 8"
        Pattern monthDatePat = Pattern.compile(
                "\\b(?:(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?([a-zA-Z]+)(?:\\s+(\\d{4}))?|([a-zA-Z]+)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?)\\b"
        );
        Matcher mMatcher = monthDatePat.matcher(lower);
        while (mMatcher.find()) {
            int d = 0;
            int m = 0;
            Integer y = null;

            if (mMatcher.group(1) != null) {
                d = Integer.parseInt(mMatcher.group(1));
                m = parseMonthName(mMatcher.group(2));
                if (mMatcher.group(3) != null) y = Integer.parseInt(mMatcher.group(3));
            } else if (mMatcher.group(4) != null) {
                m = parseMonthName(mMatcher.group(4));
                d = Integer.parseInt(mMatcher.group(5));
                if (mMatcher.group(6) != null) y = Integer.parseInt(mMatcher.group(6));
            }

            if (m > 0 && d >= 1 && d <= 31) {
                int resolvedYear = y != null ? y : LocalDate.now().getYear();
                LocalDate parsed = resolveYearForDate(resolvedYear, m, d, y != null);
                if (first == null) {
                    first = parsed;
                } else if (second == null) {
                    second = parsed;
                    break;
                }
            }
        }

        return new LocalDate[]{first, second};
    }

    private static LocalDate resolveYearForDate(int year, int month, int day, boolean yearExplicitlyProvided) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            // If year wasn't explicitly provided and date is in the past, roll to next year
            if (!yearExplicitlyProvided && date.isBefore(LocalDate.now())) {
                date = date.plusYears(1);
            }
            return date;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseMonthName(String name) {
        if (name == null) return 0;
        String m = name.toLowerCase();
        if (m.startsWith("jan")) return 1;
        if (m.startsWith("feb")) return 2;
        if (m.startsWith("mar")) return 3;
        if (m.startsWith("apr")) return 4;
        if (m.equals("may")) return 5;
        if (m.startsWith("jun")) return 6;
        if (m.startsWith("jul")) return 7;
        if (m.startsWith("aug")) return 8;
        if (m.startsWith("sep")) return 9;
        if (m.startsWith("oct")) return 10;
        if (m.startsWith("nov")) return 11;
        if (m.startsWith("dec")) return 12;
        return 0;
    }

    private int hindiWordToNumber(String word) {
        if (word == null) return 0;
        switch (word.toLowerCase()) {
            case "ek": return 1;
            case "do": return 2;
            case "teen": return 3;
            case "char": return 4;
            case "paanch": case "panch": return 5;
            case "chhe": return 6;
            case "saat": return 7;
            default: return 0;
        }
    }

    /**
     * Formats a date as yyyy-MM-dd for consistent display.
     */
    public static String formatDate(LocalDate date) {
        if (date == null) return "N/A";
        return date.toString(); // LocalDate.toString() returns yyyy-MM-dd
    }

    private LocalDate parseFlexibleIsoDate(String s) {
        try {
            String sanitized = s.replace('/', '-').replace('.', '-');
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
            String sanitized = s.replace('/', '-').replace('.', '-');
            String[] parts = sanitized.split("-");
            int d = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            if (y < 100) y += 2000;
            // Handle MM-DD-YYYY transposition if m > 12 and d <= 12
            if (d <= 12 && m > 12) {
                int temp = d;
                d = m;
                m = temp;
            }
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    // ----- Other extractors -----

    public boolean extractHalfDay(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("half day") || lower.contains("half-day") || lower.contains("0.5 day") ||
                lower.contains("aadha din") || lower.contains("adha din");
    }

    public String extractHalfDaySession(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase().trim();
        if (lower.contains("first half") || lower.contains("1st half") || lower.contains("morning") ||
                lower.contains("subah") || lower.contains("pehle half") || lower.equals("am") ||
                lower.contains(" am ") || lower.endsWith(" am") || lower.startsWith("am ")) {
            return "FIRST_HALF";
        }
        if (lower.contains("second half") || lower.contains("2nd half") || lower.contains("afternoon") ||
                lower.contains("dopahar") || lower.contains("doosre half") || lower.contains("post lunch") ||
                lower.contains("evening") || lower.equals("pm") || lower.contains(" pm ") ||
                lower.endsWith(" pm") || lower.startsWith("pm ")) {
            return "SECOND_HALF";
        }
        return null;
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

    public String extractRawReason(String message) {
        if (message == null || message.trim().isEmpty()) return null;
        String lower = message.toLowerCase().trim();

        // If user is just stating leave type, date, or relative day expression, not a reason:
        if (isDateOrLeaveExpression(lower) ||
                lower.equals("yes") || lower.equals("no") || lower.equals("skip") ||
                lower.equals("confirm") || lower.equals("proceed")) {
            return null;
        }

        // Check explicit markers
        String[] markers = {"because ", "due to ", "reason: ", "reason is ", "as i am ", "as i have ", "kyunki ", "isliye "};
        for (String marker : markers) {
            int idx = lower.indexOf(marker);
            if (idx != -1) {
                String sub = message.substring(idx + marker.length()).trim();
                sub = sub.replaceAll("(?i)\\b(starting|from|tomorrow|tomoroow|tommorow|tomorow|tmrw|next week|on|date|for \\d+ days?).*$", "").trim();
                if (!sub.isEmpty() && sub.length() > 2 && !isDateOrLeaveExpression(sub)) {
                    return sub;
                }
            }
        }

        // Check "for <reason>" where it's not a duration, date, or leave type
        Matcher forMatcher = Pattern.compile("(?i)\\bfor\\s+(?!\\d+\\s*(?:work)?days?|\\d{1,2}(?:st|nd|rd|th)?|tomorrow|tomoroow|tommorow|tomorow|tmrw|yesterday|today|next|back\\s*date|paid|sick|casual|wfh|lop|half\\s*day|(?:an?|another|new|some)?\\s*leave)([a-zA-Z\\s]{3,})").matcher(message);
        if (forMatcher.find()) {
            String sub = forMatcher.group(1).trim();
            sub = sub.replaceAll("(?i)\\b(starting|from|tomorrow|tomoroow|tommorow|tomorow|tmrw|next week|on|date).*$", "").trim();
            if (!sub.isEmpty() && sub.length() > 2 && !isDateOrLeaveExpression(sub)) {
                return sub;
            }
        }

        // Check common symptom / reason keywords
        String[] keywords = {
                "fever", "headache", "migraine", "flu", "cold", "cough", "food poisoning",
                "stomach pain", "stomach infection", "unwell", "illness",
                "family function", "sister's wedding", "brother's wedding", "wedding", "marriage",
                "personal work", "urgent work", "personal commitment", "family emergency",
                "going to native", "native place", "travel", "travelling",
                "vacation", "burnout", "stress", "fatigue", "exhaustion",
                // Hindi reason keywords
                "tabiyat kharab", "bimar", "bukhar", "dard", "shaadi", "function"
        };
        for (String kw : keywords) {
            if (lower.contains(kw) && !isDateOrLeaveExpression(kw)) {
                return kw;
            }
        }

        return null;
    }

    /**
     * Returns true if the string is purely a date, relative day, duration, or leave type keyword,
     * ensuring that expressions like "for tomorrow", "for tomoroow", or "half day" are never
     * falsely captured as leave reasons.
     */
    public boolean isDateOrLeaveExpression(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase().trim();
        if (lower.equals("tomorrow") || lower.equals("yesterday") || lower.equals("today") ||
                lower.equals("tomoroow") || lower.equals("tommorow") || lower.equals("tomorow") || lower.equals("tmrw") ||
                lower.equals("kal") || lower.equals("parso") || lower.equals("aaj") ||
                lower.equals("next week") || lower.equals("half day") || lower.equals("full day") ||
                lower.equals("leave") || lower.equals("chutti") || lower.equals("chhutti")) {
            return true;
        }
        if (lower.matches("^(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|somwar|mangalwar|budhwar|guruwar|shukrawar)$")) {
            return true;
        }
        if (extractLeaveType(lower) != null && lower.split("\\s+").length <= 2) {
            return true;
        }
        if (lower.matches("^\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}$") || lower.matches("^\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}$")) {
            return true;
        }
        if (lower.matches("^(?:from\\s+)?(?:the\\s+)?\\d{1,2}(?:st|nd|rd|th)?(?:\\s*(?:to|-|till|until|se|and)\\s*(?:the\\s+)?\\d{1,2}(?:st|nd|rd|th)?)?$")) {
            return true;
        }
        if (lower.matches("^\\d+\\s*(?:work)?days?$") || lower.matches("^(?:ek|do|teen|char|paanch)\\s*din$")) {
            return true;
        }
        if (FuzzyMatcher.fuzzyMatch(lower, "tomorrow", 2) || FuzzyMatcher.fuzzyMatch(lower, "yesterday", 2)) {
            return true;
        }
        return false;
    }

    // ----- Utility methods -----

    private boolean hasFuzzyKeyword(String text, List<String> keywords, int maxDistance) {
        return FuzzyMatcher.fuzzyContainsAny(text, keywords, maxDistance) != null;
    }

    public static boolean isAffirmative(String lower) {
        return lower.equals("yes") || lower.equals("yeah") || lower.equals("yep") || lower.equals("yup") ||
                lower.equals("sure") || lower.equals("ok") || lower.equals("okay") || lower.equals("confirm") ||
                lower.equals("proceed") || lower.equals("go ahead") || lower.equals("submit") ||
                lower.equals("do it") || lower.equals("haan") || lower.equals("ha") ||
                lower.equals("theek hai") || lower.equals("thik hai") || lower.equals("kar do") ||
                lower.startsWith("yes") || lower.startsWith("confirm") || lower.startsWith("proceed");
    }

    public static boolean isNegative(String lower) {
        return lower.equals("no") || lower.equals("nope") || lower.equals("nah") || lower.equals("cancel") ||
                lower.equals("stop") || lower.equals("abort") || lower.equals("never mind") || lower.equals("nevermind") ||
                lower.equals("nahi") || lower.equals("mat karo") || lower.equals("rehne do") ||
                lower.startsWith("no ");
    }
}
