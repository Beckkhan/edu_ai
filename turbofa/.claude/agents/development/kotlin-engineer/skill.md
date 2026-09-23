<!-- .claude/agents/development/kotlin-engineer/skill.md -->
# kotlin-engineer — skill

Companion to `.claude/agents/development/kotlin-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Layer rules (T7/T9): no LLM HTTP, no SQL, no R2 log call sites in these files. LLM
  access goes through the 5d `TurbofaAgent` interface only; fueling data arrives solely
  via the agent's tool call. No DeepSeek/Koog/JDBC types imported here (R6, R5).
- Orchestration per spec §3.1/§5d: `history.append(user)` → `agent.chat(messages)` →
  record the assistant reply → return its text. ChatService is the ONLY `fueling_id`
  embedding point: it embeds the id into the DeepSeek message before the call, so the
  agent receives the fully built message list; without it the list stays a plain
  dialogue and no tool call happens (R9, D2).
- D5: the assistant record's `received_at` is the moment the DeepSeek response is
  received — not a later write time, not the tool-call time.
- History (5e/R4): `ChatHistoryStore` is the single source of truth, backed by
  `InMemoryHistoryCache` + `TextFileHistoryWriter`; the text file is truncated on
  startup; cache and file stay consistent during a run.
- Wiring order (T9): AppConfig → DeepSeekClient/TurbofaAgent (apiKey + model injected) →
  ChatService → ChatRoutes, with RequestLogger and FuelingDataSource wired in; history
  file cleared on startup; `./gradlew run` starts cleanly.
- Startup truncation of the R2 log (T9 acceptance2, E4/D4): Application.kt also truncates
  `logs/turbofa.log` before the process creates its first SLF4J logger — a top-level
  initializer declared ABOVE the file's `log` property; `main()` is too late, because
  `ApplicationKt.log` (and thus logback's configuration) initializes before `main()` runs.
  Ordering is a correctness requirement: truncated after logback read the file, the first
  R2 event rolls the emptied file over the previous day's archive.
- R10: plain readable classes; no single-implementation interfaces, no premature
  abstraction.
- Dependency direction must hold: this layer may import config/client/history/tool, and
  is imported by routes — never the reverse.

## File ownership

| Path | Notes |
|------|-------|
| `Application.kt` | Ktor bootstrap and wiring (T9, mainClass `com.eduai.turbofa.ApplicationKt`) |
| `service/ChatService.kt` | orchestration glue (T7) |
| `history/ChatHistoryStore.kt`, `history/InMemoryHistoryCache.kt`, `history/TextFileHistoryWriter.kt` | history trio, 5e (T7) |
| everything else | read-only — routes, config, client, db, logging, tool belong to other owners |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5d `TurbofaAgent.chat(messages): String` | koog-engineer | the only LLM entry point used here |
| `AppConfig` (apiKey, model, DB settings) | api-client-engineer | constructor inputs during wiring |
| 5e history semantics | test-engineer, reviewer | provided by this agent: cache + file, cleared on restart, D5 timestamps |
| `ChatService` | api-client-engineer (route, T8/T9) | suspend entry the route awaits |
| 5a `RequestLogger` | logging-engineer | known, but no R2 call sites are owned here |

## Verification hooks

- Grep these five files for HTTP-client or JDBC imports/SQL keywords → none.
- Restart truncates the history file; during a run the cache and file agree.
- ChatService tests (T10) cover the with- and without-`fueling_id` split.
- No import from `routes/` into `service/` (dependency direction preserved).
