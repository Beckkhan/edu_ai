<!-- .claude/agents/specification/task-planner/skill.md -->
# task-planner — skill

Companion to `.claude/agents/specification/task-planner.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Task line format is frozen:
  `- [ ] T<n> | owner: <team>/<agent> | artifact: <path> | status: pending | verified: -`
  plus `acceptance:` and optional `deps:` sub-bullets; statuses are
  `pending | in progress | done | blocked`.
- Artifact path convention: `.kt` files relative to `src/main/kotlin/com/eduai/turbofa/`
  (tests: `src/test/kotlin/com/eduai/turbofa/`); all other paths repo-relative.
- One owner per task. If a deliverable spans two scopes, split it; no implementation
  file may appear in two tasks — cross-check against spec §3.3.
- Ordering: discovery → contracts → infrastructure → implementation → tests. Encode
  dependencies in list order plus explicit `deps:`; a task never starts before its deps
  are `done`.
- Acceptance criteria must be checkable by file existence, JSON field/type, grep, or a
  test run — that is what the reviewer verifies and the CTO dispatches on. State stale
  requirement readings explicitly when they matter (`fueling_id` string UUID, D6/E1 —
  not int).
- Coverage section: one row per R1–R12, plus rows for D1–D9 and 5a–5f, each naming the
  tasks that satisfy it.
- After handover, statuses belong to the CTO (R12); this agent re-plans only when an
  escalation reopens a contract.

## File ownership

| Path | Notes |
|------|-------|
| docs/tasks.md | sole author until handover; afterwards read-only (CTO owns statuses) |
| docs/project-specification.md | read-only source |
| — | no source files |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Spec contracts + §3.3 module map | spec-writer | decomposed into tasks with `artifact:` paths |
| Agent roster (`name:` frontmatter values) | skill-designer / CTO | `owner:` values must be spawnable subagent types |
| Runnable task lines | CTO | dispatch unit with acceptance and deps |
| Acceptance criteria | task owners, reviewer | mechanically verifiable baseline |
| Escalation-driven re-plan | spec-writer | contract changes reopen affected tasks |

## Verification hooks

- Every task has exactly one owner and at least one artifact path.
- No implementation file is produced by two tasks; no todo R1–R12 is unmapped.
- `deps:` are acyclic and topologically consistent with list order.
- Owner names all match `.claude/agents/**/*.md` `name:` frontmatter values.
