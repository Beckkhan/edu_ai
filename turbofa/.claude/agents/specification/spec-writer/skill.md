<!-- .claude/agents/specification/spec-writer/skill.md -->
# spec-writer — skill

Companion to `.claude/agents/specification/spec-writer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
docs/project-specification.md §3.3, §4, §5; docs/tasks.md).

## Craft

- Authoring order: goal/non-goals → tech stack (versions from build.gradle.kts) →
  architecture (request flow, logging boundaries, module map, team scopes) → key
  contracts 5a–5f → discovered schema §6 → decisions D1–D9 → escalations E1–E3.
- Quote discovery verbatim from the architect: column names, types, provenance date,
  read-only marker. Never an assumed schema — the requirements process note forbids it.
- Contract style: numbered IDs (`5a`–`5f` for interfaces, `D1`–`D9` for decisions) with
  input/output shapes, one owning agent each, phrased so the reviewer can check them
  mechanically (R12). Kotlin snippets for interfaces; JSON shapes for wire formats.
- Stale requirements: record the conflict, adopt the discovered fact, add an escalation
  ID — never edit docs/requirements.md. Precedents: E1 R9 `<int>` vs UUID text, E2
  "tables" vs databases, E3 `DB_URL` pointing at the admin database.
- Traceability: every R1–R12 maps to a section or contract, and the mapping stays stable
  so docs/tasks.md can carry the R/D/5x coverage rows.
- Freeze discipline: after handover the spec changes only through Specification-team
  re-planning on an escalation; the CTO and Development team never patch it inline.
- Uncertainty is stated, not smoothed over: unverified joins and best-effort lookups
  keep their "verified/unverified" qualifiers (D8, D9).

## File ownership

| Path | Notes |
|------|-------|
| docs/project-specification.md | sole writer, end-to-end |
| docs/requirements.md | read-only — stakeholder wording, verbatim |
| docs/tasks.md | read-only — task-planner's deliverable |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Architect's design + schema discovery | architect | consumed into spec §2/§3/§6 |
| 5a–5f (shapes, owners, invariants) | all development agents | task acceptance criteria cite them |
| R/D/5x IDs | task-planner | coverage table and `deps:` references |
| Verification baseline | reviewer | every acceptance criterion traces to a spec contract |
| E1–E3 | stakeholder (via CTO) | recorded escalations, not silent fixes |

## Verification hooks

- Every R1–R12 is traceable to a spec section or contract.
- `fueling_id` is documented as TEXT UUID (D6) and every contract referencing it says
  string.
- Each contract names its input, output, and owning agent; each decision carries Why and
  the rejected alternatives.
