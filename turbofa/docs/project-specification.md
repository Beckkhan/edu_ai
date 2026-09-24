<!-- docs/project-specification.md -->
# Project Specification — turbofa

Solution document for the turbofa fueling-data chat backend (`com.eduai.turbofa`).
Written by the Specification team (spec-writer, architect as consultant) from
`docs/requirements.md`; external schema discovered 2026-09-23 (read-only, see §6).
Executed by the CTO via `/process` against `docs/tasks.md`. Logback 1.6.3 append
behavior verified 2026-09-23 against the resolved jars and a live run (§5a/D10, E4).

## 1. Goal

A backend that aggregates proliv data of one fueling by `fueling_id` and provides AI
analysis of it via DeepSeek. Development is harness-driven: all implementation happens
through `.claude` agents via `/process` — no hand-written code.

- Bruno sends `{"prompt": "...", "fueling_id": <id>}` (fueling_id optional).
  With fueling_id: the backend embeds it into the DeepSeek message; the chain is
  Bruno → backend → DeepSeek → tool get_fueling_info(fueling_id) → external DB →
  DeepSeek (summary) → backend → Bruno. Without fueling_id: normal dialogue, no
  tool call (R9).
- The external Postgres is READ-ONLY: only SELECTs. No migrations, no schema
  creation, no writes of any kind (R5).
- All DeepSeek calls via Koog (R6); every backend→DeepSeek request carries a
  non-empty tools array (R7).
- Six R2 log points with single-line compact json bodies, written to BOTH the console
  and logs/turbofa.log (R8, D10); per run that is 4 log events for a plain dialogue
  and 8 when a tool call is made (3.2); the file is truncated at startup so one run's
  file holds exactly that run's events (E4, 5a).
- Message history in a local cache + text file; the file is cleared on restart (R4).

## 2. Tech stack

Versions from build.gradle.kts (no changes without a task):

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.4.20, JVM toolchain 23 |
| Server | Ktor 3.5.2 (server-netty, content-negotiation, kotlinx-json) |
| LLM | **Koog 1.2.0 (mandatory for ALL DeepSeek interactions, R6)**: koog-agents, prompt-executor-deepseek-client 1.2.0-beta, http-client-ktor |
| Serialization | kotlinx-serialization-json 1.11.0 |
| DB access | Plain JDBC (postgresql 42.7.7) + HikariCP 6.2.1 — read-only, external |
| Logging | SLF4J + logback-classic 1.6.3 (RollingFileAppender forces append=true — E4; R2 on CONSOLE + FILE — D10) |
| Tests | JUnit 5 (kotlin-test), MockK 1.14.11, kotlinx-coroutines-test 1.8.1 |
| Infra | No docker-compose — the database is external and already running |

## 3. Architecture

### 3.1 Request flow (R9)

```
Bruno ──POST /chat──▶ ChatRoutes ──▶ ChatService ──▶ TurbofaAgent (Koog) ──▶ DeepSeek
  ▲  {prompt,           │ log 1           │ history +      │ AIAgent +          │
  │   fueling_id?}      │                 │ fuel_id into   │ singleRunStrategy  │
  │                     │                 │ message (D2)   │ + tools (non-empty)│
  └──{"response"}───────┘ log 5
```

With fueling_id present, the LLM may call `get_fueling_info(fueling_id)`:

```
TurbofaAgent ──tool call──▶ FuelingInfoTool ──SELECT──▶ fueling DB (fuelings)
                                       │                    └─▶ payment DB (payments by user_id)
                                       │                    └─▶ vendors DB (fueling_orders,
                                       │                          fueling_events,
                                       └── result JSON ──▶ DeepSeek produces summary
```

- `ChatRoutes` receives the R9 DTO (log point 1), `ChatService` builds the message
  list (embedding fueling_id into the outgoing user message when present, R9 — the
  single embedding point), the agent runs with a non-empty tool set, `ChatRoutes`
  returns the assistant text (log point 5).
- No tool call happens when fueling_id is absent — plain dialogue (D1).
- The server binds `0.0.0.0:8080`; Bruno targets `http://localhost:8080/chat`
  (pinned from T9, the same binding as the sibling weather project).

### 3.2 Logging boundaries (R8, D10)

