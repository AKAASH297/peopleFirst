# AI Agent (OpenAI-Compatible) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Kura from keyword-regex replies into a tool-calling agent that talks to any OpenAI-compatible endpoint (configurable base URL), while keeping every leave/policy execution inside the existing grounded Java services.

**Architecture:** `GenAiClient` becomes transport-only (base URL + provider + chat-with-tools call). New `agent/tools/` package declares 7 function schemas backed 1:1 by existing services. `AgentService.processMessage()` becomes an agentic loop (system prompt from `buildSystemContext()` + history + tools, max 5 turns) with a confirm-gate on writes and byte-for-byte rule fallback when unconfigured. `IntentParser` and all handlers stay untouched as the fallback path.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Jackson (already on classpath), JUnit 5 + Mockito (via `spring-boot-starter-test`), JDK `com.sun.net.httpserver.HttpServer` for endpoint stubs in tests. No new dependencies.

## Global Constraints

- Identity is never trusted from the client: `AgentService.processMessage()` keeps resolving the user via `CurrentUserProvider.getCurrentUser()` first, before any LLM call.
- The LLM never writes to the DB: state changes happen only through `LeaveService.applyLeave()` / `LeaveService.cancelLeave()` inside tool execution.
- `AgentChatResponseDto` JSON contract is frozen: `reply`, `intent`, `actionExecuted`, `actionName`, `actionData`, `wellbeingSuggestions`, `quickReplies` — frontend renders all of these.
- Java version for Maven runs: `JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.19_10` (default `java` on PATH is 25; Spring Boot 3.2.5 needs 17).
- Maven binary: `%LOCALAPPDATA%\Apache\apache-maven-3.9.16\bin\mvn.cmd`.
- Rollout is flag-only for everyone: `GENAI_ENABLED` on/off, no per-role gating.
- Never log or return API keys or full base URLs: `/api/agent/status` reports booleans and mode only.
- TDD: failing test first for every behavior, then minimal implementation, then commit per task on branch `feat/ai-agent-openai-compatible`.

---

## File Structure

- Modify: `backend/src/main/java/com/peoplefirst/agent/client/GenAiClient.java` — add `base-url` + `provider` config, `chatWithTools()` + JSON-degrade call, setters mirroring existing `setApiKey`/`setModel`.
- Modify: `backend/src/main/resources/application.yml` — `genai.base-url`, `genai.provider` keys.
- Modify: `.env.example` — document `OPENAI_BASE_URL`, `GENAI_PROVIDER`, keep both key slots.
- Create: `backend/src/main/java/com/peoplefirst/agent/tools/AgentTool.java` — enum of 7 tools, each with OpenAI function-schema JSON.
- Create: `backend/src/main/java/com/peoplefirst/agent/tools/AgentToolCatalog.java` — `getSchemas()` list builder used in the chat request.
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java` — history store, agentic loop, confirm-gate, DTO mapping; delete `PendingLeaveDraft` + draft methods only after loop tests pass.
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java#getAgentStatus` — add `agentMode`, `genAiProvider`, `genAiEndpointReachable` (boolean).
- Create tests: `backend/src/test/java/com/peoplefirst/agent/GenAiClientTest.java`, `backend/src/test/java/com/peoplefirst/agent/AgentToolCatalogTest.java`, `backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java`.
- Extend: `backend/src/test/java/com/peoplefirst/PeopleFirstIntegrationTest.java` — agentic chat test with stub endpoint (only if existing file has an agent section pattern to follow; otherwise put it in `AgentServiceAgenticTest` as a Spring test — do not restructure the existing file).

---

