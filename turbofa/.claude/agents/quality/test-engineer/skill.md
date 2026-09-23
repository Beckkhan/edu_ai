<!-- .claude/agents/quality/test-engineer/skill.md -->
# test-engineer — skill

Companion to `.claude/agents/quality/test-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Unit-test stack: MockK for final classes, `kotlinx-coroutines-test` (`runTest`) for
  suspend functions, `kotlin.test` assertions. Unit tests run with no network, no DB, no
  real `.env` — mock the 5d `TurbofaAgent` interface so `ChatService` is testable
  without Koog.
- Must-cover list: ChatService with `fueling_id` (the message carries it and the tool
  path is taken) vs without (no tool call); RequestLogger exact labels, json bodies, and
  secret redaction; history file cleared on restart plus cache/file consistency; DTO
  validation (prompt required, `fueling_id` optional string).
- T11 integration test runs read-only against the stage DB with credentials from `.env`:
  the five 5f queries return data for a sample `fueling_id` (String UUID); SELECT only,
  no DDL/DML.
- Verification-only passes (R12): inspect the existing diff against its acceptance
  criteria and run its tests; never re-implement; report pass or a findings list with
  file:line.
- Assert behavior, not internals (R10): e.g. the tool-path decision and the returned
  text, not Koog strategy internals or private fields.
- Redaction tests must assert absence of the real secret value, not just the presence of
  a placeholder.
- Resolved 2026-09-23: the agent definition now says optional String UUID (D6), aligned
  with the spec and T8 acceptance — test the string contract.

## File ownership

| Path | Notes |
|------|-------|
| `src/test/kotlin/com/eduai/turbofa/**` | T10 `service/ChatServiceTest.kt`, `logging/RequestLoggerTest.kt`; T11 `db/FuelingDataSourceIntegrationTest.kt`; further test files as tasks assign |
| production sources | read-only — defects are reported to the owning agent, never fixed here |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5d `TurbofaAgent` interface | koog-engineer | mocked in ChatService tests |
| 5a `RequestLogger` | logging-engineer | format and redaction assertions |
| 5e history semantics | kotlin-engineer | restart-clear and consistency assertions |
| Acceptance criteria | task-planner / CTO | the definition of "covered" per task |
| Test results | reviewer | evidence the referenced tests exist and pass |

## Verification hooks

- `./gradlew test` is green without Docker and without DEEPSEEK_API_KEY.
- The R9 with/without-`fueling_id` split is covered.
- Redaction test proves the API key and DB password never appear in log output.
- T11 fails with a clear message if the stage DB is unreachable (documented external
  dependency, not a skipped test).
