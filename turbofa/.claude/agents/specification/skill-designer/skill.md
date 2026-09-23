<!-- .claude/agents/specification/skill-designer/skill.md -->
# skill-designer — skill

Companion to `.claude/agents/specification/skill-designer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Agent-definition skeleton is frozen: YAML frontmatter (`name`, `description`, `tools`)
  plus body sections Role, Mission, Inputs, Outputs, Constraints, Workflow, Definition
  of Done — in that order, no ad-hoc sections per agent. The `name:` value, not the
  folder, is the spawn identity.
- skill.md format (this file set): a companion to the definition, plain markdown with no
  YAML frontmatter — the frontmatter contract belongs to agent definitions, so a
  skill.md is never itself a subagent. Sections: Craft, File ownership, Cross-agent
  contracts, Verification hooks. Document the definition; never duplicate it.
- Tool-descriptor format (R7; spec 5b) — the contract koog-engineer implements for every
  `src/main/resources/tools/*.json`:
  - `name` (string): must equal the LLM-visible tool name — `get_fueling_info`;
  - `description` (string): one sentence stating scope and that it is read-only;
  - `parameters` (JSON-Schema object): `"type": "object"`,
    `"properties": {"fueling_id": {"type": "string", "description": ...}}`;
  - `required` (array of strings): `["fueling_id"]`.
  `fueling_id` is a string UUID per the discovered schema (§6, D6) — never
  `"type": "integer"`. Descriptors load at startup and the tool set must be non-empty
  (5c): a missing or empty tool file is a startup failure, not a warning.
- Rationale currency (R2): .claude/README.md carries the folder map, one SKILL-vs-AGENT
  row per entity (12 agents + the `/process` skill), and a note that each agent also has
  a skill.md.
- Audit loop: definitions against the skeleton; each skill's ownership table against
  spec §3.3 (no file owned twice); contract references against 5a–5f; README table
  against the actual `.claude/agents/` tree and `.claude/commands/`.

## File ownership

| Path | Notes |
|------|-------|
| `.claude/agents/<team>/<agent>.md` | agent definitions — frontmatter and skeleton frozen |
| `.claude/agents/<team>/<agent>/skill.md` | the 12 skill files (R2, T13) |
| `.claude/README.md` | rationale table and folder map only |
| `src/main/resources/tools/*.json` | format only — the descriptor file itself is koog-engineer's (T1) |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Descriptor format 5b + `fueling_id` string rule | koog-engineer | implemented verbatim in T1 |
| skill.md per agent (craft, ownership, contracts) | all agents, reviewer | read at spawn and during review |
| Rationale table + folder map | CTO, task-planner | entity roster and spawn names |
| Spec §3.3/§4/§5 | spec-writer, architect | content source for skills and ownership |

## Verification hooks

- 12 definitions and 12 skill.md files exist, one per agent directory.
- Every definition carries exactly the skeleton sections; every skill carries Craft,
  File ownership, Cross-agent contracts, Verification hooks.
- README rationale table lists every entity (12 agents + `/process`) and the skill.md
  note; descriptor format uses `"type": "string"` for `fueling_id`.
