<!-- docs/project-specification.md -->
# Project Specification

## 1. Goal

Rework the weather backend to satisfy R1–R6 from docs/requirements.md:

- R1 — all DeepSeek interactions go through the Koog framework
- R2 — five fixed log lines per request round-trip
- R3 — a working `save_weather` tool: JSON descriptor in resources, DB write on request,
  timestamp = time the DeepSeek response with the weather data was received
- R4 — `tools` array in backend→DeepSeek requests is always non-empty
- R5 — harness folder with commands/agents/skills + skill-vs-agent rationale
- R6 — specification → tasks → `/process` sequential execution

Development is harness-driven: implementation happens only via prompts to the agent teams
through `/process`; no hand-written code. The partially started Koog migration
(commit "Rework weather DeepSeek client to use the Koog framework") is completed and
extended with the agent/tool layer; agent documentation is reconciled with the code.

## 2. Tech stack

Versions taken from build.gradle.kts (no changes without a task):

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.20, JVM toolchain 23 |
| Server | Ktor 3.5.2 (server-netty, content-negotiation, kotlinx-json) |
| LLM | **Koog 1.2.0 (mandatory for ALL DeepSeek interactions)**: koog-agents, prompt-executor-deepseek-client 1.2.0-beta, http-client-ktor |
| HTTP client | Ktor client 3.5.2 (CIO engine, client-logging) |
| Serialization | kotlinx-serialization-json 1.11.0 |
| Persistence | Postgres 15-alpine (Docker), plain JDBC, HikariCP 6.2.1, postgresql 42.7.7 |
| Logging | SLF4J + logback-classic 1.6.3 |
| Tests | JUnit 5 (kotlin-test), MockK 1.14.11, kotlinx-coroutines-test 1.8.1 |

## 3. Architecture

### 3.1 Request flow

```
Bruno ──POST /chat──▶ ChatRoutes ──▶ ChatService ──▶ WeatherAgent (Koog) ──▶ DeepSeek
   ▲                      │                │                 │
   │                      │          (history,        AIAgent + strategy
   │                      │           clarification,   + tools from
   │                      │           Open-Meteo)      resources/tools/*.json)
   └────{"response"}──────┘
```

1. `ChatRoutes` receives `{"prompt": "..."}` from Bruno (log point 1)
2. `ChatService` keeps its deterministic orchestration: history append, country
   clarification, FETCH routing with real weather from `WeatherService` (Open-Meteo)
3. Every LLM call goes through `WeatherAgent` — the Koog agent layer (R1)
4. The agent runs with a strategy and a **non-empty tool set** (R4)
5. `ChatRoutes` returns `{"response": "..."}` (log point 5)

### 3.2 Tool call path

```
WeatherAgent (AIAgent + singleRunStrategy)
   └─ LLM decides to call save_weather
        └─ handler: SaveWeatherTool
             └─ WeatherLogRepository.save(city, country, temperature, description, received_at)
                  └─ weather_log (Postgres)
```

- The LLM itself calls the tool with real weather data placed in the prompt by `ChatService`
- `received_at` = time the DeepSeek response carrying the weather data was received
- Tool call is logged only when the tool is actually invoked (log point 4)

### 3.3 Logging boundaries (R2)

All five lines go through one logger interface (contract 5a), owned by logging-engineer:

| # | Line | Emitted by |
|---|------|-----------|
| 1 | `Request from Bruno to backend` | ChatRoutes (api-client-engineer) |
| 2 | `Request to Deepseek` | WeatherAgent (koog-engineer) |
| 3 | `Response from Deepseek` | WeatherAgent (koog-engineer) |
| 4 | `Tool call` (only if a tool is actually used) | tool handler (koog-engineer) |
| 5 | `Response to Bruno` | ChatRoutes (api-client-engineer) |

Secrets (DEEPSEEK_API_KEY, Authorization) never appear in any log line.

