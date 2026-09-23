<!-- docs/tasks.md -->
# Tasks — turbofa

Ordered backlog derived from docs/project-specification.md (contracts 5a–5f, decisions
D1–D9, schema discovery §6, escalations E1–E3). Execution: /process (CTO, EXECUTION
ONLY). Statuses: pending → in progress → done (+ blocked); `verified: ok` is set by the
CTO after an approved review. Only the CTO updates statuses (R12). Dependencies are
encoded by list order; `deps:` bullets refine the parallel branches.

Artifact paths: `.kt` files are relative to `src/main/kotlin/com/eduai/turbofa/`
(tests: `src/test/kotlin/com/eduai/turbofa/`); other paths are repo-relative.

## Backlog

- [x] T1 | owner: development/koog-engineer | artifact: src/main/resources/tools/get_fueling_info.json | status: done | verified: ok
  - acceptance: file exists and is valid JSON; descriptor per spec 5b: name "get_fueling_info", fueling_id parameter with type "string" (UUID, D6) — not int; required ["fueling_id"]; koog-engineer loads it at startup
- [x] T2 | owner: development/koog-engineer | artifact: tool/FuelingInfoTool.kt | status: done | verified: ok
  - acceptance: Koog Tool<GetFuelInfoArgs, String> with the descriptor from T1; handler calls FuelingDataSource (T3, signatures per spec 5f) to aggregate from the three DBs; returns JSON with the fuelings row + payments (user-scoped, LIMIT 10, D8) + fueling_orders + fueling_events (LIMIT 20) + vendor_fueling_orders (best-effort, D9)
  - deps: T1
- [x] T3 | owner: development/data-engineer | artifact: db/DataSourceFactory.kt, db/FuelingDataSource.kt | status: done | verified: ok
  - acceptance: three HikariCP DataSources (fueling, payment, vendors databases, D7) built from the three JDBC URLs provided by AppConfig (T8) — URL derivation (DB_HOST/DB_PORT/DB_USER/DB_PASSWORD + fixed database names) is AppConfig's per spec 5f, E3; FuelingDataSource exposes fuelingById(id: String), paymentsByUserId(userId: String, limit: Int), fuelingOrdersById(id: String), fuelingEventsById(id: String, limit: Int), vendorFuelingOrdersById(id: String) per spec 5f; fueling_id type String (UUID, D6); SELECT only — no DDL/DML anywhere (R5); statements closed in finally; maximumPoolSize 5
  - deps: T1
- [x] T4 | owner: development/koog-engineer | artifact: client/deepseek/DeepSeekClient.kt, client/deepseek/DeepSeekModels.kt, client/deepseek/TurbofaAgent.kt | status: done | verified: ok
  - acceptance: TurbofaAgent (spec 5d — the "FuelingAgent" of the planning notes) implements chat(messages: List<ChatMessage>): String on AIAgent + singleRunStrategy (D1); history passed as one Prompt with system/user/assistant roles — no Koog session state (D2); tool registered from T1's JSON; tools list never empty — startup fails fast if no tool file (5c, R7); plain-text responses without a tool call work (R9); apiKey/model injected via constructor (no env reads in client code)
  - deps: T2, T3