All six lines go through one logger interface (contract 5a), owned by logging-engineer:

| # | Line | Emitted by |
|---|------|-----------|
| 1 | `Request from Bruno to backend` | ChatRoutes (api-client-engineer) |
| 2 | `Request from backend to DeepSeek` | TurbofaAgent (koog-engineer) |
| 3 | `Response from DeepSeek to backend` | TurbofaAgent — Receive phase of the response pipeline (koog-engineer, D3) |
| 4 | `Request from backend to Postgres` (only if a tool is actually used) | tool handler (koog-engineer) |
| 4b | `Response from Postgres to backend` | tool handler — aggregation result (koog-engineer) |
| 5 | `Response from backend to Bruno` | ChatRoutes (api-client-engineer) |

Event counts per run (R8's log-point count names the defined points, not a run's
lines): a plain-dialogue run emits 4 events — 1,2,3,5 — and a tool-call run emits 8 —
1,2,3,4,4b,2,3,5 — because R9's chain mandates two DeepSeek exchanges (tool call, then
summary) and D3 fires point 3 for each DeepSeek response.

Secrets (DEEPSEEK_API_KEY, DB_PASSWORD, Authorization) never appear in any log line.

R2 events go to BOTH sinks (D10): the CONSOLE (R2 console appender) and
`logs/turbofa.log` (R2 file appender). `logs/turbofa.log` is truncated at startup
before logback opens it, so a run's file contains only that run's events (E4, 5a).
Application.kt's own log lines go to the root CONSOLE logger, never to the R2 FILE
appender (logback forbids two appenders on one file, and %msg%n would leave root
lines without a date/time).

### 3.3 Module map

```
src/main/kotlin/com/eduai/turbofa/
├── Application.kt            # wiring + startup truncation of logs/turbofa.log (kotlin-engineer, E4)
├── config/AppConfig.kt       # env config, per-DB URLs (api-client-engineer)
├── routes/ChatRoutes.kt      # Bruno-facing API + log points 1/5 (api-client-engineer)
├── client/deepseek/          # Koog client + agent + tool registration (koog-engineer)
│   ├── DeepSeekClient.kt     #   Koog DeepSeekLLMClient + PromptExecutor
│   ├── TurbofaAgent.kt       #   AIAgent + singleRunStrategy + non-empty tools
│   └── DeepSeekModels.kt     #   ChatMessage(role: String, content: String) — 5d
├── tool/FuelingInfoTool.kt   # Koog tool handler → data-engineer queries (koog-engineer)
├── service/ChatService.kt    # orchestration glue (kotlin-engineer)
├── history/                  # ChatHistoryStore, cache, text file (kotlin-engineer)
├── db/                       # HikariCP + read-only queries (data-engineer)
│   ├── DataSourceFactory.kt  #   one DataSource per database (D7)
│   └── FuelingDataSource.kt  #   the five SELECTs of 5f
└── logging/RequestLogger.kt  # R2 logger interface + impl (logging-engineer)
src/main/resources/
├── logback.xml               # exists: R2 → CONSOLE + FILE, both %msg%n (D10)
└── tools/get_fueling_info.json  # NEW: tool descriptor (koog-engineer)
```

Dependency direction: config ← db/client/history/tool ← service ← routes.

## 4. Team responsibilities

| Agent | Scope |
|-------|-------|
| koog-engineer | Koog DeepSeek client, TurbofaAgent (AIAgent + singleRunStrategy, D1), get_fueling_info descriptor + registration, non-empty tools invariant, log points 2/3/4 (point 3 in the Receive phase, D3) |
| api-client-engineer | Bruno-facing API: ChatRoutes DTOs (prompt + optional fueling_id as string, D6), AppConfig incl. per-DB URL derivation (D7), content-negotiation wiring, log points 1/5 |
| logging-engineer | RequestLogger interface + SLF4J/logback implementation, exact R8 format (single-line, compact), secret redaction, logback.xml (D10: CONSOLE + FILE; freshness is NOT a logback feature — see 5a) |
| kotlin-engineer | Application glue: ChatService (incl. fueling_id embedding into the outgoing message, R9 — the single embedding point), history (ChatHistoryStore + cache + text file cleared on restart), Application.kt wiring + startup truncation of logs/turbofa.log (E4) |
| data-engineer | Read-only JDBC: DataSourceFactory (three DataSources, D7), FuelingDataSource (the queries of 5f, SELECT only) |
| test-engineer | Unit tests: agent contract, ChatService flow (with/without fueling_id), RequestLogger format/redaction, history, DTO validation |
| reviewer | Diff review gate for every task; verifies no DDL/DML anywhere (R5) |

