<!-- .claude/agents/executive/cto/skill.md -->
# cto — skill

Companion to `.claude/agents/executive/cto.md`; the definition stays authoritative for
identity, scope, and authority. This file carries craft, file ownership, and the
cross-agent contracts exchanged on the project (source: docs/project-specification.md
§3.3, §4, §5; docs/tasks.md).

## Craft

- Dispatch rule: `owner: <team>/<agent>` in docs/tasks.md → spawn the subagent type
  `<agent>` (identity = the `name:` frontmatter value; the team subfolder is organization
  only). Pass the task line verbatim, its `acceptance:` / `deps:` bullets, and the spec
  contracts (5a–5f) it touches. The owner's definition loads automatically.
- Dependency gate: a task starts only when every `deps:` task is `done`. List order is
  the default execution order; `deps:` bullets refine parallel branches.
- Status discipline (R12): statuses and the `verified:` field in docs/tasks.md are
  CTO-only and advance immediately on an outcome — `pending → in progress → done`, or
  `blocked` with a reason. `verified: ok` only after an approved review.
- Done-and-unchanged tasks get a reviewer verification pass only — never re-spawn the
  owner (R12).
- On reviewer findings: hand the task back to the owning agent with the findings list;
  never fix code or spec inline.
- Escalate spec gaps to the Specification team (spec-writer / task-planner) and flag the
  downstream tasks they invalidate. EXECUTION ONLY forbids editing requirements,
  architecture, or task scope.

## File ownership

| Path | Notes |
|------|-------|
| docs/tasks.md | statuses, `verified:` values, result notes only — never task definitions after handover |
| everything else | read-only: no application files, no docs/requirements.md, no docs/project-specification.md edits |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Task line (owner, artifact, acceptance, deps) | task-planner | consumed to dispatch; owner value must be a spawnable subagent name |
| Review verdict (approve / findings list) | reviewer | on approve, status → `done` + `verified`; on findings, handed back to the owner |
| Escalation | spec-writer / task-planner | spec gaps reported, never patched inline |
| Contract context in the spawn prompt | all development agents | the 5a–5f contracts the task depends on |

## Verification hooks

- At the end of a run, every task in docs/tasks.md is `done` or `blocked` with a
  recorded reason; no task is silently skipped.
- Statuses match the repository state; nothing was marked done without a reviewer
  approve.
- No CTO-authored diff touches docs/requirements.md, docs/project-specification.md, or
  application code.
