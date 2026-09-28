<!-- .claude/agents/quality/correctness-reviewer/skill.md -->
# correctness-reviewer — skill

Companion to `.claude/agents/quality/correctness-reviewer.md`; the definition stays
authoritative. Carries craft, file ownership, and cross-agent contracts (source:
docs/project-specification.md §3.3; docs/tasks.md).

## Craft

- Acceptance-criteria-first reading: map every acceptance bullet to code evidence
  before judging; failure paths get the same weight as the happy path.
- R5 sweep pattern: `grep -rniE "create\s+(table|index|database)|drop\s+|insert\s+into|update\s+\w+\s+set|delete\s+from"` over the diff.
- Secrets sweep pattern: `grep -rniE "deepseek_api_key|db_password|authorization"` over
  the diff and log statements.
- Test verification is verdict-only (T18): `./gradlew test --quiet 2>&1 | tail -20`;
  on failure run only the failing test: `./gradlew test --tests "ClassName.methodName"`.
- R12 verification-only passes: inspect the existing diff against its acceptance
  criteria, run its tests, report pass or findings — never re-implement.

## File ownership

| Path | Notes |
|------|-------|
| none | read-only by definition (no Write/Edit tools) |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Verdict | CTO | approve or findings list; findings hand the task back to the owner agent |
| Correctness vs style | code-style-reviewer | style is never checked here |
| Correctness vs history | git-reviewer | git hygiene is never checked here |