- [x] T5 | owner: development/logging-engineer | artifact: logging/RequestLogger.kt | status: done | verified: ok
  - acceptance: RequestLogger interface per spec 5a (brunoRequest, deepSeekRequest, deepSeekResponse, toolCall, brunoResponse); line format "<date/time> <label>: <json body>" with pretty-printed JSON, no ellipsis (R8); labels exactly per R8; secrets redacted (DEEPSEEK_API_KEY, DB_PASSWORD, Authorization); logger com.eduai.turbofa.requestlog; logback.xml kept per D4 — single FILE appender logs/turbofa.log attached only to the R2 logger
  - note (CTO): defect found by T10 (Authorization regex over-consumption leaked a second Bearer token, R8) — fix routed to logging-engineer 2026-09-23 (token class bounded to [^\s",}]+), regression tests added in T10, delta reviewed ok. Residual (non-blocking, unreachable in this app): quoted-token shape `Authorization: Bearer "sk-x"` stays visible — possible future backlog line
- [x] T6 | owner: development/koog-engineer | artifact: client/deepseek/DeepSeekLoggingHttpClientFactory.kt | status: done | verified: ok
  - acceptance: factory wraps KtorKoogHttpClient.Factory; logs the request body (Transform phase) as "Request to Deepseek" and the response body (Receive phase, D3) as "Response from Deepseek" via RequestLogger (T5); json bodies
  - deps: T4
- [x] T7 | owner: development/kotlin-engineer | artifact: service/ChatService.kt, history/ChatHistoryStore.kt, history/InMemoryHistoryCache.kt, history/TextFileHistoryWriter.kt | status: done | verified: ok
  - acceptance: orchestration per spec 3.1: history.append(user) → agent.chat(messages); with fueling_id — embedded into the DeepSeek message (R9); without — normal dialogue, no tool call; history kept in cache + text file (5e); assistant records timestamped at DeepSeek response receipt (D5); no LLM HTTP calls and no SQL in this layer (R6/R5)
  - note (CTO): integration gate lifted 2026-09-23 — T4 verified with the exact 5d signature; whole main source set compiles
- [x] T8 | owner: development/api-client-engineer | artifact: routes/ChatRoutes.kt, config/AppConfig.kt | status: done | verified: ok
  - acceptance: POST /chat with DTO {prompt: String, fueling_id: String?} (D6) → {response: String}; missing prompt rejected; AppConfig reads env (DEEPSEEK_API_KEY, DEEPSEEK_MODEL, DB_*); log points 1 ("Request from Bruno to backend") and 5 ("Response from backend to Bruno") via RequestLogger (5a); no LLM/tool types in routes
  - deps: T5
- [x] T14 | owner: development/kotlin-engineer | artifact: settings.gradle.kts, gradle/wrapper/gradle-wrapper.properties, gradle/wrapper/gradle-wrapper.jar, gradlew, gradlew.bat | status: done | verified: ok
  - acceptance: settings.gradle.kts exists with rootProject.name = "turbofa" (single-module build per spec 3.3); wrapper files present (gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties) with distributionUrl pinned to Gradle 9.6.0 (gradle-9.6.0-bin.zip) — that version runs the identical Kotlin 2.4.20 / Ktor 3.5.2 / jvmToolchain(23) build in the sibling project weather, satisfying the Kotlin 2.4.20 + JDK 23 toolchain constraint (Gradle 9.x is well past the 8.14+ KGP floor; Gradle 9.x supports JDK 23 toolchains); wrapper may be copied from weather/ (already pinned to 9.6.0) or generated with the local Gradle 7.6.4 wrapper task in a temp dir — 7.6.4 cannot evaluate this build itself (KGP 2.4.20 rejects Gradle 7.6.4); `./gradlew tasks` (or `./gradlew help`) succeeds — first run downloads the distribution; no changes to build.gradle.kts versions
- [x] T9 | owner: development/kotlin-engineer | artifact: Application.kt | status: done | verified: ok
  - acceptance: wiring per spec 3.3 (config ← db/client/history/tool ← service ← routes): AppConfig → DeepSeekClient/TurbofaAgent (apiKey+model injected) → ChatService → ChatRoutes; RequestLogger and FuelingDataSource wired; history file cleared on startup (R4); app starts via ./gradlew run
  - acceptance2 (E4, D4 corrected): logs/turbofa.log truncated at startup before the first SLF4J logger initializes (top-level initializer above the log property) — the RollingFileAppender always appends
  - deps: T6, T7, T8, T14
- [x] T10 | owner: quality/test-engineer | artifact: src/test/kotlin/com/eduai/turbofa/service/ChatServiceTest.kt, src/test/kotlin/com/eduai/turbofa/logging/RequestLoggerTest.kt | status: done | verified: ok
  - acceptance: ChatService tested with a mocked TurbofaAgent — with fueling_id the message carries it, without it no tool call; RequestLogger format (exact labels, json bodies) and redaction (key/password never appear); MockK + coroutines-test; no network, no DB; ./gradlew test green
  - deps: T9
- [x] T11 | owner: quality/test-engineer | artifact: src/test/kotlin/com/eduai/turbofa/db/FuelingDataSourceIntegrationTest.kt | status: done | verified: ok
  - acceptance: read-only integration test against the external stage DB (credentials from .env): the five queries of 5f return data for a sample fueling_id; fueling_id typed String (UUID); SELECT only
  - deps: T9
- [x] T12 | owner: quality/reviewer | artifact: - | status: done | verified: ok
  - acceptance: final scenario (read-only): run the app (./gradlew run) and send two requests — (a) prompt only, (b) prompt + fueling_id (UUID); verify: exactly five R2 log lines with json bodies, non-empty tools array, tool called for (b) and data from the three DBs aggregated into the summary, no secrets in logs, no DDL/DML in the codebase; verdict approve → CTO marks the backlog done; R1–R12, D1–D9, 5a–5f all satisfied
  - deps: T10, T11
- [x] T13 | owner: specification/skill-designer | artifact: .claude/agents/<team>/<agent>/skill.md, .claude/agents/development/api-client-engineer.md, .claude/agents/development/data-engineer.md, .claude/agents/quality/test-engineer.md, .claude/agents/specification/skill-designer.md | status: done | verified: ok
  - acceptance: one skill.md per agent (12 files) capturing craft, file ownership and cross-agent contracts from the spec; .claude/README.md rationale table kept current (R2)
- [x] T15 | owner: specification/spec-writer | artifact: docs/README.md | status: done | verified: ok
  - acceptance: README describes each documentation file in docs/ (requirements.md, project-specification.md, tasks.md), styled similarly to the weather project's docs README; stakeholder-requested 2026-09-23
  - note: requested by the stakeholder during /process execution; executed by the CTO as a routed task

## Coverage

| Item | Task(s) |
|------|---------|
| R1 three teams + CTO lead | T12 (harness exists; exercised by the /process run) |
| R2 agents/skills folder + rationale + skill.md per agent | T13 |
| R3 docs folder with specifications | satisfied by the specification phase (requirements.md, project-specification.md, this file) |
| R4 stateless DeepSeek, history cache + text file cleared on restart | T7, T9 |
| R5 Kotlin, external read-only Postgres, no schema creation | T3, T11 |
| R5 Kotlin build infrastructure (settings.gradle.kts + wrapper, ./gradlew works) | T14 |
| R6 all DeepSeek calls via Koog | T2, T4, T6 |
| R7 get_fueling_info + descriptor + performs work + non-empty tools | T1, T2, T3, T4 |
| R8 five log points, json bodies | T5, T6, T8 |
| R9 Bruno request contract and chain | T7, T8 |
| R10 no unnecessary abstractions | T12 (reviewer verdict; also pinned in T4/T7 acceptance) |
| R11 efficient token usage | T3 (LIMIT 10/20), T4 |
| R12 docs → tasks → /process, verification-only, CTO-only statuses | T12 (process run); this file |
| D1 AIAgent + singleRunStrategy | T4 |
| D2 history as one Prompt, no Koog session | T4, T7 |
| D3 response logged in Receive phase | T6 |
| D4 single FILE appender, R2 logger only | T5 |
| D5 received_at = DeepSeek response receipt | T7 |
| D6 fueling_id string UUID | T1, T3, T8 |
| D7 three DataSources (fueling/payment/vendors) | T3 |
| D8 payments user-scoped, LIMIT 10 | T2, T3 |
| D9 vendor_fueling_orders best-effort | T2, T3 |
| 5a log format + RequestLogger interface | T5 |
| 5b get_fueling_info descriptor | T1 |
| 5c non-empty tools invariant | T4 |
| 5d ChatService ↔ agent interface | T4, T7 |
| 5e history contract | T7, T9 |
| 5f external data access (five SELECTs) | T3 |
| E1 fueling_id int vs UUID | T1, T8 (string adopted per D6; stakeholder confirmation outside tasks) |
| E2 tables vs databases | T3 (per-database queries per §6) |
| E3 DB_URL points at admin DB | T3 (URLs from DB_HOST/DB_PORT/DB_USER/DB_PASSWORD) |
