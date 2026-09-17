<!-- commands/process.md -->
---
description: Execute the task backlog via the CTO in EXECUTION ONLY mode
argument-hint: [task-id | all]
---

> Claude Code wiring: this harness document is exposed as a real slash command via
> `.claude/commands/process.md` — the two must stay in sync.

# /process

Drives the harness execution loop. Delegates to the **cto** agent
(`.claude/agents/cto.md`) in **EXECUTION ONLY** mode.

## What it does

1. Loads `docs/project-specification.md` and `docs/tasks.md`
2. Runs the task backlog top-down: every `todo` task whose dependencies are `done`
   is executed by spawning the owning agent as a subagent (SpecificationTeam,
   DevelopmentTeam, QualityTeam)
3. Spawns the reviewer subagent on each task's diff before it is marked done
4. Updates task statuses in `docs/tasks.md` after each task finishes
5. Escalates blockers and specification gaps back to SpecificationTeam — never fixes them inline

## Usage

- `/process` — run every runnable task until the backlog is exhausted
- `/process T3` — run a single task by id

## Hard rule

The CTO executes in EXECUTION ONLY mode: it may not change requirements,
architecture, agent scopes, or task definitions. Any such change must go through
the Specification Team and be re-planned into `docs/tasks.md` first.
