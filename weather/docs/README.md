<!-- docs/README.md -->
# Document Flow

How requirements become shipped work, and which document each stage owns.

```
docs/requirements.md ──(SpecificationTeam)──▶ docs/project-specification.md
                                                    │
                                                    ▼
                                            docs/tasks.md ──(/process, CTO)──▶ statuses updated
```

## Documents

| Document | Written by | Purpose |
|----------|-----------|---------|
| `docs/requirements.md` | Product owner / humans | Raw input requirements. The only source of truth for what the product must do. |
| `docs/project-specification.md` | Specification Team (spec-writer) | Architecture: package tree, class contracts, tech stack, agent scopes and their skill files. |
| `docs/tasks.md` | Specification Team (task-planner) | Ordered, dependency-aware task backlog: every task has an owner, dependencies, and acceptance criteria. |

## Flow

1. **Requirements** — `docs/requirements.md` describes the desired behavior.
2. **Specification** — SpecificationTeam reads requirements and creates
   `docs/project-specification.md` (the design) and `docs/tasks.md` (the plan).
3. **Execution** — `/process` invokes the cto agent in EXECUTION ONLY mode. The cto
   reads `docs/project-specification.md` and `docs/tasks.md`, dispatches each runnable
   task to its owning agent, and updates task statuses in `docs/tasks.md`
   (`todo → in progress → done`, or `blocked` with a reason).
4. **Acceptance** — a task becomes `done` only after QualityTeam review and tests pass.
5. **Feedback** — spec gaps discovered during execution are escalated back to
   SpecificationTeam; the spec is updated and `docs/tasks.md` is re-planned.

## Rules

- `docs/requirements.md` is never edited by agents — requirements change only through the product owner.
- `docs/project-specification.md` and `docs/tasks.md` are owned by SpecificationTeam; the cto never edits them during execution.
- `docs/tasks.md` statuses are the single source of truth for progress and always reflect the repository state.
