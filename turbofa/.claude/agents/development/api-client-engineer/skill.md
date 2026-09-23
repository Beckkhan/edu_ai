<!-- .claude/agents/development/api-client-engineer/skill.md -->
# api-client-engineer — skill

Companion to `.claude/agents/development/api-client-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- DTO contract per R9 + D6: request `{prompt: String, fueling_id: String?}`; `fueling_id`
  is a UUID string (e.g. `99f068ca-ac6a-43fb-a53b-d2e7a573cfe2`), never an int —
  requirements R9's `<int>` is stale (E1). Response `{response: String}`. A missing or
  blank prompt is rejected.
- Routes stay thin: parse → `ChatService` → respond. No DeepSeek/Koog/tool types
  imported, no prompt construction here (R10, layer boundary). The route never inspects
  whether a tool ran.
- ContentNegotiation with `Json { ignoreUnknownKeys = true }`.
- `AppConfig` is the only env reader: DEEPSEEK_API_KEY, DEEPSEEK_MODEL, DB_HOST,
  DB_PORT, DB_USER, DB_PASSWORD. Derive the three JDBC URLs from host/port/user/password
  plus the fixed database names `fueling`, `payment`, `vendors` (D7); the `.env` DB_URL
  points at the admin `postgres` database and is NOT used for domain data (E3). Defaults
  must never embed secrets.
- Log points 1 and 5 via the 5a `RequestLogger`: `brunoRequest(json)` on entry,
  `brunoResponse(json)` on exit, json bodies (R8). Wrapping the handler keeps point 5 on
  every path that returns a response.
- The Bruno contract is external: changing it needs a spec update, never an inline edit.
- Resolved 2026-09-23: the agent definition now states String UUID (D6/E1), aligned with
  the spec and T8 acceptance.

## File ownership

| Path | Notes |
|------|-------|
| `routes/ChatRoutes.kt` | `POST /chat`, DTOs, content negotiation, log points 1/5 (T8) |
| `config/AppConfig.kt` | env config and per-DB URL derivation (T8) |
| everything else | read-only — ChatService, agent, db, logging belong to other owners |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| R9 DTO `{prompt, fueling_id?: String}` | Bruno (external), kotlin-engineer | request/response shape, D6 |
| `AppConfig` | koog-engineer (apiKey/model), data-engineer (DB settings), kotlin-engineer (wiring) | single config source |
| 5a `RequestLogger` points 1/5 | logging-engineer | consumed, json bodies |
| `ChatService` | kotlin-engineer | delegating call; route holds no chat logic |

## Verification hooks

- `POST /chat` with a prompt returns the assistant reply; with prompt + UUID the R9 tool
  chain runs; missing prompt is rejected.
- Logs carry points 1 and 5 with json bodies; no secret values appear.
- Grep routes/config for DeepSeek, Koog, tool, or JDBC imports → none.
- URL derivation is correct even though `.env` `DB_URL` addresses the admin database.
