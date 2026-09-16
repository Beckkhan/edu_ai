<!-- agents/README.md -->
# Harness Design

The agent harness for the weather chat application. Every agent is a folder with a
single `skill.md`, written against a frozen skeleton: Role, Mission, Inputs, Outputs,
Constraints, Workflow, Definition of Done.

## Folder map

```
agents/
├── executive/               # Orchestration
│   └── cto/                 #   /process entry point, EXECUTION ONLY
├── specification/           # What to build (renamed from design/)
│   ├── architect/           #   system design, tech stack, contracts
│   ├── spec-writer/         #   docs/project-specification.md from requirements
│   ├── task-planner/        #   docs/tasks.md backlog + owners
│   └── skill-designer/      #   skill.md author for every agent
├── development/             # Building it (renamed from implementation/)
│   ├── kotlin-engineer/     #   Kotlin/JVM application code
│   ├── koog-engineer/       #   all LLM interactions via the Koog framework
│   ├── logging-engineer/    #   logging: HTTP, tool calls, DB operations
│   ├── api-client-engineer/ #   DeepSeek API client
│   ├── core-engineer/       #   chat pipeline, tools, routes
│   └── data-engineer/       #   Postgres + JDBC persistence
└── quality/                 # Proving it (renamed from testing/)
    ├── reviewer/            #   diff review gate before a task is done
    ├── test-architect/      #   test strategy and ownership
    ├── unit-test-engineer/  #   fast isolated tests (MockK, coroutines-test)
    └── integration-test-engineer/  # tests against real Docker Postgres

commands/
└── process.md               # slash command → CTO, EXECUTION ONLY
```

## Team responsibilities

| Team | Folder | Responsibility |
|------|--------|----------------|
| Executive | `agents/executive/` | Runs the harness: dispatches tasks, tracks statuses, escalates. Never changes the spec. |
| Specification | `agents/specification/` | Turns `docs/requirements.md` into `docs/project-specification.md` and `docs/tasks.md`; owns agent scopes and skill files. |
| Development | `agents/development/` | Implements tasks exactly as specified; every engineer owns a disjoint set of files. |
| Quality | `agents/quality/` | Reviews diffs and proves behavior with tests; a task is not done until approved and green. |

## Rationale: SKILL vs AGENT

| Entity | Kind | Rationale |
|--------|------|-----------|
| cto | AGENT | Persistent orchestrator with authority boundaries (EXECUTION ONLY) and an escalation contract. |
| spec-writer | SKILL | Creating documentation from template — deterministic process. |
| skill-designer | AGENT | Owns the skill-file format and every agent's skill.md — a standing deliverable, not a one-off. |
| task-planner | AGENT | Maintains the backlog over many runs; dependency and ownership rules must persist. |
| architect | AGENT | Owns system design decisions across the project lifetime, not a single invocation. |
| kotlin-engineer | AGENT | Owns a disjoint slice of application files and its own tests. |
| koog-engineer | AGENT | Owns every LLM interaction; the "MUST use Koog" constraint lives in one skill. |
| logging-engineer | AGENT | Single authority for what gets logged and what is redacted, referenced by other engineers' constraints. |
| test-engineer (test-architect / unit / integration) | AGENT | Test strategy, mock boundaries, and test files are standing ownership, split by speed class. |
| reviewer | AGENT | Quality gate with a verdict contract (approve / findings list) applied to every task. |
| /process | SKILL | Stateless glue: reads the spec and backlog, invokes the cto agent, then exits. No state of its own. |

## Workflow

1. Requirements land in `docs/requirements.md`.
2. SpecificationTeam turns them into `docs/project-specification.md` (spec-writer:
   goal, tech stack, architecture, team responsibilities, key contracts) and
   `docs/tasks.md` (task-planner: ordered backlog with owners, artifacts,
   dependencies).
3. `/process` triggers the cto in EXECUTION ONLY mode: it reads
   `docs/project-specification.md` and `docs/tasks.md`, dispatches every runnable
   task to its owning agent, and updates task statuses in `docs/tasks.md`.
4. DevelopmentTeam implements; QualityTeam tests and reviews; a task reaches
   `done` only after an approved review.
5. Spec gaps found during execution are escalated to SpecificationTeam and
   re-planned — the cto never patches the spec inline.
