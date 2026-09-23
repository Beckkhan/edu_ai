<!-- .claude/agents/development/logging-engineer/skill.md -->
# logging-engineer — skill

Companion to `.claude/agents/development/logging-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- 5a interface is exactly five methods (`brunoRequest`, `deepSeekRequest`,
  `deepSeekResponse`, `toolCall`, `brunoResponse`) — no extras (R10).
- Line format: `<date/time> <label>: ` followed by the JSON body pretty-printed with a
  2-space indent; date/time is ISO-8601 local. Never an ellipsis or a truncated body
  (R8). The label line ends with a colon; the JSON starts on the next line.
- Labels exactly: `Request from Bruno to backend`, `Request to Deepseek`,
  `Response from Deepseek`, `Tool call`, `Response from backend to Bruno`.
- `toolCall()` fires only when a tool is actually invoked for the request (R8).
- Redact before writing: DEEPSEEK_API_KEY, DB_PASSWORD, and the Authorization header
  value. When in doubt about a field, drop it rather than log it.
- Logger name `com.eduai.turbofa.requestlog`, the five lines at INFO. D4: logback.xml
  keeps a single FILE appender to `logs/turbofa.log` (no append attribute — logback
  1.6.3 forces append=true on RollingFileAppender, the old `<append>false</append>` was
  inert and has been removed; freshness is app-side per E4/D4: Application.kt truncates
  the file at startup, see kotlin-engineer; daily rotation, maxHistory 7, pattern
  `%msg%n`) attached ONLY to that logger — logback 1.6.3 forbids two appenders on one
  file; CONSOLE for everything else; root INFO; com.zaxxer.hikari / io.netty /
  io.ktor at WARN.
- Call sites belong to the consumers (points 1/5 api-client-engineer, 2/3/4
  koog-engineer); this agent ships the interface and the logback configuration only.
- Formatting is centralized in the implementation: call sites pass raw JSON strings and
  never build the label line themselves.

## File ownership

| Path | Notes |
|------|-------|
| `logging/RequestLogger.kt` | interface + SLF4J implementation (T5) |
| `src/main/resources/logback.xml` | keep as configured per D4 (T5) |
| other R2 call sites | read-only — owned by api-client-engineer and koog-engineer |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5a interface (five methods) | api-client-engineer (points 1/5), koog-engineer (points 2/3/4) | consumed; one line per call |
| Exact label set + json body rule | reviewer, test-engineer | verifiable format contract |
| Secret list | api-client-engineer (AppConfig env set) | which values must never reach a line |
| R8 five-line guarantee | the union of the consumers' call sites | not enforceable from this module alone |

## Verification hooks

- One Bruno round-trip produces exactly five lines in R2 order; the tool line appears
  only when a tool is used.
- R2 output lands only in `logs/turbofa.log`; the console stays free of R2 traffic.
- Grep the log file for the API key and DB password values → absent; Authorization
  headers never appear.
- T10 `RequestLoggerTest` covers the exact labels, json bodies, and redaction.
