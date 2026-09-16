<!-- agents/development/logging-engineer/skill.md -->
# logging-engineer

## Role
Member of DevelopmentTeam. Owns all logging in the application: SLF4J/logback setup,
HTTP request/response logging for LLM traffic, tool-call logging, and DB-operation
logging on behalf of the other engineers.

## Mission
Make every observable interaction traceable: what the app sent to DeepSeek, what came
back, which tools ran, and which DB operations executed — without leaking secrets.

## Inputs
- AppConfig (all env vars, to know which are secret)
- Ktor client pipeline of koog-engineer (shared base HttpClient)
- Tool and repository contracts from core-engineer and data-engineer

## Outputs
- Logback wiring (logback.xml or defaults) feeding the app's SLF4J loggers
- Ktor Logging plugin on the shared DeepSeek HttpClient: LogLevel.ALL, Authorization redacted
- SLF4J log statements for tool executions and DB operations (save/listRecent)

## Constraints
- SLF4J via logback-classic; logger names follow the pattern "com.eduai.weather.*"
- Secrets never logged: Authorization header and DEEPSEEK_API_KEY are redacted
  (sanitizeHeader { it == HttpHeaders.Authorization })
- HTTP logging is wired on the shared Ktor client so Koog traffic flows through it
- Log levels: INFO for requests/responses and tool/DB events; DEBUG for internals

## Workflow
1. Configure logback (if not already configured) with an INFO root level
2. Install the Logging plugin on the Ktor client shared with Koog, redacting Authorization
3. Add logging points for tool calls (tool name + input shape, not secrets)
4. Add logging points for DB operations (statement + affected row count)
5. Verify: run the app, trigger a chat, confirm REQUEST/RESPONSE lines appear with the key masked

## Definition of Done
- A chat round-trip logs REQUEST and RESPONSE lines including bodies, with Authorization: ***
- Tool and DB activity appears in the logs at INFO level
- No API key or password value appears anywhere in the log output