## 5. Key contracts

### 5a. R2 log format and logger interface

An R2 event is a SINGLE LINE (D10): "<timestamp> <label>: <compact json body>",
timestamp = `yyyy-MM-dd HH:mm:ss.SSS` (local, milliseconds), body = compact JSON
(prettyPrint = false), never an ellipsis, never a truncated body (R8):

```
2026-09-24 10:15:30.123 Request from backend to DeepSeek: {"model":"deepseek-v4","messages":[...]}
```

```kotlin
interface RequestLogger {
    fun brunoRequest(json: String)
    fun deepSeekRequest(json: String)
    fun deepSeekResponse(json: String)
    fun toolCall(json: String)      // called only on an actual tool invocation
    fun postgresResponse(json: String) // the aggregation result coming back from Postgres
    fun brunoResponse(json: String)
}
```

`src/main/resources/logback.xml`: the R2 logger `com.eduai.turbofa.requestlog` is
attached to BOTH sinks — an R2 CONSOLE appender and a FILE appender `logs/turbofa.log`
(daily rotation, maxHistory 7) — both with pattern `%msg%n` (the event carries its own
timestamp). The root logger keeps the CONSOLE appender with pattern
`%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`; root INFO;
com.zaxxer.hikari / io.netty / io.ktor at WARN (D10).

**Fresh log per run (E4, corrected 2026-09-23; formerly half of D4).** logback 1.6.3 does not honor
`append=false` on a RollingFileAppender: `RollingFileAppender.start()` warns
"Append mode is mandatory for RollingFileAppender. Defaulting to append=true." and
forces append=true (verified in the logback-core 1.6.3 sources, the resolved jar, and
a live run — the `<append>false</append>` attribute is inert). The application
therefore truncates `logs/turbofa.log` itself at startup — `Application.kt`, owned by
kotlin-engineer (T9), alongside the R4 history-file clear. The truncation MUST run
**before the process creates its first SLF4J logger** (in Application.kt: a top-level
initializer declared above the file's `log` property; doing it in `main()` is too
late, because `ApplicationKt`'s `log` property — and thus logback's configuration —
is initialized before `main()` runs). Ordering is a correctness requirement, not
style: if the file is truncated after logback has read it, TimeBasedRollingPolicy
takes the stale file's lastModified as its initial rolling period, and the first R2
event rolls the now-empty file over the name `turbofa.<previous-day>.log`, clobbering
an existing archive (verified). Truncated first, the active file is empty and
current-day, no rollover fires, and the appender's O_APPEND writes start at offset 0:
`logs/turbofa.log` then contains exactly the current run's lines. The inert
`<append>false</append>` attribute is removed from logback.xml and replaced by a
comment pointing at the startup truncation (logging-engineer; cosmetic, no behavior
change — see D10). Rotation (daily) and maxHistory 7 are unchanged.

### 5b. resources/tools/get_fueling_info.json

fueling_id is typed from the discovered schema (§6): TEXT (UUID). The descriptor
parameter is a **string** — R9's `<int>` is stale (D6):

```json
{
  "name": "get_fueling_info",
  "description": "Gathers fueling data for one fueling_id: the fueling record, its user's latest payments, and vendor-side orders/events. Read-only.",
  "parameters": {
    "type": "object",
    "properties": {
      "fueling_id": {"type": "string", "description": "UUID of the fueling, e.g. 99f068ca-ac6a-43fb-a53b-d2e7a573cfe2"}
    },
    "required": ["fueling_id"]
  }
}
```

The tool actually performs the work (R7): it runs the SELECTs of 5f and returns the
raw rows as JSON for DeepSeek to summarize.

### 5c. Tool registration invariant (R7)

- Tool descriptors are loaded from `resources/tools/*.json` at startup.
- Invariant: the tools list handed to the agent is never empty — startup fails fast
  if no tool file is present, so every backend→DeepSeek request carries a non-empty
  `tools` array.
