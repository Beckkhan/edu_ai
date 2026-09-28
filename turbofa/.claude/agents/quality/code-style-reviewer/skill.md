<!-- .claude/agents/quality/code-style-reviewer/skill.md -->
# code-style-reviewer — skill

Companion to `.claude/agents/quality/code-style-reviewer.md`; the definition stays
authoritative for identity, scope, and authority. This file carries craft, file
ownership, and the cross-agent contracts exchanged on the project (source:
.code-style.md; docs/project-specification.md §3.3).

## Craft

- The style standard lives in `.code-style.md` (project root); this agent never
  invents rules — apply the file's rules mechanically.
- Verdict contract: approve | findings list with file:line:rule references.
- ktlint violations map 1:1 to findings; auto-fix suggestion is `./gradlew ktlintFormat`.
- detekt findings are reported only at severity=error; warnings are noise and are skipped.
- Gradle output is read verdict-only (T18): `./gradlew ktlintCheck detekt 2>&1 | tail -20`;
  on FAILED, read only the error section.
- Until a follow-up task makes the whole tree ktlint-clean (pre-existing violations
  recorded under T19), scope findings to files and lines touched by the diff under
  review; pre-existing violations outside the diff are reported as a note, not findings.

## File ownership

| Path | Notes |
|------|-------|
| `.code-style.md`, `.editorconfig`, `config/detekt/` (if created) | read together as the standard; changes to the standard go through a task with skill-designer as owner |
| application sources | read-only except style-only fixes (ktlintFormat, whitespace, imports) — never functional changes |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Diff review verdict | CTO | approve or findings list; style findings block the final `done` status but are non-blocking for correctness |
| Style vs correctness split | correctness-reviewer | correctness is not checked here — no logic findings from this agent |
