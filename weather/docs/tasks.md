<!-- docs/tasks.md -->
# Tasks

Derived from docs/project-specification.md. Format per line:
`- [ ] T<n> | owner: <team/agent> | artifact: <path> | status: pending | verified: -`

Standing rules: the cto advances statuses during /process (pending → in progress → done/blocked);
QualityTeam's reviewer reviews every task's diff and sets `verified` before a task is done;
test-architect approves test assignments as part of the quality gate (no separate artifact task).
Order encodes dependencies; `deps:` notes are listed only where order alone is ambiguous.

- [x] T1 | owner: SpecificationTeam/skill-designer | artifact: agents/development/{koog-engineer,api-client-engineer,logging-engineer,kotlin-engineer,data-engineer,core-engineer}/skill.md | status: done | verified: ok
  - acceptance: every DevelopmentTeam skill.md matches spec §4 (koog-engineer owns client+agent+tools; api-client-engineer re-scoped to Bruno-facing contracts/config/DTOs; logging-engineer owns RequestLogger; core-engineer marked with scope absorbed by kotlin-engineer/koog-engineer); no skill still describes the hand-written client as its deliverable

- [x] T2 | owner: DevelopmentTeam/koog-engineer | artifact: src/main/resources/tools/save_weather.json | status: done | verified: ok
  - acceptance: the file exists and is valid JSON per contract 5b (name, description, parameters: city, country, temperature, description, received_at); koog-engineer loads it at startup (Task A of R3)

- [x] T3 | owner: DevelopmentTeam/koog-engineer | artifact: src/main/kotlin/com/eduai/weather/client/deepseek/WeatherAgent.kt, src/main/kotlin/com/eduai/weather/tool/SaveWeatherTool.kt | status: done | verified: ok
  - deps: T2
  - note: executed with singleRunStrategy per the SpecificationTeam's D1/5c update (blocker resolved)
  - acceptance: the Koog strategy registers save_weather from the JSON (Task B of R3); the outgoing backend→DeepSeek request carries a non-empty tools array containing save_weather (R4); the tool handler calls WeatherLogRepository.save; on a weather request the log contains a "Tool call" line with a json body

- [x] T4 | owner: DevelopmentTeam/logging-engineer | artifact: src/main/kotlin/com/eduai/weather/logging/RequestLogger.kt | status: done | verified: ok
  - acceptance: interface per contract 5a (brunoRequest, deepSeekRequest, deepSeekResponse, toolCall, brunoResponse); output matches the R2 line format exactly; secrets (DEEPSEEK_API_KEY, Authorization) are redacted

- [x] T5 | owner: DevelopmentTeam/koog-engineer | artifact: src/main/kotlin/com/eduai/weather/client/deepseek/WeatherAgent.kt, src/main/kotlin/com/eduai/weather/client/deepseek/DeepSeekClient.kt | status: done | verified: ok
  - deps: T3, T4
  - acceptance: WeatherAgent emits "Request to Deepseek", "Response from Deepseek", and "Tool call" lines (only on an actual tool invocation) via RequestLogger, with json bodies

- [x] T6 | owner: DevelopmentTeam/api-client-engineer | artifact: src/main/kotlin/com/eduai/weather/routes/ChatRoutes.kt | status: done | verified: ok
  - deps: T4
  - acceptance: ChatRoutes emits "Request from Bruno to backend" and "Response to Bruno" lines via RequestLogger, with json bodies

- [x] T7 | owner: DevelopmentTeam/kotlin-engineer | artifact: src/main/kotlin/com/eduai/weather/service/ChatService.kt, src/main/kotlin/com/eduai/weather/Application.kt | status: done | verified: ok
  - deps: T3, T5
  - acceptance: ChatService depends on WeatherAgent per contract 5d; the direct saveWeatherTool.execute() call is removed — DB writes happen ONLY via an LLM-initiated tool call (Task C of R3); a weather request inserts a row into weather_log; Application.kt wires WeatherAgent + RequestLogger

- [x] T8 | owner: DevelopmentTeam/data-engineer | artifact: src/main/kotlin/com/eduai/weather/db/WeatherLogRepository.kt | status: done | verified: ok
  - acceptance: save(city, country, temperature, description, receivedAt) accepts an explicit receivedAt and persists it exactly; DB default now() remains as a safety net only

- [x] T9 | owner: DevelopmentTeam/koog-engineer | artifact: src/main/kotlin/com/eduai/weather/tool/SaveWeatherTool.kt | status: done | verified: ok
  - deps: T3, T8
  - acceptance: the handler passes received_at = the moment the DeepSeek response with weather data was received through to WeatherLogRepository.save (Task D of R3); the received_at value in the DB matches the DeepSeek response timestamp in the log (±1s)

- [x] T10 | owner: QualityTeam/unit-test-engineer | artifact: src/test/kotlin/com/eduai/weather/service/ChatServiceTest.kt | status: done | verified: ok
  - deps: T7
  - acceptance: ChatService tests pass with WeatherAgent mocked; no direct tool-call path remains testable; no network, no DB

- [x] T11 | owner: QualityTeam/unit-test-engineer | artifact: src/test/kotlin/com/eduai/weather/logging/RequestLoggerTest.kt | status: done | verified: ok
  - deps: T4
  - acceptance: unit tests assert the exact five-line R2 format and secret redaction

- [x] T12 | owner: QualityTeam/integration-test-engineer | artifact: src/test/kotlin/com/eduai/weather/db/WeatherLogRepositoryTest.kt | status: done | verified: ok
  - deps: T8
  - acceptance: tests pass against docker compose up postgres; received_at round-trips without loss

- [x] T13 | owner: QualityTeam/reviewer | artifact: final scenario run (Bruno request against the running app) | status: done | verified: ok
  - deps: T5, T6, T7, T9, T10, T11, T12
  - acceptance: one Bruno request produces exactly five log lines with json bodies (R2); the outgoing DeepSeek request shows a non-empty tools array (R4); a weather request inserts a weather_log row with received_at matching the DeepSeek response timestamp (±1s, R3); harness folder with commands/agents/skills and skill-vs-agent rationale exists (R5); docs/project-specification.md and docs/tasks.md exist and were created by SpecificationTeam (R6)