- Model comes from AppConfig (DEEPSEEK_MODEL).

### 5d. ChatService ↔ TurbofaAgent interface

```kotlin
interface TurbofaAgent {
    /** Returns the final assistant text; tool calls execute inside the agent. */
    suspend fun chat(messages: List<ChatMessage>): String
}
```

- `ChatService` depends on this interface, not on Koog types (R10).
- History is passed as one Prompt with system/user/assistant roles; no Koog session
  state; ChatHistoryStore is the single source of truth (D2).
- With fueling_id present, ChatService embeds it into the DeepSeek message (R9) —
  ChatService is the ONLY embedding point: `chat(messages)` receives the fully built
  list and has no fueling_id parameter, so the agent must not embed it again; without
  it, the message list stays a plain dialogue.

`ChatMessage` is T4's plain data class (`client/deepseek/DeepSeekModels.kt`), with
**String** roles — no enum — exactly as ChatService constructs it (T7, named args):

```kotlin
data class ChatMessage(val role: String, val content: String) // "system" | "user" | "assistant"
```

The agent maps those role strings to Koog's SystemMessage/UserMessage/AssistantMessage.

### 5e. History (R4)

- `history/ChatHistoryStore.kt` = single source of truth: InMemoryHistoryCache +
  TextFileHistoryWriter. The text file is CLEARED on restart (R4).
- received_at semantics (D5): the timestamp recorded for a DeepSeek response is the
  moment the response is received — not a later write time, not the tool-call time.
  (Turbofa never INSERTs into the external DB, so D5 applies to history records.)

### 5f. External data access (R5, R7)

Three databases on the same server (discovered, §6). AppConfig derives three JDBC URLs
from DB_HOST / DB_PORT / DB_USER / DB_PASSWORD + fixed database names `fueling`,
`payment`, `vendors` (D7); the .env `DB_URL` (which points at the `postgres` admin
database) is not used for domain data (E3). One HikariCP DataSource per database,
maximumPoolSize 5 each, plain JDBC PreparedStatements, closed in finally blocks.

`FuelingDataSource` executes, in order (all SELECT only):

1. `SELECT * FROM fuelings WHERE fueling_id = ?` — fueling DB. `fuelings` is
   partitioned by month; querying the parent routes across partitions.
2. From the row above, `user_id` → `SELECT * FROM payments WHERE user_id = ?
   ORDER BY created_at DESC LIMIT 10` — payment DB. `payments.order_id` is NOT the
   fueling id (verified, §6); the user is the only verified link (D8).
3. `SELECT * FROM fueling_orders WHERE fueling_id = ?` — vendors DB (verified 1:1).
4. `SELECT * FROM fueling_events WHERE fueling_id = ? ORDER BY created_at DESC
   LIMIT 20` — vendors DB (verified, several per fueling).
5. `SELECT * FROM vendor_fueling_orders WHERE fueling_id = ?` — vendors DB,
   best-effort: same UUID key space, rows exist only for a subset of fuelings (D9).

Return types (pinned): queries 1/3/5 return `Map<String, Any?>?` — null when the row is
absent; queries 2/4 return `List<Map<String, Any?>>`. A row is an ordered column-label →
JDBC-value map (no domain classes, R10; jsonb unwrapped to text, other values opaque so
the heterogeneous epoch timestamps stay as-is). `FuelingInfoTool` serializes them as one
JSON object keyed by the table names — `{"fuelings": …, "payments": […], "fueling_orders":
…, "fueling_events": […], "vendor_fueling_orders": …}` — an absent single row becomes JSON
`null` and an empty list `[]`; `payments` is `null` when the fueling row is missing (no
user_id to scope by, D8). LIMITs enforce token efficiency (R11).

## 6. Discovered external schema (2026-09-23, read-only)

Requirement R7 names "tables fueling, payment, vendors". Discovery shows they are
**databases** (one per domain, among ~34 on the server); the mandated query
`table_name IN ('fueling','payment','vendors')` matches nothing anywhere. Actual
tables carrying fueling data (all in schema `public`):

