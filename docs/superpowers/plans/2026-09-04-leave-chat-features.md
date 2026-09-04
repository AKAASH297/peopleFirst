# Leave Chat Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Managers approve/reject team leave inside Kura chat, same-day double-booking is rejected everywhere, half-day leaves work end-to-end with a sick-room nudge, and volunteering leave enrolls employees in CSR groups with banner opt-in.

**Architecture:** Grounded-services-first: overlap guard lives in `LeaveValidator` so web, agent, and CLI all inherit it; approvals reuse `ApprovalService` + `ApprovalValidator` behind the existing confirm-gate; half-day session flows through the existing `halfDaySession` column; volunteering enrollment is one small JPA entity surfaced through the agent's post-apply step.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Data JPA (H2 dev), JUnit 5 + Mockito + MockMvc. No new dependencies.

## Global Constraints

- Identity is never trusted from the client: every new agent path resolves the user via `CurrentUserProvider.getCurrentUser()` first (already the pattern at the top of `AgentService.processMessage`).
- The LLM never writes to the DB: approvals and enrollments execute only through service calls (`ApprovalService`, new `VolunteeringService`) behind the existing `pendingActions` confirm-gate semantics.
- `AgentChatResponseDto` JSON contract is frozen: `reply`, `intent`, `actionExecuted`, `actionName`, `actionData`, `wellbeingSuggestions`, `quickReplies`.
- Java for Maven runs: `JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.19_10`, Maven `%LOCALAPPDATA%\Apache\apache-maven-3.9.16\bin`.
- Work from worktree `E:\Inter OS Data\halping\peopleFirst\agent-work`, branch `feat/ai-agent-openai-compatible` — do NOT switch branches.
- TDD every task: failing test first, minimal implementation, full focused-suite green, commit per task.
- User-facing copy: provider-neutral Kura voice, no emoji overload; sick room is **Floor 6, Room 7** (this supersedes any older room copy in agent replies and the half-day sick rule text).
- Never log or return API keys or base URLs.

---

## File Structure

- Modify: `backend/.../leave/validator/LeaveValidator.java` — add overlap guard (Task 1).
- Modify: `backend/.../leave/repository/LeaveRequestRepository.java` — add range query (Task 1).
- Modify: `backend/.../leave/service/LeaveService.java` — call guard in `applyLeave` + `editLeave` (Task 1).
- Modify: `backend/.../agent/intent/AgentIntent.java` — add `APPROVE_LEAVES` (Task 2).
- Modify: `backend/.../agent/intent/IntentParser.java` — approval keywords + ordinal parse + half-day session parse (Tasks 2, 3).
- Modify: `backend/.../agent/tools/AgentTool.java` + `AgentToolCatalog.java` — `approve_leave`, `reject_leave` schemas + `halfDaySession` param + volunteering chips text (Tasks 2, 3, 4).
- Modify: `backend/.../agent/service/AgentService.java` — approval handlers, half-day session step, sick-room nudge, volunteering post-apply step (Tasks 2, 3, 4).
- Modify: `backend/.../wellbeing/rules/HalfDaySickLeaveWellbeingRule.java` — Floor 6, Room 7 (Task 3).
- Create: `backend/.../volunteering/entity/VolunteeringEnrollment.java`, `repository/VolunteeringEnrollmentRepository.java`, `service/VolunteeringService.java` (Task 4).
- Tests: `LeaveServiceTest` (extend), new `ApprovalAgentTest` (or extend `AgentServiceAgenticTest`), `LeaveValidatorTest` (new or extend `PolicyValidatorTest` file area — check what exists first), `VolunteeringServiceTest` (new).

---

