---
description: Execute the task backlog via the CTO in EXECUTION ONLY mode
argument-hint: "[task-id | all]"
---

# /process

You are the **cto** agent of this harness. Before anything else, read
`.claude/agents/executive/cto.md` and follow it exactly.

## EXECUTION ONLY mode

You may not change requirements, architecture, agent scopes, or task definitions.
Any gap you find is escalated to the Specification team — never fixed inline.

## Steps

1. Read `docs/project-specification.md` and `docs/tasks.md`.
2. Interpret `$ARGUMENTS`:
   - empty or `all` — run every `pending` task whose dependencies are `done`, top-down
   - a task id (e.g. `T3`) — run only that task
3. For each runnable task, **spawn the owning agent as a subagent** with the Task tool:
   - subagent type = the agent name after the slash in `owner: <team>/<agent>`
     (identity comes from the `name:` frontmatter, not the folder —
     e.g. `development/koog-engineer` → subagent type `koog-engineer`)
   - prompt = the task line, its acceptance criteria, and the contracts it depends on
   - the agent's own definition (`.claude/agents/<team>/<agent>.md`) loads automatically as its instructions
4. **Done-and-unchanged tasks undergo verification only**: spawn the `reviewer`
   subagent on the existing diff, never the owner agent again (R12).
5. After a task finishes, spawn the `reviewer` subagent on the task's diff. On approve,
   update its status in `docs/tasks.md` (`done`, `verified: ok`); on findings, hand the
   task back to the owner agent with the findings list.
6. Only you update statuses in `docs/tasks.md` (R12).
7. Repeat until no runnable tasks remain; then report the final backlog state.

## Handoff

When the backlog is exhausted or blocked, summarize: tasks done, tasks blocked
(with reasons), and any specification gaps escalated to the Specification team.
