<!-- .claude/agents/development/koog-engineer/skill.md -->
# koog-engineer — skill

Companion to `.claude/agents/development/koog-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Koog only (R6): every DeepSeek interaction goes through the Koog client; the sole
  hand-written HTTP touch point is the logging client factory (T6). `apiKey` and `model`
  are injected via constructor — no env reads in client code.
- D1: `TurbofaAgent` = `AIAgent` + `singleRunStrategy`. `chatAgentStrategy` is forbidden:
  it forces tool calls and breaks plain-text dialogue (weather-project experience).
- D2: each `chat()` maps the received message list (String roles) to ONE Koog `Prompt`
  with system/user/assistant roles; no Koog session state, no mutable conversation
  fields — the message list is the whole state.
- 5c/R7: descriptors load from `resources/tools/*.json` at startup; an empty tool set is
  a fail-fast startup error, so every backend→DeepSeek request carries a non-empty
  `tools` array containing `get_fueling_info`.
- Descriptor 5b: implement the skill-designer format exactly — `fueling_id` is
  `{"type": "string"}` (UUID, D6), `required: ["fueling_id"]`; never an int.
- R8 log call sites: 2 "Request from backend to DeepSeek" (request body),
  3 "Response from DeepSeek to backend" in the Receive phase of the response pipeline
  (D3 — Transform/Parse never fire for Koog, so logging there is dead code),
  4 "Request from backend to Postgres" only on an actual invocation, 4b "Response from
  Postgres to backend" (the tool result). All through the 5a `RequestLogger`; never log
  the Authorization header or API key.
- Tool handler (T2): `FuelingInfoTool` delegates to the T3 queries and returns JSON —
  fuelings row + user-scoped payments (LIMIT 10, D8) + fueling_orders + fueling_events
  (LIMIT 20) + vendor_fueling_orders (best-effort, D9). SELECT only.
- R11: minimal messages and tool payloads; never resend tool results.
- `fueling_id` embedding is NOT this agent's job: ChatService is the ONLY embedding
  point (5d) and hands over the fully built message list; this agent maps the String
  roles to Koog messages. Without a `fueling_id` the flow must work as plain dialogue
  with no tool call.

## File ownership

| Path | Notes |
|------|-------|
| `client/deepseek/DeepSeekClient.kt`, `client/deepseek/DeepSeekModels.kt`, `client/deepseek/TurbofaAgent.kt` | T4 |
| `client/deepseek/DeepSeekLoggingHttpClientFactory.kt` | T6 |
| `tool/FuelingInfoTool.kt` | T2 |
| `src/main/resources/tools/get_fueling_info.json` | T1 — descriptor content, format owned by skill-designer |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5d `TurbofaAgent.chat(messages): String` | kotlin-engineer | provided; tool calls execute inside the agent |
| 5a `RequestLogger` points 2/3/4 | logging-engineer | consumed, json bodies |
| `AppConfig` apiKey/model | api-client-engineer | injected, not read from env here |
| 5f `FuelingDataSource` five queries | data-engineer | consumed by the tool handler |
| Descriptor format 5b | skill-designer | implemented verbatim in T1 |

## Verification hooks

- Startup fails if `resources/tools/` yields an empty tool set; the request `tools`
  array is never empty.
- With `fueling_id`: the tool fires and the summary follows; without: no tool call.
- Four R2 lines (2/3/4/4b) appear with json bodies; the Postgres pair (4/4b) only when
  the tool is invoked.
- Grep the codebase for hand-written DeepSeek HTTP outside the logging factory → none.
