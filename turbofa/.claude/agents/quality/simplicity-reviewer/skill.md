<!-- .claude/agents/quality/simplicity-reviewer/skill.md -->
# simplicity-reviewer — skill

Companion to `.claude/agents/quality/simplicity-reviewer.md`; the definition stays
authoritative. Carries craft, file ownership, and cross-agent contracts (source:
docs/project-specification.md §3.3; docs/tasks.md).

## Craft

- R10 checklist per diff: any new interface with one implementation? any class with
  one method and one caller that could be a function? any duplicated block (>5 lines)
  with a safe extraction point?
- Extraction findings must name the target location and the expected diff shrink —
  "extract this" without a destination is not actionable.
- Dependency direction: config ← db/client/history/tool ← service ← routes. Grep the
  diff's imports for arrows pointing backwards, e.g. `service/` importing from
  `routes/` or `config/` importing from `service/`.
- Oversized-diff threshold: >200 changed lines in one file without a justification in
  the task's acceptance criteria → finding, not automatic reject.

## File ownership

| Path | Notes |
|------|-------|
| none | read-only by definition (no Write/Edit tools) |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Verdict | CTO | approve or findings list; findings hand the task back to the owner agent |
| Simplicity vs correctness | correctness-reviewer | correctness is never checked here |
| Simplicity vs style | code-style-reviewer | style is never checked here |