### Task 1: OpenAI-compatible transport in GenAiClient

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/agent/client/GenAiClient.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`
- Test: `backend/src/test/java/com/peoplefirst/agent/GenAiClientTest.java`

**Interfaces:**
- Consumes: existing `generateContent(String, String)`, `isConfigured()`, `getModel()`, `setApiKey(String)`, `setModel(String)`.
- Produces (used by Task 3): `public Optional<String> chatWithTools(String systemInstruction, List<Map<String, String>> history, List<Map<String, Object>> tools)` returning the raw assistant message JSON (`{"content": "...", "tool_calls": [...]}` serialized as String); `public Optional<IntentSlots> parseIntentJson(String systemInstruction, String userMessage)` for the JSON-degrade path where `IntentSlots` is `record IntentSlots(String intent, Map<String, String> slots)`; `public void setBaseUrl(String)`, `public void setProvider(String)`, `public void setEnabled(boolean)` mirroring the existing setter style.

- [ ] **Step 1: Create branch and failing test for base-URL override**

```bash
git checkout -b feat/ai-agent-openai-compatible
```

```java
package com.peoplefirst.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.client.GenAiClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenAiClientTest {

    private HttpServer stub;
    private String lastPath;
    private String lastBody;
    private int stubStatus = 200;
    private String stubBody = "{\"choices\": [{\"message\": {\"content\": \"hello\"}}]}";
    private GenAiClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = HttpServer.create(new InetSocketAddress(0), 0);
        stub.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        stub.start();
        client = new GenAiClient(new ObjectMapper());
        client.setEnabled(true);
        client.setApiKey("test-key-not-sk");
        client.setProvider("openai_compatible");
        client.setModel("test-model");
        client.setBaseUrl("http://localhost:" + stub.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    @Test
    void postsToConfiguredBaseUrlChatCompletions() {
        Optional<String> reply = client.generateContent("sys", "hi");
        assertTrue(reply.isPresent());
        assertEquals("/chat/completions", lastPath);
        assertTrue(lastBody.contains("\"model\":\"test-model\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk17.0.19_10"; $env:Path = "$env:LOCALAPPDATA\Apache\apache-maven-3.9.16\bin;" + $env:Path; mvn -B test -Dtest=GenAiClientTest` (in `backend/`)
Expected: FAIL — compilation error, `setEnabled`/`setProvider`/`setBaseUrl` do not exist.

- [ ] **Step 3: Minimal transport implementation**

In `GenAiClient.java`, replace the hardcoded OpenAI URL and key-sniffing with explicit config (keep the Gemini path exactly as-is):

```java
@Value("${app.genai.base-url:${OPENAI_BASE_URL:https://api.openai.com/v1}}}")
private String openAiBaseUrl;

@Value("${app.genai.provider:${GENAI_PROVIDER:auto}}")
private String provider;

public void setBaseUrl(String baseUrl) {
    this.openAiBaseUrl = baseUrl;
}

public void setProvider(String provider) {
    this.provider = provider;
}

public void setEnabled(boolean enabled) {
    this.enabled = enabled;
}
```

Routing rule in `generateContent`: `if ("openai_compatible".equalsIgnoreCase(provider.trim()))` → OpenAI path; `else if ("gemini".equalsIgnoreCase(...))` → Gemini path; else `auto` keeps today's behavior (key starts with `sk-` or model mentions `gpt`/`3.5` → OpenAI, otherwise Gemini). In `callOpenAiApi`, build the URL as `openAiBaseUrl.trim().replaceAll("/+$", "") + "/chat/completions"` instead of the hardcoded string. Add `chatWithTools` (same POST shape plus `"tools"` array and `"tool_choice": "auto"`, returns `choices[0].message` node serialized via `objectMapper.writeValueAsString`) and `parseIntentJson` (POST with `"response_format": {"type": "json_object"}`, system instruction demands exactly `{"intent": "<ONE_OF_10>", "slots": {"leaveType": "", "startDate": "", "endDate": "", "confirmed": ""}}`, parse into the `IntentSlots` record; empty Optional on any failure). All non-200 responses and exceptions log-warn and return `Optional.empty()`, matching existing style.

`application.yml` genai block becomes:

```yaml
  genai:
    enabled: ${GENAI_ENABLED:true}
    provider: ${GENAI_PROVIDER:auto}
    api-key: ${GEMINI_API_KEY:${OPENAI_API_KEY:${GENAI_API_KEY:}}}
    model: ${GENAI_MODEL:${GEMINI_MODEL:gemini-1.5-pro}}
    endpoint: https://generativelanguage.googleapis.com/v1beta/models
    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
```

`.env.example` gains (keep every existing line, append):

```
# OpenAI-compatible endpoint (OpenRouter, LiteLLM, Azure, Ollama/vLLM, or OpenAI itself).
# Leave the default until you have a real URL — without a reachable endpoint Kura runs rule-based.
OPENAI_BASE_URL=https://api.openai.com/v1
# Provider routing: auto | openai_compatible | gemini
GENAI_PROVIDER=auto
```

- [ ] **Step 4: Run test to verify it passes**

Run: same `mvn -B test -Dtest=GenAiClientTest` command as Step 2
Expected: PASS (2 tests if you also assert the trailing-slash trim: base URL `http://localhost:PORT/` still posts to `/chat/completions` — add that assertion in the same test method, not a new file).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/client/GenAiClient.java backend/src/main/resources/application.yml .env.example backend/src/test/java/com/peoplefirst/agent/GenAiClientTest.java
git commit -m "feat(agent): OpenAI-compatible base URL and provider config in GenAiClient"
```

---

### Task 2: Tool catalog (schemas only, no execution)

**Files:**
- Create: `backend/src/main/java/com/peoplefirst/agent/tools/AgentTool.java`
- Create: `backend/src/main/java/com/peoplefirst/agent/tools/AgentToolCatalog.java`
- Test: `backend/src/test/java/com/peoplefirst/agent/AgentToolCatalogTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 (pure schema data).
- Produces (used by Task 3): `AgentTool` enum with `getName()` returning one of `check_balance`, `apply_leave`, `cancel_leave`, `view_leaves`, `get_policy`, `wellbeing`, `ticket_info`; `AgentToolCatalog.getSchemas()` returning `List<Map<String, Object>>` where each entry is `{"type": "function", "function": {"name": ..., "description": ..., "parameters": {"type": "object", "properties": {...}, "required": [...]}}}`.

- [ ] **Step 1: Write the failing test**

```java
package com.peoplefirst.agent;

import com.peoplefirst.agent.tools.AgentTool;
import com.peoplefirst.agent.tools.AgentToolCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolCatalogTest {

    @Test
    void exposesSevenGroundedToolsWithStrictSchemas() {
        List<Map<String, Object>> schemas = new AgentToolCatalog().getSchemas();
        assertEquals(7, schemas.size());
        List<String> names = schemas.stream()
                .map(s -> (String) ((Map<String, Object>) s.get("function")).get("name"))
                .toList();
        assertTrue(names.containsAll(List.of("check_balance", "apply_leave", "cancel_leave",
                "view_leaves", "get_policy", "wellbeing", "ticket_info")));
        Map<String, Object> apply = schemas.stream()
                .filter(s -> ((Map<String, Object>) s.get("function")).get("name").equals("apply_leave"))
                .findFirst().orElseThrow();
        Map<String, Object> params = (Map<String, Object>) ((Map<String, Object>) apply.get("function")).get("parameters");
        assertTrue(((List<String>) params.get("required")).containsAll(List.of("leaveType", "startDate", "endDate")));
    }

    @Test
    void enumResolvesByName() {
        assertEquals(AgentTool.APPLY_LEAVE, AgentTool.fromName("apply_leave"));
        assertEquals(AgentTool.WELLBEING, AgentTool.fromName("wellbeing"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B test -Dtest=AgentToolCatalogTest` (in `backend/`, with `JAVA_HOME` + Maven `PATH` set as in Task 1)
Expected: FAIL with compilation errors (`package com.peoplefirst.agent.tools does not exist`).

- [ ] **Step 3: Write minimal implementation**

`AgentTool.java`:

```java
package com.peoplefirst.agent.tools;

public enum AgentTool {
    CHECK_BALANCE("check_balance"),
    APPLY_LEAVE("apply_leave"),
    CANCEL_LEAVE("cancel_leave"),
    VIEW_LEAVES("view_leaves"),
    CHECK_POLICY("get_policy"),
    WELLBEING_INQUIRY("wellbeing"),
    TICKET_INQUIRY("ticket_info");

    private final String name;

    AgentTool(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AgentTool fromName(String name) {
        for (AgentTool tool : values()) {
            if (tool.name.equals(name)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Unknown agent tool: " + name);
    }
}
```

`AgentToolCatalog.java` builds the schema list with a private helper `schema(AgentTool tool, String description, Map<String, Object> properties, List<String> required)` returning `Map.of("type", "function", "function", Map.of("name", tool.getName(), "description", description, "parameters", Map.of("type", "object", "properties", properties, "required", required)))`. Parameter shapes: `check_balance` has optional `leaveType` string; `apply_leave` requires `leaveType`, `startDate`, `endDate` (ISO `YYYY-MM-DD` strings) with optional `halfDay` boolean and `reason` string; `cancel_leave` takes no properties (`Map.of()`, required `List.of()`); `view_leaves` no properties; `get_policy` optional `topic` string; `wellbeing` optional `topic` string (`hospitals|resorts|amenities`); `ticket_info` no properties. Descriptions state grounding facts the model must respect (e.g. apply_leave: "Contractors: Sick/Paid/LOP only. Paid needs 3+ days notice. Sick > 2 days needs a certificate. Never invent balances; call check_balance first when unsure.").

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B test -Dtest=AgentToolCatalogTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/tools backend/src/test/java/com/peoplefirst/agent/AgentToolCatalogTest.java
git commit -m "feat(agent): tool catalog with seven grounded function schemas"
```

---

### Task 3: Agentic loop in AgentService (history, tools, confirm-gate, fallback)

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java`
- Test: `backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java`

**Interfaces:**
- Consumes: Task 1 `chatWithTools`, `parseIntentJson`/`IntentSlots`, `isConfigured`; Task 2 `AgentTool.fromName`, `AgentToolCatalog.getSchemas`; existing `processMessage` callers (`AgentController.chat`) unchanged.
- Produces (used by Task 4): `processMessage` behavior — agentic path when configured, identical rule path otherwise; `getAgentStatus` gains keys consumed by Task 4 assertions.

- [ ] **Step 1: Write the failing tests (mocked LLM, real services mocked too)**

```java
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

        AgentChatRequestDto request = new AgentChatRequestDto();
        request.setMessage("how many sick days do I have left?");
        request.setConversationId("test-conv-1");
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
        AgentChatRequestDto request = new AgentChatRequestDto();
        request.setMessage("hello");
        AgentChatResponseDto response = agentService.processMessage(request);
        assertTrue(response.getReply().contains("Kura"));
    }
}
```

Note: check `AgentChatRequestDto` for the exact `setMessage`/`setConversationId` setter names before running; if `conversationId` has no setter, add `private String conversationId` + getter/setter to the DTO as part of this task (it is currently ignored server-side per the codebase map) and assert history isolation per conversation id in a third test.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B test -Dtest=AgentServiceAgenticTest`
Expected: FAIL — `chatWithTools` does not exist on the mock's type (Mockito strict stubs complain about unnecessary stubbing or the call falls through returning null → NullPointerException in the loop). Either failure mode counts: no agentic path exists.

- [ ] **Step 3: Minimal loop implementation**

In `AgentService`, at the top of `processMessage` after identity resolution and message trim: `if (genAiClient.isConfigured()) { return processAgentic(message, request.getConversationId(), user); }` — everything below stays byte-identical as the rule fallback. New private members: `private final Map<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();` keyed by `conversationId` (default `"default"` when blank), each list capped at 20 entries (drop oldest pairs first); `private final Map<UUID, PendingAgentAction> pendingActions = new ConcurrentHashMap<>();` where `PendingAgentAction` is a private static class with `toolName`, `argumentsJson`, `createdAt` and the same 15-minute expiry style as `PendingLeaveDraft`.

`processAgentic` algorithm (write exactly this, no extras): append `{"role":"user","content":message}` to history; turn loop max 5 — call `genAiClient.chatWithTools(buildSystemContext(user) + "\nToday is " + LocalDate.now() + ".", historySnapshot, new AgentToolCatalog().getSchemas())`; empty Optional → break to rule fallback (`return processRuleBased(request, user)` — extract the current method body below the new branch into `processRuleBased` unchanged); parse message JSON; append assistant message to history; for each `tool_calls` entry resolve `AgentTool.fromName` (unknown name → append `{"role":"tool","tool_call_id":id,"content":"Unknown tool"}` and continue); `check_balance` → run existing `handleCheckBalance` logic refactored to return data (extract balance-list building from `handleCheckBalance` into `fetchBalances(user)` used by both, do not change its reply text); `view_leaves`/`get_policy`/`wellbeing` → call the corresponding existing handler methods and serialize their `actionData` to compact JSON via `new ObjectMapper().writeValueAsString` for the tool result message; `apply_leave`/`cancel_leave` → do NOT execute: store `PendingAgentAction`, return `AgentChatResponseDto` with reply `"I've prepared <summary>. Reply **yes** to confirm or **no** to discard."`, intent `APPLY_LEAVE`/`CANCEL_LEAVE`, quick replies `List.of("Yes, confirm", "No, discard")`, `actionExecuted=false`. Confirmation handling at the top of `processAgentic` before the loop: if `pendingActions` has an entry for the user and lower message is `yes|confirm|proceed` → execute via existing `executeLeaveApplication`-equivalent path for the stored arguments (build a `PendingLeaveDraft` from the stored JSON and call `executeLeaveApplication(draft, user)`, remove entry); if `no|cancel|discard` → remove entry, return cancellation reply with `getPostActionQuickReplies(user)`. After the loop with no tool calls, final assistant `content` becomes the reply with intent `UNKNOWN`, `actionExecuted=false`, quick replies `List.of("Check my balances", "Apply for leave", "Company leave policies", "Campus amenities")`, and if any tool ran this turn set intent/actionName/actionData from the last tool result (reuse the same action names as handlers: `CHECK_BALANCE`, `VIEW_LEAVES`, `CHECK_POLICY`, `APPLY_LEAVE`, `CANCEL_LEAVE`). JSON-degrade: if the endpoint ever throws/returns empty on a tools call twice in one turn, call `parseIntentJson` once and route the returned intent through the existing handler switch (reuse `processRuleBased` with the parsed intent by constructing the equivalent message path — simplest: call `processRuleBased(request, user)` and let regex handle it, since regex is the offline equivalent; do not build a second switch).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -B test -Dtest=AgentServiceAgenticTest`
Expected: PASS. Then run the full suite: `mvn -B clean test` — all 26 existing tests plus the new ones green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/service/AgentService.java backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java backend/src/main/java/com/peoplefirst/agent/dto/AgentChatRequestDto.java
git commit -m "feat(agent): tool-calling loop with confirm-gate and rule fallback"
```

---

### Task 4: Retire drafts, surface status, full verification

**Files:**
- Modify: `backend/src/main/java/com/peoplefirst/agent/service/AgentService.java` (delete `PendingLeaveDraft`, `userDrafts`, `continueLeaveDraft`, draft branches in `processRuleBased`)
- Test: extend `AgentServiceAgenticTest` + run `PeopleFirstIntegrationTest`

**Interfaces:**
- Consumes: Task 3 loop + history.
- Produces: final `/api/agent/status` shape: `agentName`, `role`, `genAiConfigured`, `genAiModel`, `agentMode` (`"agentic"` when configured else `"rule-based"`), `genAiProvider` (provider string, never the key), `genAiEndpointReachable` (boolean from a lightweight `GET {baseUrl}/models` probe with 3s timeout at status-call time, failures → false, never throws), `architecture` (keep existing string).

- [ ] **Step 1: Write the failing test**

```java
@Test
void statusReportsAgenticModeWithoutLeakingSecrets() {
    when(genAiClient.isConfigured()).thenReturn(true);
    Map<String, Object> status = agentService.getAgentStatus();
    assertEquals("agentic", status.get("agentMode"));
    assertFalse(status.toString().contains("test-key-not-sk"));
}

@Test
void draftLanguageStillWorksThroughHistory() {
    when(genAiClient.chatWithTools(anyString(), anyList(), anyList()))
            .thenReturn(Optional.of("{\"content\": \"Which type of leave?\", \"tool_calls\": []}"));
    AgentChatRequestDto request = new AgentChatRequestDto();
    request.setMessage("i want to apply for leave");
    request.setConversationId("test-conv-2");
    AgentChatResponseDto response = agentService.processMessage(request);
    assertNotNull(response.getReply());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -B test -Dtest=AgentServiceAgenticTest`
Expected: FAIL — `agentMode` key absent (`expected: <agentic> but was: <null>`).

- [ ] **Step 3: Minimal implementation**

Delete the `PendingLeaveDraft` static class, the `userDrafts` field, `continueLeaveDraft`, and the draft-management block at the top of the rule path (the expiry check, the explicit-draft-cancel check, and the `isExplicitOtherIntent` reroute) — the agentic path already handles multi-turn via history, and the rule path returns to single-turn switch behavior. Update `getAgentStatus` to add the four keys (reachability probe: `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()` GET to `baseUrl + "/models"` with Bearer key, `true` only on HTTP 200, catch-all `false`; needs `getBaseUrl()` accessor on `GenAiClient` returning the configured base URL string — add it in this task, no key ever included).

- [ ] **Step 4: Run everything and verify live behavior**

Run: `mvn -B clean test`
Expected: BUILD SUCCESS — all 26 pre-existing tests plus new agent tests pass. Then rebuild and restart the backend (`mvn -B package -DskipTests`, relaunch `java -jar` with JDK 17 as in the setup log), and verify: `GET /api/agent/status` shows `agentMode: rule-based` (no key configured); `POST /api/agent/chat` greeting still returns the template reply; frontend `:3000` login + chat drawer work unchanged.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/peoplefirst/agent/service/AgentService.java backend/src/test/java/com/peoplefirst/agent/AgentServiceAgenticTest.java
git commit -m "feat(agent): retire draft state machine, report agent mode in status"
```

---

## Self-review notes (checked while writing)

- Spec coverage: transport + placeholder default (Task 1), seven tools (Task 2), loop + history + confirm-gate + DTO contract + fallback (Task 3), draft retirement + status + full verification (Task 4). Grill decisions honored: placeholder base URL with identical-to-today behavior when unreachable, always-confirm writes, flag-only rollout for everyone (no per-role code).
- No placeholders: every step names exact files, exact commands with the project's JDK/Maven paths, and real code. One genuine unknown flagged inline: `AgentChatRequestDto.setConversationId` — the task says to check and add it if missing rather than assuming.
- Type consistency: `chatWithTools` signature (`String, List<Map<String,String>>, List<Map<String,Object>>` → `Optional<String>`) is identical in Task 1's producer block and Task 3's consumer; `IntentSlots` record shape matches the JSON the degrade prompt demands; action names reuse handler strings.
