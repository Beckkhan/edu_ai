<!-- agents/README.md -->
# Harness Design

The agent harness for the weather chat application. Every agent is a **real Claude Code
subagent** defined in `.claude/agents/<agent>.md`: YAML frontmatter (`name`, `description`,
`tools`) plus a body following the frozen skeleton (Role, Mission, Inputs, Outputs,
Constraints, Workflow, Definition of Done).

## Folder map

```
.claude/
├── agents/                   # live subagent definitions (one file per agent)
│   ├── cto.md                #   /process orchestrator, EXECUTION ONLY
│   ├── architect.md          #   system design, tech stack, contracts
│   ├── spec-writer.md        #   docs/project-specification.md from requirements
│   ├── task-planner.md       #   docs/tasks.md backlog + owners
│   ├── skill-designer.md     #   agent-definition format + files
│   ├── kotlin-engineer.md    #   application glue (ChatService, history, wiring)
│   ├── koog-engineer.md      #   all LLM interactions (Koog, WeatherAgent, tools)
│   ├── logging-engineer.md   #   R2 RequestLogger module
│   ├── api-client-engineer.md#   Bruno-facing API + config
│   ├── data-engineer.md      #   Postgres + JDBC persistence
│   ├── core-engineer.md      #   retired (scope absorbed, redirects only)
│   ├── reviewer.md           #   read-only diff review gate
│   ├── test-architect.md     #   test strategy
│   ├── unit-test-engineer.md #   fast isolated tests
│   └── integration-test-engineer.md # tests against Docker Postgres
└── commands/
    └── process.md            # slash command → spawns the cto subagent

agents/
└── README.md                 # this design document (teams, rationale, workflow)

docs/
├── requirements.md           # raw stakeholder requirements (R1–R6)
├── project-specification.md  # solution document (spec-writer)
├── tasks.md                  # ordered backlog (task-planner, executed by /process)
└── README.md                 # document flow description
```

## Team responsibilities

| Team | Agents | Responsibility |
|------|--------|----------------|
| Executive | cto | Spawns the owning agent per task, tracks statuses, escalates. Never changes the spec. |
| Specification | architect, spec-writer, task-planner, skill-designer | Turns `docs/requirements.md` into `docs/project-specification.md` and `docs/tasks.md`; owns agent definitions. |
| Development | kotlin-engineer, koog-engineer, logging-engineer, api-client-engineer, data-engineer (core-engineer retired) | Implements tasks exactly as specified; each engineer owns a disjoint set of files. |
| Quality | reviewer, test-architect, unit-test-engineer, integration-test-engineer | Reviews diffs and proves behavior with tests; a task is not done until approved and green. |

## Rationale: SKILL vs AGENT

| Entity | Kind | Rationale |
|--------|------|-----------|
| cto | AGENT | Persistent orchestrator with authority boundaries and its own spawn/status tools. |
| architect | AGENT | Owns system design decisions across the project lifetime, not a single invocation. |
| spec-writer | AGENT | Owns the specification document end-to-end (read requirements, write spec); needs file tools. |
| task-planner | AGENT | Maintains the backlog over many runs; dependency and ownership rules must persist. |
| skill-designer | AGENT | Owns the agent-definition format and every agent file — a standing deliverable. |
| kotlin-engineer | AGENT | Owns a disjoint slice of application files and its own tests. |
| koog-engineer | AGENT | Owns every LLM interaction; the "MUST use Koog" constraint lives in one agent. |
| logging-engineer | AGENT | Single authority for what gets logged and what is redacted, referenced by other agents' constraints. |
| api-client-engineer | AGENT | Owns the Bruno-facing API contract and configuration. |
| data-engineer | AGENT | Owns the DB layer: schema, JDBC repository, datasource. |
| test-engineer (test-architect / unit / integration) | AGENT | Test strategy and test files are standing ownership, split by speed class. |
| reviewer | AGENT | Quality gate with a verdict contract (approve / findings list) applied to every task. |
| core-engineer | AGENT (retired) | Kept as a stub so stale task references redirect instead of failing silently. |
| /process | SKILL | Stateless glue: reads the spec and backlog, spawns the cto subagent, then exits. No state of its own. |

Note: before real spawning, these entities were documented as skills; they became AGENTs
because each needs its own persistent scope, tool set, and isolated context — the original
SKILL/AGENT rationale per entity is superseded by this table.

## Workflow

1. Requirements land in `docs/requirements.md`.
2. SpecificationTeam (spec-writer) turns them into `docs/project-specification.md`
   (goal, tech stack, architecture, team responsibilities, key contracts) and
   (task-planner) `docs/tasks.md` (ordered backlog with owners, artifacts, dependencies).
3. `/process` spawns the cto in EXECUTION ONLY mode: the cto reads the spec and backlog,
   spawns the owning agent (subagent) per runnable task, then spawns reviewer on each diff,
   and updates task statuses in `docs/tasks.md`.
4. DevelopmentTeam implements; QualityTeam tests and reviews; a task reaches
   `done` only after an approved review.
5. Spec gaps found during execution are escalated to SpecificationTeam and
   re-planned — the cto never patches the spec inline.
