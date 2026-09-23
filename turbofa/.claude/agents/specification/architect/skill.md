<!-- .claude/agents/specification/architect/skill.md -->
# architect — skill

Companion to `.claude/agents/specification/architect.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Discovery-first: any design that touches the external Postgres starts with a read-only
  `information_schema` query (psql via Bash). Record the real columns and the exact
  `fueling_id` type BEFORE any tool SQL or descriptor design — the requirements process
  note makes this a precondition, not a nicety.
- Treat the discovery result as the design baseline. For this project it was: three
  databases (`fueling`, `payment`, `vendors`), five queried tables, `fueling_id` TEXT
  UUID, payments reachable only via `user_id`, `vendor_fueling_orders` best-effort
  (spec §6, D6–D9).
- Deliverable shape: package tree for `com.eduai.turbofa`; class contracts as 1–2-line
  signatures; tech-stack versions pinned to build.gradle.kts (no change without a task);
  the module/dependency map that becomes spec §3.3.
- Boundary rules: dependency direction `config ← db/client/history/tool ← service ←
  routes`, no cycles; one writer per implementation file; Kotlin/Ktor +
  kotlinx.serialization; plain JDBC + HikariCP (no ORM); all LLM interaction via Koog
  (R6).
- Design checks: no interface with a single implementation (R10); no redundant context
  sent to DeepSeek (R11); external DB stays read-only — no migrations, no schema
  creation, no docker-compose.
- Handoff: schema + contracts → spec-writer; module ownership → skill-designer and
  task-planner. The frozen record is the specification, not this agent's working notes.

## File ownership

| Path | Notes |
|------|-------|
| External Postgres | read-only discovery/verification queries only — never DDL/DML |
| docs/project-specification.md §2, §3, §6 | contributes package tree, class signatures, discovered schema; spec-writer is the writer |
| — | no production source files; no file is ever owned by two agents |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Design record (tech stack, package tree, class signatures) | spec-writer | becomes spec §2/§3 |
| Discovered schema incl. `fueling_id` TEXT UUID | spec-writer → koog-engineer | descriptor 5b and query types |
| Module map + one-writer rule | skill-designer, task-planner | skill file-ownership tables and task `artifact:` paths |
| Dependency direction | all development agents | import/boundary checks in review |

## Verification hooks

- Spec §6 lists real types with a discovery date and read-only provenance; every table
  used in 5f appears there with its columns.
- Spec §3.3 assigns each file exactly one owning agent.
- Every class named in the spec has an owning agent in §4; task `owner:` values match
  agent `name:` frontmatter.
