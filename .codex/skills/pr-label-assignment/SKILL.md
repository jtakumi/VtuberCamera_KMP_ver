---
name: pr-label-assignment
description: Assign PR labels when creating or updating a pull request. Use when opening a PR with gh pr create or gh pr edit, and you need to derive labels from changed files, diff scope, bot/dependency context, and repo conventions before applying them.
---

# PR Label Assignment

## Overview

Apply labels to a pull request from changed files and diff scope before running `gh pr create` or `gh pr edit`.
Use this skill to keep PR labeling consistent and to separate confirmed labels from provisional candidates.

## Workflow

1. Decide whether the task is for a new PR or an existing PR.
2. Inspect changed files first, then read the diff only where label decisions remain unclear.
3. Derive label candidates from repo-confirmed labels and the initial mapping in [references/label-mapping.md](references/label-mapping.md).
4. Resolve conflicts such as `automerge-candidate` versus `manual-review-required`.
5. Build the exact `gh pr create --label ...` or `gh pr edit --add-label ...` command.
6. Report applied labels, excluded labels, evidence, and any low-confidence candidates that still need user confirmation.

## Output Expectations

When using this skill:

- prefer changed-files evidence over title-only guesses
- keep labels minimal and responsibility-oriented
- separate confirmed existing labels from provisional candidate labels
- do not propose workflow-only labels without matching conditions
- produce a runnable `gh` command when enough information exists

## Resources

- Read [references/label-mapping.md](references/label-mapping.md) for the initial label mapping and heuristics.
- Treat [SKILL.md](../../../.github/skills/pr-label-assignment/SKILL.md) as the upstream repo source to mirror when this Codex skill needs updating.
