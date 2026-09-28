# Token Efficiency Audit — inter-agent communication (T25)

Audit of what the CTO passes in spawn prompts, where it duplicates content the
subagent already loads, and how to shrink it without losing verifiability.
Sources: `.claude/agents/executive/cto.md`, `.claude/commands/process.md`,
`docs/project-specification.md` §5a–5f, the T23 /process run in session history.
Analysis only — no agent definition or application code was changed.

## 1. Current spawn prompt structure

The contract (`.claude/agents/executive/cto.md` Workflow step 3,
`.claude/commands/process.md` step 3): the prompt contains

1. the task line (owner, artifact, status) — verbatim,
2. its `acceptance:` / `deps:` bullets — verbatim,
3. "the contracts it depends on" (5a–5f) — **wording is ambiguous**: reference or
   verbatim text is not specified, and `.claude/agents/executive/cto/skill.md`
   says "Pass … the spec contracts (5a–5f) it touches" — also ambiguous.

What actually happened in the T23 run (session history):

| Prompt | Task line + acceptance | Restated rules from definitions/spec | Context notes | Total ≈ |
|--------|----------------------:|-------------------------------------:|--------------:|--------:|
| kotlin-engineer (owner) | ~90 | ~120 (FORBIDDEN/ALLOWED comment lists, already in the definition + .code-style.md) | ~110 | ~345 |
| correctness-reviewer | ~90 | ~150 (checks 1–5 restating its own Constraints) | ~150 | ~420 |
| simplicity / git / code-style reviewers | ~80 each | ~100 each | ~90 each | ~270 avg |
| 4 handback messages (SendMessage) | — | — | — | ~50 avg |

A full 5-gate run ≈ **1,650 tokens of prompts**, of which ~550 restate content the
subagent already loads for free (its own definition and skill.md load automatically
at spawn; the spec is one Read away).

Worst case implied by the ambiguous wording: if "the contracts it depends on" were
executed as verbatim pasting, every owner spawn would carry the full 5a–5f text —
**~1,390 tokens per spawn** (measured: 5a ≈ 490, 5b ≈ 120, 5c ≈ 70, 5d ≈ 205,
5e ≈ 82, 5f ≈ 426).

## 2. Identified redundancies

1. **Contract text by value (risk, not yet realized).** The spawn contract wording
   ("the contracts it depends on") permits pasting §5a–5f text. The spec is stable
   and on disk — the subagent has Read. A contract ID is 3 tokens; the text is
   ~1,390. Redundancy: ~1,390 tokens × every owner spawn.
2. **All contracts to every agent (no dependency map).** Each task touches 1–3 of
   the six contracts (data-engineer: 5f; logging-engineer: 5a; koog-engineer:
   5a–5d+5f; kotlin-engineer: 5d/5e …). Nothing in tasks.md or the spawn template
   pins *which* contracts a task needs, so a CTO that pastes at all pastes all six.
   Redundancy: ~800–1,200 tokens per spawn for contracts the agent cannot act on.
3. **Definition content restated in prompts.** The T23 reviewer prompts repeated
   scope boundaries ("Do NOT check style — that's code-style-reviewer's job") and
   verdict-only gradle rules that are already in each reviewer's Constraints.
   Redundancy: ~100–150 tokens per reviewer spawn, and drift risk (the prompt copy
   ages while the definition is maintained).
4. **Verdict-only rule restated per prompt.** The T18 `tail -20` / error-section
   instruction is in the definitions; it was re-explained in review prompts.
   Redundancy: ~40–60 tokens per gradle-running spawn.
5. **Acceptance criteria duplication (minor, acceptable).** `acceptance:` lives in
   docs/tasks.md and is copied into the prompt. It is the dispatch unit and the
   reviewer's verifiability anchor — worth its ~40–80 tokens; the alternative
   (subagent reads the backlog itself) saves tokens but costs a Read round-trip
   and weakens dispatch atomicity.
6. **Handback loops are already efficient.** SendMessage resumes the agent with its
   context — no re-send of the task line. Codify, don't fix.

## 3. Proposed optimizations (before/after)

**A. Contracts by reference, never by value.**
Before (worst case): prompt carries §5a text (378 words) for logging work.
After: `Contracts: 5a (docs/project-specification.md §5a) — read the section; never
paste contract text into prompts.` Savings: ~1,380 tokens per affected spawn.

**B. Per-task contract list instead of all six.**
Before: data-engineer receives 5a–5f.
After: data-engineer receives `Contracts: 5f`. The mapping is derivable today from
the task's `deps:` and artifact, but should be pinned explicitly — task-planner adds
a `contracts: 5f, 5b` sub-bullet to each backlog line (one 6-character field, no
spec change). Savings: ~1,100 tokens on the widest tasks, more on the narrow ones.

**C. No restated definition content in prompts.**
Before (T23 correctness-reviewer spawn): five "Checks per your constraints" bullets,
~150 tokens, all already in its definition.
After: `Verdict against your own Constraints; diff scope: <files>.` The agent's
definition loads automatically at spawn and is the authority. Savings: ~100–150
tokens per reviewer spawn.

**D. Standard spawn template (one place, in cto.md/process.md).**
```
Task T<n> <one-line subject>  |  owner: <agent>   (read your T<n> block in docs/tasks.md)
Contracts: <ids, e.g. 5a, 5f> — read the sections; text is never pasted
Diff scope: <files or "whole tree">
Verdict-only gradle per your Constraints. Report <deliverable>.
```
~90–120 tokens per owner spawn, ~60–80 per reviewer. Template lives in cto.md once,
so per-run phrasing drift stops too.

**E. Acceptance criteria: keep verbatim (recommended).** They are the verifiability
anchor for correctness-reviewer ("Verdict is against the agent's Definition of
Done") — the ~40–80 tokens buy mechanical verifiability. No change.

## 4. Token savings estimate

| Scenario | Per owner spawn | Per reviewer spawn | Full T23-style run (1 owner + 4 gates + 2 handbacks) |
|----------|----------------:|-------------------:|-----------------------------------------------------:|
| Worst case (contracts pasted verbatim + restated rules) | ~1,800 | ~450 | ~4,000 |
| Actual T23 run (measured) | ~345 | ~270 | ~1,650 |
| Optimized (B + C + D) | ~120 | ~80 | ~600 |

- vs worst case: **~85% reduction** (~3,400 tokens saved per task run)
- vs actual: **~64% reduction** (~1,050 tokens saved per task run)
- Per full backlog sweep (~15 tasks, the T1–T15 shape): **~16–50k tokens saved per
  /process run**, and every future run inherits the saving.

## 5. Implementation recommendations

(Not executed here — analysis only. Owners in parentheses.)

1. **cto.md** (skill-designer): amend Workflow step 3 with the spawn template from
   §3-D and a constraint: "Contract text is never pasted into prompts — the ID is
   passed, the subagent reads the spec section itself."
2. **process.md** (skill-designer): same template in step 3 of the /process command.
3. **task-planner**: add `contracts:` sub-bullet to every backlog line (pins the
   per-task contract list once, at planning time).
4. **spec**: no change needed — 5a–5f stay as-is and remain the single source.
5. **Code of review prompts**: reviewers are spawned with verdict context only
   (task id, diff scope); scope boundaries and gradle rules stay in the
   definitions, which already load at spawn.
6. **Keep**: acceptance criteria verbatim in prompts; SendMessage-based handbacks.
