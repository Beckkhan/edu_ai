<!-- .claude/README.md -->
# Harness Design

The agent harness for the turbofa fueling-data chat backend (package `com.eduai.turbofa`).
Everything harness-related lives inside `.claude/` — one place, no duplication: agent
definitions organized by team subfolder in `.claude/agents/`, the `/process` entry point
in `.claude/commands/`, and this design document.

Every agent is a **real Claude Code subagent** defined in
`.claude/agents/<team>/<agent>.md`: YAML frontmatter (`name`, `description`, `tools`)
plus a body following the frozen skeleton (Role, Mission, Inputs, Outputs, Constraints,
Workflow, Definition of Done). The subagent type is the team-qualified path — e.g.
`development/koog-engineer` — which is exactly the `owner:` value used in docs/tasks.md,
so spawning is a 1:1 lookup.

## Folder map

```
.claude/
├── README.md                  # this design document (teams, rationale, workflow)
├── agents/                    # live subagent definitions, one team subfolder each
│   ├── executive/
│   │   └── cto.md                         # /process orchestrator, EXECUTION ONLY
│   ├── specification/
│   │   ├── architect.md                   # system design, tech stack, external schema discovery
│   │   ├── spec-writer.md                 # docs/project-specification.md from requirements
│   │   ├── task-planner.md                # docs/tasks.md backlog + owners
│   │   └── skill-designer.md              # agent-definition format, skill.md files, tool-descriptor format
│   ├── development/
│   │   ├── kotlin-engineer.md             # application glue (ChatService, history, wiring)
│   │   ├── koog-engineer.md               # all LLM interactions (Koog, get_fueling_info tool)
│   │   ├── logging-engineer.md            # R2 RequestLogger module + logback config
│   │   ├── api-client-engineer.md         # Bruno-facing API + config
│   │   └── data-engineer.md               # read-only JDBC over the external Postgres
│   └── quality/
│       ├── test-engineer.md               # unit tests + verification-only passes
│       └── reviewer.md                    # read-only diff review gate
└── commands/
    └── process.md             # slash command → runs the cto workflow

docs/
├── requirements.md            # raw stakeholder requirements (R1–R12)
├── project-specification.md   # solution document (spec-writer; created in spec phase)
└── tasks.md                   # ordered backlog (task-planner; executed by /process)

src/main/resources/tools/      # LLM tool descriptors (get_fueling_info.json)
logs/turbofa.log               # R2 event log (single FILE appender, truncated on restart)
```

## Team responsibilities

| Team | Agents | Responsibility |
|------|--------|----------------|
| Executive | cto | Spawns the owning agent per task, tracks statuses, escalates. Never changes the spec. |
| Specification | architect, spec-writer, task-planner, skill-designer | Turns `docs/requirements.md` into `docs/project-specification.md` and `docs/tasks.md`; discovers the external DB schema; owns agent definitions. |
| Development | kotlin-engineer, koog-engineer, logging-engineer, api-client-engineer, data-engineer | Implements tasks exactly as specified; each engineer owns a disjoint set of files. |
| Quality | test-engineer, reviewer | Proves behavior with tests and reviews diffs; a task is not done until approved and green. |

## Rationale: SKILL vs AGENT

| Entity | Kind | Rationale |
|--------|------|-----------|
| cto | AGENT | Persistent orchestrator with authority boundaries and its own spawn/status tools. |
| architect | AGENT | Owns system design decisions across the project lifetime, including the one-time external-schema discovery. |
| spec-writer | AGENT | Owns the specification document end-to-end (read requirements, write spec); needs file tools. |
| task-planner | AGENT | Maintains the backlog over many runs; dependency and ownership rules must persist. |
| skill-designer | AGENT | Owns the agent-definition format and every skill.md file — a standing deliverable (R2). |
| kotlin-engineer | AGENT | Owns a disjoint slice of application files (glue, history) and their tests. |
| koog-engineer | AGENT | Owns every LLM interaction; the "MUST use Koog" constraint lives in one agent (R6). |
| logging-engineer | AGENT | Single authority for what gets logged and what is redacted, referenced by other agents' constraints (R8). |
| api-client-engineer | AGENT | Owns the Bruno-facing API contract and configuration (R9). |
| data-engineer | AGENT | Owns the read-only JDBC layer over the external DB; the "no DDL/DML" invariant lives here (R5, R7). |
| test-engineer | AGENT | Test files and verification-only passes are standing ownership (R12). |
| reviewer | AGENT | Quality gate with a verdict contract (approve / findings list) applied to every task. |
| /process | SKILL | Stateless glue: reads the spec and backlog, runs the cto workflow, then exits. No state of its own. |

Each AGENT additionally gets a skill.md at `.claude/agents/<team>/<agent>/skill.md` (R2)
capturing its craft: conventions, file ownership, and cross-agent contracts — maintained
by skill-designer. Agents need persistent scope, tool sets, and isolated context; a skill
cannot hold ownership or authority, which is why every entity with standing deliverables
is an agent, and only the stateless entry points are skills.

## Workflow

1. Requirements land in `docs/requirements.md`.
2. Specification team: the architect discovers the external DB schema
   (information_schema, read-only) and designs the system; spec-writer records it all in
   `docs/project-specification.md`; task-planner decomposes it into `docs/tasks.md`
   (ordered backlog with owners, artifacts, dependencies).
3. `/process` runs the cto in EXECUTION ONLY mode: the cto reads the spec and backlog,
   spawns the owning agent (subagent type = the `owner: <team>/<agent>` value) per
   runnable task, then spawns reviewer on each diff, and updates task statuses in
   `docs/tasks.md`.
4. Development team implements; Quality team tests and reviews; a task reaches
   `done` only after an approved review. Done-and-unchanged tasks get a reviewer
   verification pass only (R12).
5. Spec gaps found during execution are escalated to the Specification team and
   re-planned — the cto never patches the spec inline.