### 3.4 Module map (aligned with actual src/)

```
src/main/kotlin/com/eduai/weather/
├── Application.kt            # wiring (kotlin-engineer)
├── config/AppConfig.kt       # env config (api-client-engineer)
├── routes/ChatRoutes.kt      # Bruno-facing API + log points 1/5 (api-client-engineer)
├── client/deepseek/          # Koog client + agent + tool registration (koog-engineer)
│   ├── DeepSeekClient.kt     #   Koog DeepSeekLLMClient + PromptExecutor (exists, extends)
│   ├── WeatherAgent.kt       #   NEW: AIAgent + strategy + non-empty tools
│   └── DeepSeekModels.kt
├── tool/                     # Tool interface + handlers (koog-engineer)
│   ├── SaveWeatherTool.kt    #   rework: Koog tool handler, received_at
├── service/ChatService.kt    # orchestration glue (kotlin-engineer)
├── history/                  # cache + file history (kotlin-engineer)
├── weather/WeatherService.kt # Open-Meteo fetch (kotlin-engineer)
├── db/                       # repository + datasource (data-engineer)
└── logging/RequestLogger.kt  # NEW: R2 logger interface + impl (logging-engineer)
src/main/resources/
├── db/schema.sql
└── tools/save_weather.json   # NEW: tool descriptor (koog-engineer)
```

## 4. Team responsibilities

Conflict resolution: the hand-written DeepSeek client described in the old
api-client-engineer skill is already replaced by Koog (commit dfc7157). Ownership moves
to koog-engineer; api-client-engineer is **re-scoped, not retired**.

