<!-- .claude/agents/quality/reviewer/skill.md -->
# reviewer — skill

Companion to `.claude/agents/quality/reviewer.md`; the definition stays authoritative
for identity, scope, and authority. This file carries craft, file ownership, and the
cross-agent contracts exchanged on the project (source: docs/project-specification.md
§3.3, §4, §5; docs/tasks.md).

## Craft

- Read-only gate: propose fixes, never edit. Every finding is `file:line` + severity +
  required fix; no vague notes. Verdict is `approve` or a findings list, judged against
  the owner's Definition of Done, not extra criteria.
- Review order: acceptance criteria including failure paths → conventions and dependency
  direction (`config ← db/client/history/tool ← service ← routes`) → R10
  simplification/dead code/diff size → R8 six log points, json bodies, no secrets → R5
  grep for DDL/DML across the diff → test claims actually exist and pass.
- Scope check: the diff must touch only the task's `artifact:` paths and that owner's
  files; anything else is a finding.
- Contract spot-checks per owner: 5a exact labels/format; 5b `fueling_id`
  `"type": "string"` (not int); 5c fail-fast non-empty tools; 5d no Koog types leaking
  into `ChatService`; 5e history file cleared on restart; 5f SELECT-only and String ids.
- R12 mode: for done-and-unchanged tasks verify the existing diff still satisfies its
  acceptance criteria and re-run its tests — never re-execute the owner agent.
- T12 final scenario is the end-to-end gate: run the app and send two Bruno requests
  (prompt only; prompt + UUID). Verify the six R2 log points with json bodies (4 lines
  for a plain dialogue, 8 for a tool-call run), a
  non-empty tools array, the tool fired for the second request with data aggregated from
  the three DBs, no secrets in logs, and no DDL/DML anywhere in the codebase.

## File ownership

| Path | Notes |
|------|-------|
| — | owns no files; read-only tools only (Read, Glob, Grep, Bash) |
| review verdicts | returned as messages; the CTO flips docs/tasks.md on approve |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Task line + acceptance criteria | task-planner / CTO | the review baseline |
| Owner's skill.md (craft, ownership, contracts) | all agents | convention and boundary checks |
| Test results | test-engineer | evidence for coverage claims |
| Verdict | CTO | on approve: `done` + `verified`; on findings: back to the owner through the CTO |
| R5 no-DDL/DML and no-secrets checks | all development agents | blocking findings when violated |

## Verification hooks

- Every finding names a file and a line and is actionable without follow-up questions.
- No unrelated change passes silently; no task reaches `done` without an approve.
- The T12 scenario checklist above is verified end-to-end before the final verdict.
