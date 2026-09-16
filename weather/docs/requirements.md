<!-- docs/requirements.md -->
# Requirements

Recorded verbatim from the stakeholder. Facts and requirements only — no solutions.

## Context / Current state

- Existing project: weather chat on Kotlin/Ktor: DeepSeek client, history (cache + file),
  weather detector with country clarification, Postgres 15-alpine (Docker, JDBC+HikariCP),
  a weather-save tool (exists but the backend does not use it for DB writes),
  tests (unit+integration).
- Migration to Koog partially started (commit "Rework weather DeepSeek client to use the
  Koog framework"), but agent documentation contradicts it.
- Stakeholder's goal: learn to build an agent harness; backend functionality is secondary;
  decision rationale matters.

## Requirements

### R1 — Koog for all DeepSeek calls
All requests to DeepSeek must go through Koog.

### R2 — Log format
All requests must be logged in the format:

- Date/time Request from Bruno to backend: `<json body>`
- Date/time Request to Deepseek: `<json body>`
- Date/time Response from Deepseek: `<json body>`
- Date/time Tool call (only if a tool is actually used for this request): `<json body>`
- Date/time Response to Bruno: `<json body>`

### R3 — Weather-save tool
A tool that saves current weather to the DB on request (city, date, time of receiving
the DeepSeek response with weather data). A JSON file describing the tool must exist in
the resources folder. Currently the tool exists but the backend does not use it for DB writes.

### R4 — Non-empty tools array
In backend→DeepSeek requests the "tools" field must be a non-empty array
("tools": [] is not acceptable).

### R5 — Harness structure
There must be a folder with own commands/agents/skills plus an explanation of why a
skill was chosen instead of an agent (or vice versa).

### R6 — Specifications and task execution
There must be a specifications folder created by a dedicated skill/agent/team. First the
solution is worked out with the AI and recorded in a *.md document; then tasks are derived
from it into another *.md; tasks are executed sequentially by a team launched via /process;
already completed and unchanged tasks undergo only a correctness check.

## Process requirements

- No hand-written code: all implementation happens via prompts to harness AI agents.
- /process is execution-only; documentation must exist before it is invoked.