**Database `fueling` — table `fuelings`** (PARTITIONED by month: fuelings_YYYY_MM and
fuelings_part_* partitions; `fueling_id` is TEXT UUID):
fueling_id text, vendor_fueling_order_id text NULL, user_id text, status text,
amount numeric, fuel_type text, gas_station_id text, gas_pump_id text,
refueling_gun_id text, fuel_reservation_key text, created_at numeric, updated_at
numeric, actual_amount numeric NULL, vendor_transaction_date text NULL,
failed_reason text NULL, vendor_fuel_price numeric NULL, fueled_orders jsonb NULL,
discount_fuel_price numeric NULL, fueling_type text, extra jsonb NULL,
fueling_payment_type text, finished_at numeric NULL

**Database `payment` — table `payments`** (regular table; PK payment_id text):
payment_id text, order_id text (UUID-shaped but verified NOT a fueling id, 0/10),
user_id text, external_payment_id text NULL, status jsonb NULL, created_at bigint
NULL, updated_at bigint NULL, payment_system varchar, payment_method varchar,
payment_type text, purpose text, amount numeric NULL, actual_amount numeric NULL,
card_binding_id text NULL, card_binding_type text NULL, sbp_subscription_id text NULL

**Database `vendors`** (regular tables):
- `fueling_orders` (PK fueling_id text): fueling_id text, volume numeric, price
  numeric, fuel_type text, station_id text, pump_id text, created_at bigint,
  data jsonb, fuel_description text NULL, brand text
- `fueling_events` (PK event_offset bigint): fueling_id text, created_at bigint,
  status text, data jsonb, event_offset bigint, brand text
- `vendor_fueling_orders` (PK fueling_id text): fueling_id text, fuel_type text,
  quantity numeric, total_sum numeric, status text, gas_pump_number numeric,
  vendor_gas_station_id text, vendor_fuel_price numeric, email text NULL,
  phone text NULL, date_create text, created_at numeric