### Task 1: Same-day double-booking guard (backend, all channels inherit)

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/leave/repository/LeaveRequestRepository.java`
- Modify: `backend/src/main/java/com/peoplefirst/leave/validator/LeaveValidator.java`
- Modify: `backend/src/main/java/com/peoplefirst/leave/service/LeaveService.java`
- Test: `backend/src/test/java/com/peoplefirst/leave/LeaveServiceTest.java` (extend; check existing setup style first)

**Interfaces:**
- Consumes: `LeaveStatus` enum (`PENDING`, `APPROVED`, `CANCELLED`, `REJECTED`, `RETURNED` — verify names in `leave/entity/LeaveStatus.java` before coding).
- Produces: `LeaveValidator.validateNoOverlap(UUID userId, LocalDate start, LocalDate end, boolean isHalfDay, String halfDaySession, UUID excludeLeaveId)` throwing `PolicyViolationException` (the same exception type the agent already catches and renders).

- [ ] **Step 1: Write the failing tests** (append to `LeaveServiceTest`, following its existing mock/setup style)

```java
@Test
@DisplayName("Second same-day apply after approval is rejected")
void testSameDayDoubleBookingRejected() {
    // Seed: existing APPROVED full-day leave for user on 2026-10-05 (use the test's existing user/factory style)
    // Attempt: applyLeave full-day Sick on 2026-10-05
    // Expect: assertThrows(PolicyViolationException.class, ...)
}

@Test
@DisplayName("Overlapping PENDING leave blocks a second apply")
void testPendingOverlapRejected() { /* same shape, existing leave status PENDING */ }

@Test
@DisplayName("Complementary half-day sessions on the same day are allowed")
void testComplementaryHalfDaysAllowed() {
    // Existing APPROVED FIRST_HALF sick on 2026-10-06; apply SECOND_HALF same day -> succeeds (PENDING)
}

@Test
@DisplayName("Same half-day session twice is rejected")
void testSameHalfDaySessionRejected() { /* existing FIRST_HALF approved; apply FIRST_HALF -> throws */ }

@Test
@DisplayName("Cancelled/rejected leaves do not block")
void testCancelledLeavesDoNotBlock() { /* existing CANCELLED full-day; apply same day -> succeeds */ }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk17.0.19_10"; $env:Path = "$env:LOCALAPPDATA\Apache\apache-maven-3.9.16\bin;" + $env:Path; mvn -B test -Dtest=LeaveServiceTest` (in `backend/`)
Expected: FAIL — the 3 rejection tests fail (no guard exists); the 2 allowance tests pass.

- [ ] **Step 3: Minimal implementation**

Repository — add exactly:

```java
List<LeaveRequest> findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
    UUID userId, List<LeaveStatus> statuses, LocalDate end, LocalDate start);
```

(`startDate <= end && endDate >= start` overlap predicate; statuses passed in are `PENDING, APPROVED` only.)

Validator — add exactly:

```java
public void validateNoOverlap(UUID userId, LocalDate start, LocalDate end,
        boolean isHalfDay, String halfDaySession, UUID excludeLeaveId) {
    List<LeaveRequest> clashes = leaveRequestRepository
        .findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            userId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), end, start);
    for (LeaveRequest c : clashes) {
        if (excludeLeaveId != null && excludeLeaveId.equals(c.getId())) continue;
        if (isHalfDay && c.isHalfDay()
                && halfDaySession != null && !halfDaySession.equals(c.getHalfDaySession())
                && start.equals(c.getStartDate())) continue; // complementary halves share the day
        throw new PolicyViolationException(
            "This overlaps your " + c.getLeaveType().getDisplayName() + " (" +
            c.getStartDate() + " to " + c.getEndDate() + ", " + c.getStatus() + ").");
    }
}
```

`LeaveValidator` needs a `LeaveRequestRepository` constructor arg — check its current constructor first; if it has none (static-style 31-line class), add `@Service`/`@Component` + constructor injection and update `LeaveService`'s instantiation accordingly (check how `LeaveService` obtains `LeaveValidator` today — `new` vs injected — and follow it).

`LeaveService.applyLeave`: call `leaveValidator.validateNoOverlap(user.getId(), dto.getStartDate(), dto.getEndDate(), dto.isHalfDay(), dto.getHalfDaySession(), null)` immediately after the existing `calculateTotalDays` call. `LeaveService.editLeave`: same call with the edited leave's own id as `excludeLeaveId` (check `editLeave`'s id variable name first).

- [ ] **Step 4: Run tests to verify they pass**

