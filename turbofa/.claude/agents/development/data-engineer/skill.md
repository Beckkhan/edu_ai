<!-- .claude/agents/development/data-engineer/skill.md -->
# data-engineer — skill

Companion to `.claude/agents/development/data-engineer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- The contract is spec 5f, executed in order; every statement is a SELECT:
  1. `SELECT * FROM fuelings WHERE fueling_id = ?` (fueling DB) — `fuelings` is
     partitioned by month; query the parent, never a partition directly.
  2. Take `user_id` from that row → `SELECT * FROM payments WHERE user_id = ?
     ORDER BY created_at DESC LIMIT 10` (payment DB). `payments.order_id` is verified
     NOT the fueling id (0/10), the user is the only working link (D8).
  3. `SELECT * FROM fueling_orders WHERE fueling_id = ?` (vendors DB, verified 1:1).
  4. `SELECT * FROM fueling_events WHERE fueling_id = ? ORDER BY created_at DESC
     LIMIT 20` (vendors DB).
  5. `SELECT * FROM vendor_fueling_orders WHERE fueling_id = ?` (vendors DB) —
     best-effort: empty results are normal (0/5 sampled), never a failure (D9). Do not
     join via `fuelings.vendor_fueling_order_id` (0 matches).
- Three databases → three HikariCP DataSources (`fueling`, `payment`, `vendors`),
  `maximumPoolSize = 5` each; JDBC URLs from DB_HOST/DB_PORT/DB_USER/DB_PASSWORD plus
  the fixed database names (D7). The `.env` DB_URL points at the admin database — do not
  use it for domain data (E3).
- `fueling_id` is a String UUID everywhere (§6, D6); PreparedStatements only; statements
  (and connections) closed in `finally` blocks.
- Rows return as raw column maps serialized as-is — no domain classes (R10); timestamps
  stay opaque (the sources mix numeric epoch, bigint, and text); LIMITs serve R11.
- R5 invariant: no DDL, no DML, no migrations, no schema creation, ever. Bash/psql use is
  read-only discovery and verification only.
- Signatures locked by T3: `fuelingById(id: String)`, `paymentsByUserId(userId: String,
  limit: Int)`, `fuelingOrdersById(id: String)`, `fuelingEventsById(id: String,
  limit: Int)`, `vendorFuelingOrdersById(id: String)`.
- Resolved 2026-09-23: the agent definition was reconciled to discovery — three
  databases and the five 5f queries — aligned with the spec and T3 acceptance.

## File ownership

| Path | Notes |
|------|-------|
| `db/DataSourceFactory.kt` | three HikariCP pools, driver `org.postgresql.Driver` (T3) |
| `db/FuelingDataSource.kt` | the five SELECTs of 5f (T3) |
| external Postgres | read-only; never written from any code path |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| 5f query layer (five functions, String ids) | koog-engineer (FuelingInfoTool, T2) | consumed to aggregate the tool result |
| `AppConfig` DB settings | api-client-engineer | host/port/user/password + fixed DB names |
| Raw column maps, opaque timestamps | koog-engineer | serialized as-is into the tool JSON |
| Provenance for §6 | architect / spec-writer | discovery results reproduced in the spec |

## Verification hooks

- Grep `db/` for `INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE` → none.
- Five functions exist with String `id` parameters; pool size 5; statements closed in
  `finally`.
- T11 integration test runs read-only against the stage DB and returns data for a sample
  UUID.
