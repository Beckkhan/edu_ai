<!-- docs/requirements.md -->
# Requirements

Recorded verbatim from the stakeholder. Facts and requirements only — no solutions.

## Context / Current state

- New project turbofa: chat over fueling data. Package `com.eduai.turbofa`.
- Kotlin backend; Postgres is EXTERNAL (postgres.stage.turboapp.ru:25432),
  credentials in .env; tables fueling, payment, vendors ALREADY exist — the
  application reads them read-only and never creates schema.
- DeepSeek is stateless; all DeepSeek calls go through Koog.
- Client is Bruno; requests carry {"prompt": "...", "fueling_id": <int>}.

## Requirements

### R1 — Three teams, lead role — CTO
Three teams (design, implementation, testing), lead role — CTO.

### R2 — Agents/skills folder with rationale
Agents/skills folder with rationale; .claude/agents/<agent>/skill.md per agent.

### R3 — docs folder
A docs folder with specifications created by a dedicated skill/agent/team.

### R4 — DeepSeek is stateless
DeepSeek is stateless → message history in local cache + text file; the file is
cleared on restart.

### R5 — Kotlin; external Postgres
Kotlin; Postgres is EXTERNAL (postgres.stage.turboapp.ru:25432), credentials in .env;
tables fueling, payment, vendors ALREADY exist; the application reads them
read-only, no schema creation.

### R6 — All DeepSeek calls via Koog
All DeepSeek calls via Koog.

### R7 — Tool get_fueling_info
Tool get_fueling_info(fueling_id): gathers proliv data from tables fueling, payment,
vendors by fueling_id; JSON descriptor in resources/tools/get_fueling_info.json; the
tool actually performs the work (read-only DB queries); non-empty tools array in
DeepSeek requests.

### R8 — Five log points
Five log points, json body instead of ellipsis:
- Date/time Request from Bruno to backend: `<json body>`
- Date/time Request to Deepseek: `<json body>`
- Date/time Response from Deepseek: `<json body>`
- Date/time Tool call (if used): `<json body>`
- Date/time Response from backend to Bruno: `<json body>`

### R9 — Request contract and chain
Bruno sends {"prompt": "...", "fueling_id": <int>} (fueling_id optional).
- If fueling_id is present — the backend embeds it into the DeepSeek message, and the
  chain is: Bruno → backend → DeepSeek → tool get_fueling_info(fueling_id) → external
  DB → DeepSeek (summary) → backend → Bruno.
- If fueling_id is absent — normal dialogue without a tool call.

### R10 — No unnecessary abstractions
No unnecessary interfaces or abstractions when there is a single implementation;
simple readable code.

### R11 — Efficient token usage
Efficient token usage.

### R12 — Documentation first
Documentation first (*.md), then tasks (*.md), then execution via /process;
done-and-unchanged tasks undergo verification only; only the CTO updates statuses.

## Clarifications / Process notes

- The actual schema of fueling/payment/vendors is unknown at requirements time; the
  specification phase MUST discover it (query information_schema of the external DB)
  and record the columns and the fueling_id type in docs/project-specification.md
  BEFORE designing the tool SQL and the JSON descriptor.
- The external DB is a shared stage database: no migrations, no schema creation,
  no writes of any kind — SELECT only.
- .env holds the DB credentials and the DeepSeek key/model and is gitignored.
- No docker-compose: the database is external and already running.
- The harness lives entirely under .claude/: agent definitions in .claude/agents/
  organized by team subfolder (executive, specification, development, quality), the
  /process command in .claude/commands/, and the harness design doc at .claude/README.md.
