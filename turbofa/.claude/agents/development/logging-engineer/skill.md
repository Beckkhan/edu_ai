<!-- .claude/agents/development/logging-engineer/skill.md -->
# logging-engineer — skill

Companion to `.claude/agents/development/logging-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- 5a interface is exactly six methods (`brunoRequest`, `deepSeekRequest`,
  `deepSeekResponse`, `postgresRequest`, `postgresResponse`, `brunoResponse`) — no
  extras (R10).
- Line format: a SINGLE line `<date/time> <label>: <json body>`; date/time is
  `yyyy-MM-dd HH:mm:ss.SSS` (local, milliseconds); the body is compact JSON
  (prettyPrint = false). Never an ellipsis or a truncated body (R8).
- Labels exactly, with from/to direction (D10): `Request from Bruno to backend`,
  `Request from backend to DeepSeek`, `Response from DeepSeek to backend`,
  `Request from backend to Postgres`, `Response from Postgres to backend`,
  `Response from backend to Bruno`.
- `postgresRequest()` and `postgresResponse()` fire only when a tool is actually
  invoked for the request (R8).
- Redact before writing: DEEPSEEK_API_KEY, DB_PASSWORD, and the Authorization header
  value. When in doubt about a field, drop it rather than log it.
- Logger name `com.eduai.turbofa.requestlog`, the six lines at INFO. D10: logback.xml
  attaches the R2 logger to BOTH sinks — an R2 console appender and the file appender
  `logs/turbofa.log` (no append attribute — logback 1.6.3 forces append=true on
  RollingFileAppender, the old `<append>false</append>` was inert and has been removed;
  freshness is app-side per E4: Application.kt truncates the file at startup, see
  kotlin-engineer; daily rotation, maxHistory 7), both with pattern `%msg%n` — the
  event carries its own timestamp. The root logger keeps the CONSOLE appender with the
  full pattern; root INFO; com.zaxxer.hikari / io.netty / io.ktor at WARN. logback
  forbids two appenders on one file, so the FILE appender stays on the R2 logger only
  (root lines through %msg%n would lose their date/time).
- Call sites belong to the consumers (points 1/5 api-client-engineer, 2/3/4/4b
  koog-engineer); this agent ships the interface and the logback configuration only.
- Formatting is centralized in the implementation: call sites pass raw JSON strings and
  never build the label line themselves.

## File ownership

| Path | Notes |
|------|-------|
| `logging/RequestLogger.kt` | interface + SLF4J implementation |
| `src/main/resources/logback.xml` | keep as configured per D10 |
| other R2 call sites | read-only — owned by api-client-engineer and koog-engineer |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5a interface (six methods) | api-client-engineer (points 1/5), koog-engineer (points 2/3/4/4b) | consumed; one single-line event per call |
| Exact label set + json body rule | reviewer, test-engineer | verifiable format contract |
| Secret list | api-client-engineer (AppConfig env set) | which values must never reach a line |
| R8 six-point guarantee | the union of the consumers' call sites | not enforceable from this module alone |

## Verification hooks

- One Bruno round-trip produces the six points in R2 order (4 without a tool call, 8
  events for a tool-call run); the tool and Postgres lines appear only when a tool is
  used.
- R2 output lands in BOTH the terminal and `logs/turbofa.log`; every line starts with
  `yyyy-MM-dd HH:mm:ss.SSS` and is a single line.
- Grep the log file and console for the API key and DB password values → absent;
  Authorization headers never appear.
- T10 `RequestLoggerTest` covers the exact labels, single-line compact json bodies, and
  redaction.