Run: same `mvn -B test -Dtest=LeaveServiceTest` command
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/leave/validator/LeaveValidator.java backend/src/main/java/com/peoplefirst/leave/repository/LeaveRequestRepository.java backend/src/main/java/com/peoplefirst/leave/service/LeaveService.java backend/src/test/java/com/peoplefirst/leave/LeaveServiceTest.java
git commit -m "fix(leave): reject overlapping same-day and range double-booking"
```

---

### Task 2: Manager approvals inside Kura chat

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/agent/intent/AgentIntent.java` (add `APPROVE_LEAVES`)
- Modify: `backend/src/main/java/com/peoplefirst/agent/intent/IntentParser.java` (keywords + ordinal)
- Modify: `backend/src/main/java/com/peoplefirst/agent/tools/AgentTool.java` + `AgentToolCatalog.java` (2 schemas)
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java` (handlers; inject `ApprovalService`)
- Test: `backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java` (extend with mocked `ApprovalService`)

**Interfaces:**
- Consumes: `ApprovalService.approveLeave(UUID, ApprovalActionDto, User)`, `rejectLeave(...)` (same signature), `getPendingApprovals(User)`; `ApprovalActionDto` has `comment` (check exact setter — `setComment` — before coding); `AgentTool.fromName` + `AgentToolCatalog.getSchemas()` from the prior tools task.
- Produces: nothing for later tasks.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void managerListsPendingApprovalsInChat() {
    // genAiClient.isConfigured() -> false (rule path); manager user whose getPendingApprovals returns 2 PENDING leaves
    // message "show pending approvals" -> reply lists both with numbers 1 and 2, intent APPROVE_LEAVES
}

@Test
void managerApprovesByNumberWithExistingAuthority() {
    // "approve 1" with pending list stubbed; ApprovalService.approveLeave returns approved DTO
    // -> reply confirms approval, actionName APPROVE_LEAVE, actionExecuted true
}

@Test
void nonManagerGetsEmptyApprovalsMessage() {
    // employee user, getPendingApprovals -> []; "show pending approvals" -> "no pending approvals" reply, actionExecuted false
}

@Test
void agenticApproveGoesThroughConfirmGate() {
    // isConfigured() true; chatWithTools returns tool_call approve_leave with {"leaveId":"<uuid>","comment":"ok"}
    // -> proposal reply (NOT executed): actionExecuted false; verifyNoInteractions(approvalService)
    // then "yes" -> approvalService.approveLeave called once with the uuid
}
```

