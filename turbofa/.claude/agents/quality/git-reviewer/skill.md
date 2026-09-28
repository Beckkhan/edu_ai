<!-- .claude/agents/quality/git-reviewer/skill.md -->
# git-reviewer — skill

Companion to `.claude/agents/quality/git-reviewer.md`; the definition stays
authoritative. Carries craft, file ownership, and cross-agent contracts (source:
docs/project-specification.md §3.3; docs/tasks.md).

## Craft

- Message format check: `^(feat|fix|refactor|docs|test|chore)(\(.+\))?: .+` on the
  subject line; the body must state WHY (motivation/context), never restate the diff.
- Atomicity check: one commit that touches multiple owners' files or two unrelated
  concerns is a finding; the fix belongs to the owner agent (rebase/split), never here.
- Hygiene sweep: `git ls-files` must not contain `.env`, `logs/`, `build/` or
  `*.log`; binaries over 1MB need a justification in the commit body. `.gitignore`
  rules are verified against `git status --ignored` output, not by reading alone.

## File ownership

| Path | Notes |
|------|-------|
| none | read-only by definition (no Write/Edit tools) |

## Cross-agent contracts

| Contract | Counterpart | What crosses the boundary |
|----------|-------------|---------------------------|
| Verdict | CTO | approve or findings list; findings hand the task back to the owner agent |
| History vs code quality | correctness/simplicity/code-style reviewers | code quality is never checked here |