| Agent | Scope |
|-------|-------|
| koog-engineer | DeepSeek client, WeatherAgent (AIAgent + strategy), tool registration from resources/tools/*.json, R4 guarantee, log points 2/3/4 |
| api-client-engineer | Bruno-facing API: ChatRoutes DTOs, AppConfig, content-negotiation wiring, log points 1/5 |
| logging-engineer | RequestLogger interface + SLF4J/logback implementation, exact R2 format, secret redaction |
| kotlin-engineer | Application glue: ChatService, history, WeatherService, Application.kt wiring |
| data-engineer | WeatherLogRepository (received_at parameter), schema, HikariCP datasource |
| unit-test-engineer | Unit tests: agent contracts, ChatService flow, RequestLogger format |
| integration-test-engineer | WeatherLogRepositoryTest against Docker Postgres |
| test-architect | Test plan: unit vs integration split, mock boundaries |
| reviewer | Diff review gate for every task |

## 5. Key contracts

### 5a. R2 log format and logger interface

Line format (date/time = ISO-8601 local):

```
<date/time> Request from Bruno to backend: <json body>
<date/time> Request to Deepseek: <json body>
<date/time> Response from Deepseek: <json body>
<date/time> Tool call: <json body>            (only if a tool is actually used)
<date/time> Response to Bruno: <json body>
```

```kotlin
interface RequestLogger {
    fun brunoRequest(json: String)
    fun deepSeekRequest(json: String)
    fun deepSeekResponse(json: String)
    fun toolCall(json: String)      // called only on an actual tool invocation
    fun brunoResponse(json: String)
}
```

### 5b. resources/tools/save_weather.json

Parameters match the repository save signature (city, country, temperature, description,
received_at):

```json
{
  "name": "save_weather",
  "description": "Saves current weather for a city to the weather_log table.",
  "parameters": {
    "type": "object",
    "properties": {
      "city":        {"type": "string"},
      "country":     {"type": "string"},
      "temperature": {"type": "number"},
      "description": {"type": "string"},
      "received_at": {"type": "string", "format": "date-time"}
    },
    "required": ["city", "country", "temperature", "description", "received_at"]
  }
}
```

`WeatherLogRepository.save` gains a `receivedAt` parameter; the DB default `now()` stays
as a safety net only.

### 5c. Koog strategy contract (R4)

- `WeatherAgent` wraps an `AIAgent` with `singleRunStrategy()`
- Tool descriptors are loaded from `resources/tools/*.json` at startup
- Invariant: the tools list handed to the agent is never empty — startup fails fast if
  no tool file is present, so every backend→DeepSeek request carries a non-empty `tools` array
- Model and timeouts come from AppConfig (DEEPSEEK_MODEL, DEEPSEEK_BASE_URL)

### 5d. ChatService ↔ WeatherAgent interface

```kotlin
interface WeatherAgent {
    /** Returns the final assistant text; tool calls execute inside the agent. */
    suspend fun chat(messages: List<ChatMessage>): String
}
```

`ChatService` switches its LLM dependency from `DeepSeekClient` to `WeatherAgent`; its
clarification/fetch orchestration stays. In the FETCH branch, real weather data is placed
into the prompt, and the agent persists it via the save_weather tool call instead of a
direct `tool.execute()`.

## Decision log

**D1 — Koog agent layer (AIAgent + singleRunStrategy) as the only LLM surface.**
Why: R1 mandates Koog for all DeepSeek interactions; singleRunStrategy provides optional
tool calls, which D6 requires (CLARIFY/NORMAL text flows with no tool call).
Alternatives considered: (a) chatAgentStrategy — rejected, its forced tool loop breaks D6;
(b) custom strategy — overkill, singleRunStrategy covers the case out of the box.

**D2 — api-client-engineer re-scoped, not retired.**
Why: its old scope (hand-written client) is already Koog-replaced; Bruno-facing API
contracts/config/DTOs still need an owner, and retiring an agent costs more churn than
re-scoping.
Alternatives: retirement (orphans AppConfig/ChatRoutes ownership); keeping the old scope
(contradicts the code since commit dfc7157).

**D3 — Tool descriptors live in resources/tools/*.json, loaded by koog-engineer.**
Why: R3 requires the JSON file in resources; a single source of truth for registration
and the R4 non-empty guarantee.
Alternatives: programmatic-only registration (violates R3); hardcoded non-empty array
(satisfies R4 but is not configurable).

**D4 — Explicit R2 log points via a dedicated RequestLogger, not the Ktor Logging plugin.**
Why: R2 fixes an exact five-line format at named boundaries; the Ktor plugin's
REQUEST/RESPONSE output does not match it. The plugin may remain at DEBUG level only.
Alternatives: reformat plugin output (fragile); keep plugin only (fails R2).

**D5 — received_at = DeepSeek response receipt time, passed through to the repository.**
Why: R3 names exactly this timestamp ("time of receiving the DeepSeek response with
weather data"); DB default alone would timestamp tool execution instead.
Alternatives: DB default only (drifts from R3); tool-execution time (later than response
receipt — also drifts).

**D6 — ChatService keeps deterministic clarification/fetch orchestration; the agent
persists via tool call.**
Why: real weather comes from Open-Meteo, which the LLM cannot call (no weather-fetch tool
in requirements); keeping the proven CLARIFY/FETCH flow preserves current UX while the DB
write moves onto the required tool path.
Alternatives: give the LLM a second fetch tool (adds an unrequested tool; risks
hallucinated weather); drop the clarification flow (changes behavior beyond R1–R6).

**D7 — History passed to WeatherAgent.chat(messages) as a single Prompt; no Koog session state.**
Decision: History is passed to WeatherAgent.chat(messages) as a single Prompt with
system/user/assistant roles; AIAgent.run() receives this Prompt in full. Koog's agent
session is NOT used to store history across calls — ChatHistoryStore remains the single
source of truth.
Why: ChatService already owns the deterministic CLARIFY/FETCH/NORMAL flow and the history;
duplicating state inside Koog's session creates two sources of truth and violates
statelessness (5d).
Alternatives considered: (a) use Koog's session — duplicated state, drift risk;
(b) skip history entirely — loses dialogue context.
