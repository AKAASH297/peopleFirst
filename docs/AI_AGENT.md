# Kura AI Agent — How It Works

Kura is the leave-management and wellbeing concierge for peopleFirst. It used to
answer from hardcoded keyword rules only. It is now a **tool-calling agent**:
a chat model hosted at any OpenAI-compatible endpoint does the understanding
and talking, while every real action still runs inside the existing Spring Boot
services. No key configured → the app behaves exactly as before (rule-based).

## Architecture

```
Browser / CLI ──POST /api/agent/chat {message, conversationId} (JWT)──▶
AgentController ──▶ AgentService.processMessage ──┬──▶ agentic loop (key configured)
                                                  └──▶ rule fallback (no key / endpoint down)
```

- **Transport** — `agent/client/GenAiClient.java`. Single OpenAI-compatible
  caller: builds `{baseUrl}/chat/completions` (a base URL that already ends in
  `/chat/completions` is normalized, not doubled), sends `system` + `messages`
  + `tools`, parses `choices[0].message` (plain reply or `tool_calls`).
  Any non-200 response or exception → `Optional.empty()` → silent fallback.
- **Tools** — `agent/tools/AgentTool.java` + `AgentToolCatalog.java`. Seven
  function schemas, no logic of their own:
  `check_balance`, `apply_leave`, `cancel_leave`, `view_leaves`, `get_policy`,
  `wellbeing`, `ticket_info`.
- **Loop** — `AgentService.processAgentic`: system prompt (user, role,
  department, location, quotas, deadlines + today's date) + per-user
  conversation history + tool schemas → model → execute tools against the
  existing services → feed results back (max 5 turns) → natural-language reply
  in the unchanged `AgentChatResponseDto` shape (`reply`, `intent`,
  `actionExecuted`, `actionName`, `actionData`, `wellbeingSuggestions`,
  `quickReplies`), so the frontend needed no contract changes.
- **Rule fallback** — the original `IntentParser` + handlers + multi-turn
  leave drafts, kept byte-identical. It is also what runs when the endpoint
  is unreachable mid-turn.

## Safety properties (enforced, tested)

1. **Identity never comes from the client.** `processMessage` resolves the
   user from `SecurityContext` → DB first, on both paths.
2. **The model never writes to the DB.** `apply_leave` / `cancel_leave`
   return a *proposal*; execution happens only after an explicit user
   confirmation (`yes`/`confirm`/`proceed`, exact or first-token match —
   "yesterday" does not confirm), via `LeaveService` as before.
3. **Policy is code, not vibes.** Eligibility, quotas, combinations, notice
   periods and cutoffs are still decided by `PolicyService`; the model only
   explains the verdict.
4. **Histories are per-user** (`userId:conversationId`, capped at 20, map
   evicted past 500 keys) — no cross-user leakage.
5. **Abuse cap.** Messages over 2000 chars get a friendly refusal before any
   model call (inputs also carry `maxlength="2000"`).
6. **XSS hardening.** Model replies are HTML-escaped before the tiny
   `**bold**` / newline / pipe-table-fallback rendering in both chat UIs.

## Configuration (no secrets live in this repo)

| Env var | Purpose | Default |
|---|---|---|
| `GENAI_ENABLED` | master switch (`false` = always rule-based) | `true` |
| `GENAI_API_KEY` / `OPENAI_API_KEY` / `GEMINI_API_KEY` | key (first set wins) | _(empty → rule-based)_ |
| `OPENAI_BASE_URL` | any OpenAI-compatible base (OpenRouter, LiteLLM, Azure, Ollama/vLLM…) | `https://api.openai.com/v1` |
| `GENAI_MODEL` | model served by your endpoint | `gemini-1.5-pro` |
| `GENAI_PROVIDER` | `auto` \| `openai_compatible` \| `gemini` | `auto` |

Local dev keeps these in `.env` (git-ignored). `/api/agent/status`
reports `agentMode` (`agentic`/`rule-based`), `genAiConfigured`,
`genAiEndpointReachable` — booleans and mode only, never keys or URLs.

## UX details

- The model is instructed to present tabular data as bold-led bullet lists
  (chat cannot render wide tables well); a pipe-table → HTML-table fallback
  renderer catches strays.
- Branding is provider-neutral: both chat surfaces say
  `Kura · Leave & Wellbeing Concierge`.

## Tests

`mvn clean test` — 43 tests, all green: the 26 pre-existing suites
(including the rule-path draft integration tests, unchanged) plus
`GenAiClientTest` (stub-endpoint routing/URL tests), `AgentToolCatalogTest`
(schema contract), `AgentServiceAgenticTest` (tool loop, confirm-gate
negatives, per-user history isolation, 2000-char limit, status keys).

## File map

- `backend/.../agent/client/GenAiClient.java` — endpoint transport
- `backend/.../agent/tools/` — tool enum + function schemas
- `backend/.../agent/service/AgentService.java` — loop, history, confirm-gate, fallback
- `backend/.../agent/intent/` — legacy keyword parser (fallback path)
- `backend/.../agent/controller/` + `dto/` — unchanged API contract
- `frontend/js/components/chatWidget.js`, `frontend/js/features/agent/agentChat.js` — render + escape + table fallback
- `frontend/css/components.css` — `.kura-table` styles
- `docs/superpowers/plans/2026-09-04-ai-agent-openai-compatible.md` — full implementation plan
