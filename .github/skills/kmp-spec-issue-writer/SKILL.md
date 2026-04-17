---
name: kmp-spec-issue-writer
description: >
  Use when a confirmed documentation/implementation mismatch in
  VtuberCamera_KMP_ver should become a GitHub Issue.
---

# KMP Spec Issue Writer

## Overview

Use this skill when a documentation/implementation mismatch has already been
confirmed from repository code and docs, and you need to draft a high-quality
GitHub Issue.

This skill is downstream of `kmp-spec-sync`:
- `kmp-spec-sync` confirms and classifies the mismatch
- `kmp-spec-issue-writer` converts confirmed mismatch into an actionable Issue

## Rules

- Only create an issue after the mismatch is confirmed from code and docs.
- Do not open an issue for intentional Android/iOS asymmetry unless it causes a real problem.
- Prefer doc-fix issues when the code is correct and wording is stale.
- Prefer implementation issues when the spec describes an MVP requirement that the project still intends to ship.

## Output

Return all of the following:

- issue title
- labels suggestion
- background
- confirmed evidence
- scope
- acceptance criteria
- out-of-scope

## Label Suggestions

Pick only the labels that apply:

- `docs`
- `android`
- `ios`
- `shared`
- `mvp`
- `needs-confirmation`

## Acceptance Criteria Style

Use observable completion conditions, for example:

- README no longer states X as implemented
- spec marks X as planned
- iOS ownership is documented as `iosApp`
- Android CameraX path is named explicitly

## Issue Template

### Title
`[Spec Sync] <area>: <short problem>`

### Labels Suggestion
`docs`, `android`, `ios`, `shared`, `mvp`, `needs-confirmation` から必要なもののみ

### Background
- Why the mismatch exists now
- Why this issue should be tracked

### Confirmed Evidence
- README says: ...
- Spec says: ...
- Code shows: ...

### Scope
- What this issue must change
- Which platform(s) are affected

### Acceptance Criteria
- [ ] Observable result 1
- [ ] Observable result 2
- [ ] Observable result 3

### Out of Scope
- Explicitly list what this issue will not do

## Guardrails

- Do not include speculative behavior as evidence.
- If any key statement is unverifiable, include `needs-confirmation` and state the uncertainty explicitly.
- Keep issue wording implementation-aware: shared vs platform-specific ownership must be clear.