Check `AgentService`'s constructor before writing: it currently takes 8 args (intentParser, currentUserProvider, leaveService, leaveBalanceService, policyService, wellbeingService, leaveMapper, genAiClient) — adding `ApprovalService` makes 9; update the test's construction call accordingly and check for any other `new AgentService(` call sites (production code has one — Spring injects; tests: `AgentServiceAgenticTest` + possibly `PeopleFirstIntegrationTest` which uses Spring context and needs no change).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B test -Dtest=AgentServiceAgenticTest`
Expected: FAIL — compilation errors (`APPROVE_LEAVES` / `approve_leave` / `ApprovalService` wiring do not exist).

- [ ] **Step 3: Minimal implementation**

`AgentIntent`: append `APPROVE_LEAVES` to the enum (check exact enum syntax first).
`IntentParser.parseIntent`: match approval BEFORE the generic apply branch (managers type "approve ..."): keywords `approv|reject|send back|pending approvals|my team.*leave|team.*request`. Add `public int parseApprovalOrdinal(String message)` returning 1-based number from `^(approve|reject|send back)\s+(\d+)\s*$` else -1 (return -1 when absent; the handler lists pendings then).
`AgentTool`: add `APPROVE_LEAVE("approve_leave")`, `REJECT_LEAVE("reject_leave")`. Catalog schemas: `approve_leave` requires `leaveId` (string UUID) + optional `comment`; `reject_leave` identical shape.
`AgentService`: inject `ApprovalService approvalService` (constructor + field). Rule path: `case APPROVE_LEAVES: return handleApprovalInbox(message, user);` where the handler calls `approvalService.getPendingApprovals(user)`; empty → "You have no pending approvals." reply (intent `APPROVE_LEAVES`, `actionExecuted=false`); non-empty → numbered list `1. <Name> — <Type> <start> to <end> (<days>d, <id-short>)` + "Reply `approve 1` or `reject 2`." If `parseApprovalOrdinal` ≥ 1 and index valid → execute immediately via `approvalService.approveLeave(id, dtoWithComment("Approved via Kura"), user)` / `rejectLeave(...)` (comment `"Rejected via Kura"`), reply confirms, `actionExecuted=true`, `actionName=APPROVE_LEAVE`/`REJECT_LEAVE`, `actionData` = returned DTO, and also attach `getPostActionQuickReplies(user)`. Invalid index → re-list with "I couldn't find #N." `AccessDeniedException` from the validator → catch and reply "You can only act on your direct reportees' requests." (check `ApprovalValidator`'s exact message first and mirror its meaning, not necessarily its text).
Agentic path: `approve_leave`/`reject_leave` tool calls NEVER execute directly — store `PendingAgentAction(toolName, argumentsJson)` and return the standard proposal (`actionExecuted=false`, quick replies `Yes, confirm` / `No, discard`), exactly like `apply_leave`. On `yes`: parse `leaveId` via `UUID.fromString`, call the matching service method with comment from args (default `"Approved via Kura"` / `"Rejected via Kura"`), remove entry, confirm. On `no`: discard with `getPostActionQuickReplies(user)`. Malformed UUID → discard entry, reply "That leave ID didn't look valid — please try again."
`buildSystemContext`: append for MANAGER/ADMIN roles only: `- As a <Role> you can review team leave: ask me for "pending approvals" and approve or reject by number.` (check how role is read — `user.getRole()` / `isContractor()` — before coding).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B test -Dtest=AgentServiceAgenticTest`
Expected: PASS. Then `mvn -B test` (whole suite, no clean — the running server locks the jar) green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/intent/AgentIntent.java backend/src/main/java/com/peoplefirst/agent/intent/IntentParser.java backend/src/main/java/com/peoplefirst/agent/tools/AgentTool.java backend/src/main/java/com/peoplefirst/agent/tools/AgentToolCatalog.java backend/src/main/java/com/peoplefirst/agent/service/AgentService.java backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java
git commit -m "feat(agent): manager leave approvals inside chat"
```

---

### Task 3: Half-day leaves end-to-end + sick-room nudge (Floor 6, Room 7)

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/agent/intent/IntentParser.java` (session parse)
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java` (session step + nudge)
- Modify: `backend/src/main/java/com/peoplefirst/agent/tools/AgentToolCatalog.java` (`halfDaySession` param)
- Modify: `backend/src/main/java/com/peoplefirst/wellbeing/rules/HalfDaySickLeaveWellbeingRule.java` (room text)
- Modify: `backend/src/main/java/com/peoplefirst/leave/validator/LeaveValidator.java` (session enum check)
- Test: extend `AgentServiceAgenticTest` + `LeaveServiceTest`

**Interfaces:**
- Consumes: Task 1's overlap guard (complementary sessions allowed — half-day tests in Task 1 already cover the service side); `PendingLeaveDraft` (has `halfDaySession` field, currently never set from chat).
- Produces: `halfDaySession` (`FIRST_HALF`/`SECOND_HALF`) populated on every agent half-day apply.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void halfDaySessionIsAskedWhenMissing() {
    // isConfigured() false; "apply half day sick leave tomorrow" (no morning/afternoon word)
    // -> reply asks "First half or second half?" (assert contains "First half"), intent APPLY_LEAVE, draft kept
}

@Test
void morningMapsToFirstHalfAndApplies() {
    // "apply half day sick leave tomorrow morning" -> executes (mock leaveService.applyLeave returns DTO with totalDays 0.5)
    // capture CreateLeaveRequestDto: assert isHalfDay true, halfDaySession "FIRST_HALF"
}

@Test
void afternoonMapsToSecondHalf() { /* "tomorrow afternoon" -> halfDaySession SECOND_HALF */ }

@Test
void halfDaySickNudgesSickRoomFloor6Room7() {
    // after successful half-day SICK apply, reply or wellbeingSuggestions mention Floor 6 and Room 7
    // (mock wellbeingService.evaluateLeaveWellbeing to return empty; assert on reply text containing "Floor 6" and "Room 7")
}

@Test
void invalidHalfDaySessionRejectedAtService() {
    // LeaveServiceTest style: applyLeave with halfDay=true, halfDaySession="MIDDLE" -> PolicyViolationException
}
```