**fueling_id type: TEXT** everywhere it appears — normally a UUID, e.g.
`99f068ca-ac6a-43fb-a53b-d2e7a573cfe2`, but not universally: one known non-hex key
exists in `vendor_fueling_orders` — `gpn7a264-61ab-4622-983b-1ed62961a679`, the single
non-UUID of its 37 rows (live-verified 2026-09-23; also T11's vendor-only fixture).
Ids therefore stay String end-to-end (D6); the UUID guard in ChatRoutes applies to the
Bruno-facing contract only — a non-hex `fueling_id` from Bruno gets 400, intentionally
(E5). Verified joins: `fueling_orders.fueling_id`
and `fueling_events.fueling_id` = `fuelings.fueling_id` (1:1 and ~4 rows per fueling).
Unverified/no link: `payments.order_id` (0/10), `payments` ↔ fueling (user_id is the
only working link), `fuelings.vendor_fueling_order_id` ↔ vendor tables (0 matches).

## 7. Decisions

**D1 — Koog agent layer: AIAgent + singleRunStrategy.**
Why: chatAgentStrategy forces tool calls and breaks plain-text flows (weather-project
experience); singleRunStrategy makes the tool call optional, which R9 needs (no tool
call when fueling_id is absent).
Alternatives considered: chatAgentStrategy (rejected — forced tool loop); custom
strategy (rejected — overkill, R10).

**D2 — History passed to TurbofaAgent.chat(messages) as a single Prompt; no Koog
session state.**
Why: ChatHistoryStore is the single source of truth; duplicating state inside Koog's
session creates two sources of truth and violates statelessness (R4, 5d).
Alternatives: Koog session storage (rejected — drift risk); no history (rejected —
loses dialogue context).

**D3 — The DeepSeek response body is logged in the Receive phase of the response
pipeline.**
Why: Transform/Parse phases do not fire for Koog (weather-project experience) — a
logger attached there would silently never run.
Alternatives: Transform/Parse hooks (rejected — dead code); logging from the call
site (rejected — misses what Koog actually sends/receives).

**D10 — Single-line logs with from/to direction, CONSOLE + FILE both active.**
Decision: every R2 event is one compact line "<timestamp> <label>: <compact json>"
with from/to direction labels; the R2 logger writes to BOTH sinks (CONSOLE and
logs/turbofa.log).
Why: stakeholder wants to see all logs in the terminal for debugging; direction helps
track data flow across Bruno, backend, DeepSeek, Postgres.
Alternatives considered: (a) multi-line pretty-print (hard to read in terminal);
(b) FILE only (not visible in terminal).
The append=true / startup-truncation mechanics of the former D4 stay in force and are
documented in 5a (E4).

**D5 — received_at = the moment the DeepSeek response is received, not the INSERT
time and not the tool-call time.**
Why: the requirement's intent is to timestamp the data as delivered by the model;
tool-call time and write time both lag the receipt. Turbofa performs no INSERTs at
all (R5), so the rule applies to history records.
Alternatives: write time (rejected — lags receipt); tool-call time (rejected —
wrong event, and absent in plain dialogues).

**D6 — fueling_id is a string UUID end-to-end; the DTO accepts a string (R9's
`<int>` is stale).**
Why: discovery shows every fueling_id is a UUID text (fuelings, fueling_orders,
fueling_events, vendor_fueling_orders); an int can never match. The descriptor
parameter is `{"type": "string"}` (5b). Escalation E1.
Alternatives: keep int (rejected — impossible to match real data); accept both and
cast (rejected — ambiguity, R10).

**D7 — Three HikariCP DataSources (fueling, payment, vendors databases), derived
from DB_HOST/DB_PORT/DB_USER/DB_PASSWORD; DB_URL is not used for domain data.**
Why: the domain tables live in three separate databases; the .env DB_URL points at
the `postgres` admin database, which contains none of them (verified). Escalation E3.
Alternatives: three full DB_URLs in .env (rejected — more config surface, same
secrets); FDW/cross-database SQL (rejected — not available/overkill).

**D8 — Payments are user-scoped: the tool returns the fueling user's latest 10
payments (LIMIT 10, created_at DESC).**
Why: payments has no fueling_id and payments.order_id is verified NOT the fueling id
(0/10 samples); fuelings.user_id → payments.user_id is the only working link
(verified: 75 rows for a sample user).
Alternatives: order_id join (rejected — verified non-matching); drop payments from
the tool (rejected — R7 names the payment data explicitly).

**D9 — vendor_fueling_orders lookup is best-effort by fueling_id.**
Why: its fueling_id is UUID-shaped (same key space), but 0/5 sampled fuelings had a
row there — the table covers a subset (possibly one vendor). The query is correct;
the result may be empty.
Alternatives: drop the table (rejected — loses data when present); join via
fuelings.vendor_fueling_order_id (rejected — verified 0 matches).

## 8. Escalations and execution feedback (recorded, not silently "fixed")

- **E1** — R9 says Bruno sends `fueling_id: <int>`; the real id is a TEXT UUID.
  The spec adopts string (D6); the stakeholder should confirm the Bruno contract.
- **E2** — R7 names "tables fueling, payment, vendors"; they are databases with
  differently named tables (fuelings, payments, fueling_orders/fueling_events/
  vendor_fueling_orders). Mapping per §6.
- **E3** — .env DB_URL points at the `postgres` admin database; domain data lives in
  the fueling/payment/vendors databases (D7).
- **E4 (internal, resolved by this spec — no stakeholder action)** — T9's live run
  showed logback 1.6.3 ignores `append=false` on the RollingFileAppender, so
  logs/turbofa.log accumulated across restarts and T12's "exactly five R2 log lines"
  could not hold on a second run. Resolution: 5a amended (E4) — the app truncates
  logs/turbofa.log at startup before the first logger (`Application.kt`, T9,
  kotlin-engineer; T9 was still in progress, so no backlog re-plan and no new task),
  and the inert attribute is dropped from logback.xml (logging-engineer). The sibling
  weather project carries the identical latent config and has no truncation; it is a
  precedent for the config style only, not for freshness.
- **E5 (internal, resolved in-spec — no stakeholder action)** — §6's claim "fueling_id
  type: TEXT (UUID) everywhere it appears" is not literally true:
  `vendor_fueling_orders` also holds non-hex keys (live-verified 2026-09-23: one of its
  37 rows is the legacy vendor key `gpn7a264-61ab-4622-983b-1ed62961a679`, used by
  T11's fixtures). No code change needed — D6 already keeps ids as String end-to-end,
  and `ChatRoutes`' UUID regex (`routes/ChatRoutes.kt`) rejects non-hex ids with 400,
  which is intentional for the Bruno-facing contract (E1). §6 amended accordingly.
