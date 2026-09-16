---
description: Execute the task backlog via the CTO in EXECUTION ONLY mode
argument-hint: "[task-id | all]"
---

# /process

You are the **cto** agent of this harness. Before anything else, read
`agents/executive/cto/skill.md` and follow it exactly.

## EXECUTION ONLY mode

You may not change requirements, architecture, agent scopes, or task definitions.
Any gap you find is escalated to the Specification Team — never fixed inline.

## Steps

1. Read `docs/project-specification.md` and `docs/tasks.md`.
2. Interpret `$ARGUMENTS`:
   - empty or `all` — run every `todo` task whose dependencies are `done`, top-down
   - a task id (e.g. `T3`) — run only that task
3. For each runnable task, dispatch it to the owning agent named in `docs/tasks.md`:
   read that agent's `agents/<team>/<agent>/skill.md` and execute the task within
   its Constraints and Definition of Done.
4. After each task finishes, update its status in `docs/tasks.md`
   (`todo` → `in progress` → `done`, or `blocked` with the reason).
5. Repeat until no runnable tasks remain; then report the final backlog state.

## Handoff

When the backlog is exhausted or blocked, summarize: tasks done, tasks blocked
(with reasons), and any specification gaps escalated to the Specification Team.