Check `continueLeaveDraft`'s current slot order (type → dates) before writing: the session question slots between dates-known and execute.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B test -Dtest='AgentServiceAgenticTest,LeaveServiceTest'`
Expected: FAIL — session never asked (single prompt jumps to execute with default `FIRST_HALF`), no nudge text, no enum check.

- [ ] **Step 3: Minimal implementation**

`IntentParser`: add `public String extractHalfDaySession(String message)` — lower contains `morning|first half|1st half|before noon` → `"FIRST_HALF"`; `afternoon|second half|2nd half|after noon` → `"SECOND_HALF"`; else `null`.
`AgentService.handleApplyLeave` + `continueLeaveDraft`: populate `draft.setHalfDaySession(extractHalfDaySession(message))` alongside the existing `setHalfDay`. After type+dates are known, if `draft.isHalfDay() && draft.getHalfDaySession() == null` → store draft in `userDrafts` and return "Got it — **first half** (morning) or **second half** (afternoon)?" with quick replies `List.of("First half (morning)", "Second half (afternoon)", "Cancel")`, intent `APPLY_LEAVE`. Next turn resolves via the same extractor (the `continueLeaveDraft` yes/confirm branch must not swallow "first half" — check its prefix matching first; session words contain neither yes/confirm, safe).
`executeLeaveApplication`: keep the existing `FIRST_HALF` default ONLY when session still null (defensive), but it should now always be set for agent half-days. After a successful half-day SICK apply, append to the reply: `\n\n🛏️ If you're unwell and nearby, you can rest in the office sick room (**Floor 6, Room 7**) before heading home — just let reception know.` (exact text).
`AgentToolCatalog` `apply_leave` schema: add optional `halfDaySession` string (`FIRST_HALF|SECOND_HALF`); `buildDraftFromArguments` (check exact name — `buildDraftFromArguments`) must copy it into the draft (verify current code reads only `halfDay` bool and extend it).
`HalfDaySickLeaveWellbeingRule`: change the sick-room location text to Floor 6, Room 7 (read the file first; change only the location fragment, keep trigger logic).
`LeaveValidator.calculateTotalDays` (the half-day branch): add `if (isHalfDay && halfDaySession == null)` — signature has no session param today, so instead validate in `applyLeave`/`editLeave` right after the overlap call: `if (dto.isHalfDay() && !"FIRST_HALF".equals(dto.getHalfDaySession()) && !"SECOND_HALF".equals(dto.getHalfDaySession())) throw new PolicyViolationException("Half-day leave needs a session: FIRST_HALF or SECOND_HALF.");` — put it in `LeaveService` (not the validator) to avoid signature churn; check `dto.isHalfDay()`/`getHalfDaySession()` getter names first.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B test -Dtest='AgentServiceAgenticTest,LeaveServiceTest'`
Expected: PASS. Then `mvn -B test` whole suite green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/intent/IntentParser.java backend/src/main/java/com/peoplefirst/agent/service/AgentService.java backend/src/main/java/com/peoplefirst/agent/tools/AgentToolCatalog.java backend/src/main/java/com/peoplefirst/wellbeing/rules/HalfDaySickLeaveWellbeingRule.java backend/src/main/java/com/peoplefirst/leave/service/LeaveService.java backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java backend/src/test/java/com/peoplefirst/leave/LeaveServiceTest.java
git commit -m "feat(agent): half-day sessions with sick-room nudge"
```

---

### Task 4: Volunteering leave enrollment + CSR groups + banner

