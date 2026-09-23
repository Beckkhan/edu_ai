<!-- docs/README.md -->
# Document Flow

How requirements become shipped work, and which document each stage owns.

```
docs/requirements.md ──(Specification team)──▶ docs/project-specification.md
                                                    │
                                                    ▼
                                            docs/tasks.md ──(/process, CTO)──▶ statuses updated
```

## Documents

| Document | Written by | Purpose |
|----------|-----------|---------|
| `docs/requirements.md` | Product owner / humans (recorded verbatim) | Raw input requirements R1–R12 plus clarifications and process notes. The only source of truth for what the product must do; never edited by agents. |
| `docs/project-specification.md` | Specification team (spec-writer; architect as consultant) | Solution document: goal and non-goals (read-only external DB, no migrations), tech stack, architecture (§3 request flow, logging boundaries, module map), team responsibilities, key contracts 5a–5f, discovered external schema (§6, from the live `information_schema`), decisions D1–D9 (§7) and escalations E1–E4 (§8). |
| `docs/tasks.md` | Specification team (task-planner) | Ordered backlog T1–T15, each with owner (`<team>/<agent>`), artifact, status, `verified` flag, acceptance criteria and dependencies; ends with the coverage table mapping R1–R12, D1–D9, contracts 5a–5f and escalations E1–E3 to tasks. Includes T14 (Gradle build infrastructure) and T15 (this file). |
| `docs/README.md` | Specification team (spec-writer) | This file — folder map and document flow for `docs/`. |

## Flow

1. **Requirements** — `docs/requirements.md` describes the desired behavior (R1–R12)
   and mandates that the spec phase discovers the external schema before any SQL or
   tool descriptor is designed.
2. **Specification** — the Specification team reads the requirements: the architect
   discovers the external DB schema (read-only `information_schema`), the spec-writer
   records the design in `docs/project-specification.md` (contracts 5a–5f, decisions
   D1–D9, schema §6, escalations E1–E4), and the task-planner decomposes it into
   `docs/tasks.md`.
3. **Execution** — `/process` invokes the CTO in EXECUTION ONLY mode. The CTO reads
   `docs/project-specification.md` and `docs/tasks.md`, dispatches each runnable task
   to its owning agent, and updates task statuses in `docs/tasks.md`
   (`pending → in progress → done`, or `blocked` with a reason).
4. **Acceptance** — a task becomes `done` only after Quality team review and tests
   pass; the CTO then sets `verified: ok`.
5. **Feedback** — spec gaps found during execution are escalated back to the
   Specification team and recorded in the spec (§8); the spec is updated and
   `docs/tasks.md` is re-planned. E4 is an example resolved in-spec with no new task.

## Rules

- `docs/requirements.md` is never edited by agents — requirements change only through the product owner.
- `docs/project-specification.md` and `docs/tasks.md` are owned by the Specification team; the CTO never edits them during execution.
- Only the CTO updates task statuses; done-and-unchanged tasks undergo verification only (R12).
- The external schema in §6 is quoted from the real discovery results, never an assumed schema.
- `docs/tasks.md` statuses are the single source of truth for progress and always reflect the repository state.