**Files:**
- Create: `backend/src/main/java/com/peoplefirst/volunteering/entity/VolunteeringEnrollment.java`
- Create: `backend/src/main/java/com/peoplefirst/volunteering/repository/VolunteeringEnrollmentRepository.java`
- Create: `backend/src/main/java/com/peoplefirst/volunteering/service/VolunteeringService.java`
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java` (chips + post-apply CSR step)
- Test: `backend/src/test/java/com/peoplefirst/volunteering/VolunteeringServiceTest.java` (new)

**Interfaces:**
- Consumes: `VolunteeringWellbeingRule` group names (read them first — expected: Green Earth Afforestation, Tech Literacy for Youth, Animal Welfare Network + Food Bank + Paws & Care) — reuse the exact strings; `LeaveResponseDto.getId()` for linkage.
- Produces: `VolunteeringService.enroll(UUID userId, String groupName, UUID leaveRequestId, boolean bannerOptIn)` returning the saved entity.

- [ ] **Step 1: Write the failing tests**

```java
class VolunteeringServiceTest {
    // Use a real repository if the project test style is @DataJpaTest (check an existing test first),
    // else Mockito the repository and assert the service sets fields + calls save.
    @Test
    void enrollPersistsGroupAndBannerChoice() {
        VolunteeringEnrollment e = service.enroll(userId, "Green Earth Afforestation", leaveId, true);
        assertEquals("Green Earth Afforestation", e.getGroupName());
        assertTrue(e.isBannerOptIn());
        assertEquals(leaveId, e.getLeaveRequestId());
    }

    @Test
    void blankGroupNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.enroll(userId, "  ", leaveId, false));
    }
}
```

Agent-level test (append to `AgentServiceAgenticTest`):

```java
@Test
void volunteeringApplyOffersCsrGroups() {
    // isConfigured() false; approved volunteering DTO from mocked leaveService; wellbeingService returns the real rule's suggestion (mock it)
    // reply contains a CSR group name AND asks about joining AND mentions the intranet banner
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B test -Dtest='VolunteeringServiceTest,AgentServiceAgenticTest'`
Expected: FAIL — compilation errors (entity/service do not exist).

- [ ] **Step 3: Minimal implementation**

Entity (follow an existing entity's annotations — check `SupportTicket` for `@Entity`/`@Id`/`@GeneratedValue` style first):

```java
@Entity
@Table(name = "volunteering_enrollments")
public class VolunteeringEnrollment {
    @Id @GeneratedValue private UUID id;
    private UUID userId;
    private String groupName;
    private UUID leaveRequestId;
    private boolean bannerOptIn;
    private LocalDateTime createdAt;
    // getters + setters (check whether entities use Lombok — if not, write them out)
}
```

Repository: `public interface VolunteeringEnrollmentRepository extends JpaRepository<VolunteeringEnrollment, UUID> { List<VolunteeringEnrollment> findByUserId(UUID userId); }` (check `JpaRepository` import style in an existing repository first).
Service:

```java
@Service
public class VolunteeringService {
    private final VolunteeringEnrollmentRepository repository;
    public VolunteeringService(VolunteeringEnrollmentRepository repository) { this.repository = repository; }
    public VolunteeringEnrollment enroll(UUID userId, String groupName, UUID leaveRequestId, boolean bannerOptIn) {
        if (groupName == null || groupName.trim().isEmpty()) throw new IllegalArgumentException("Volunteering group must be named.");
        VolunteeringEnrollment e = new VolunteeringEnrollment();
        e.setUserId(userId); e.setGroupName(groupName.trim()); e.setLeaveRequestId(leaveRequestId);
        e.setBannerOptIn(bannerOptIn); e.setCreatedAt(LocalDateTime.now());
        return repository.save(e);
    }
}
```

`AgentService`: inject `VolunteeringService`. (a) Add `"Volunteering Leave"` to employee chips in `getEligibleLeaveTypeChips` + mention in `promptForLeaveType` role note (contractors unchanged). (b) In `executeLeaveApplication` success branch, if `leaveType == LeaveType.VOLUNTEERING`: append `\n\n🌱 **CSR chapters you can join:** <group1>, <group2>, <group3>, <group4>.\nWant me to enroll you in one — and feature you on the company intranet banner? Reply with the group name (add "and feature me" for the banner).` using the exact group strings from the rule; quick replies = the group names + `"No thanks"`. (c) New conversation state: reuse the existing `userDrafts`-adjacent pattern minimally — store a `PendingVolunteeringSignup(UUID leaveRequestId)` in a `Map<UUID, PendingVolunteeringSignup> volunteeringSignups` (same 15-min expiry style as drafts); next user message: if it names a group (case-insensitive contains against the 4 names) → `volunteeringService.enroll(user.getId(), matchedName, signup.getLeaveRequestId(), message contains "feature")`, remove entry, confirm "You're enrolled in **<name>**! <If banner: You'll be featured on the intranet banner. reach out to CSR at https://csr.peoplefirst.internal/enroll for onboarding.>" — read the rule's actionUrl first and reuse it instead of inventing a URL. If "no thanks"/"cancel" → remove entry, "No problem — enjoy your volunteering leave!". If intent parses as an explicit other intent (reuse the existing `isExplicitOtherIntent` boolean), drop the signup and route normally.
`AgentToolCatalog` `apply_leave` description: extend the leave-type enumeration text to include Volunteering (read current description first; append, don't rewrite).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B test -Dtest='VolunteeringServiceTest,AgentServiceAgenticTest'`
Expected: PASS. Then `mvn -B test` whole suite green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/volunteering backend/src/main/java/com/peoplefirst/agent/service/AgentService.java backend/src/test/java/com/peoplefirst/volunteering/VolunteeringServiceTest.java backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java
git commit -m "feat(agent): volunteering enrollment with CSR groups and banner opt-in"
```

---

### Task 5: Full verification + live check + PR push

**Files:** none (verification only).

- [ ] **Step 1: Run the whole suite**

Run: `mvn -B test` (in `backend/`; no `clean` — the running server locks the jar)
Expected: BUILD SUCCESS, 43+ new tests all green (count = 43 prior + Task 1:5 + Task 2:4 + Task 3:5 + Task 4:3 = 60).

- [ ] **Step 2: Rebuild + restart + live-verify** (servers are currently STOPPED; start fresh)

```powershell
mvn -B package -DskipTests
$env:GENAI_API_KEY="<key from local .env>"; $env:OPENAI_BASE_URL="<url from local .env>"; $env:GENAI_PROVIDER="openai_compatible"; $env:GENAI_MODEL="<model from local .env>"; $env:GENAI_ENABLED="true"
# Start backend java -jar + frontend http.server per SETUP_INSTALL_LOG.md, then:
# 1. POST /api/auth/login employee1/WEB -> 200
# 2. agent chat "show pending approvals" as manager1 -> lists direct reportees' pendings (seed one via employee apply first if empty)
# 3. agent chat approve flow with yes-confirm -> APPROVED
# 4. double apply same day -> PolicyViolationException surfaces in chat reply ("overlaps")
# 5. half-day sick chat -> session question -> morning -> Floor 6 Room 7 nudge present
# 6. volunteering chat -> CSR groups + banner question -> enroll
```

Read the actual key/URL/model from the local git-ignored `.env` at runtime — never write them anywhere else. If `.env` is empty (keys were scrubbed), run checks 2–6 against the rule fallback instead and note agentic checks as skipped.

- [ ] **Step 3: Push branch (PR updates automatically)**

```bash
git push fork feat/ai-agent-openai-compatible
```

PR: https://github.com/rishi0714/peopleFirst/pull/1 (from `AKAASH297` fork — auto-updates on push; report the new commit range).

## Self-Review

- Spec coverage: manager approve/reject in chat (Task 2, incl. send-back? — spec asked approve; `sendBackLeave` exists but chat exposes approve/reject only. Gap accepted: send-back stays web-only; noted here deliberately). Double-booking incl. half-day interplay (Tasks 1+3). Sick-room Floor 6 Room 7 in both agent reply and rule text (Task 3). Volunteering groups + enroll + banner (Task 4). Agentic confirm-gate for approve/reject (Task 2) consistent with prior apply/cancel behavior. Rule-path + agentic-path both covered for approvals.
- Type consistency: `ApprovalActionDto` comment setter, `LeaveStatus` names, `UUID.fromString`, `PendingAgentAction` reuse — all flagged as check-first items in the tasks because the plan author could not see those files; implementers must verify, not guess.
- No placeholders: every step names files, commands, exact strings; check-first items are explicit, not vague.
