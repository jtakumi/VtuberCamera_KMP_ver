---
name: kmp-spec-sync
description: >
  Use when checking whether docs/KMP_IMPLEMENTATION_SPEC.ja.md,
  README, and the current Android/iOS implementation are aligned.
  Detect spec-vs-code gaps, classify them, and propose repo-specific
  follow-up actions or GitHub Issues without guessing.
---

# KMP Spec Sync

## Overview

Apply this skill when you need to compare the current implementation of
`VtuberCamera_KMP_ver` with its implementation spec and README.

This repository has an important split:
- Android camera implementation lives mainly in `composeApp/src/androidMain`
- shared UI/state lives in `composeApp/src/commonMain`
- iOS real camera implementation currently lives mainly in `composeApp/src/iosMain` (host app is `iosApp`)
- `docs/KMP_IMPLEMENTATION_SPEC.ja.md` includes future-facing scope, so it must
  not be treated as proof that a feature already exists

The goal is to keep documentation useful without rewriting history or guessing.

## Primary Rules

1. Never infer implementation from the spec alone.
2. Treat README and current source code as the source of truth for "what exists now".
3. Treat `docs/KMP_IMPLEMENTATION_SPEC.ja.md` as design intent unless code confirms it.
4. Do not describe Android and iOS as equally shared if the implementation is asymmetric.
5. Before proposing changes, read repository-level guidance and coding rules:
   - `.codex/AGENTS.md`
   - `.codex/mobile-coding-conventions/SKILL.md`

## Files To Check First

1. `README.md`
2. `docs/KMP_IMPLEMENTATION_SPEC.ja.md`
3. `composeApp/src/commonMain`
4. `composeApp/src/androidMain`
5. `composeApp/src/iosMain`
6. `iosApp`
7. `gradle/libs.versions.toml`
8. `.github/workflows` (if automation/CI is involved)

## What To Classify

When you find a difference, classify it into exactly one bucket:

### A. Spec ahead of code
The spec describes behavior that is not implemented yet.

Action:
- keep the spec if still planned
- rewrite wording to clearly say "planned" / "not yet implemented"
- optionally propose an Issue when the gap is important

### B. Code ahead of spec
Code has behavior not documented in spec/README.

Action:
- propose doc updates grounded in current code
- cite relevant file paths in the summary

### C. README/spec mismatch
README and spec disagree about current implementation state.

Action:
- prefer current code + README for present-tense status
- revise spec wording or move future-facing content into roadmap language

### D. Intentional platform asymmetry
Android and iOS differ because implementation is not fully shared yet.

Action:
- do not report as a bug by default
- explain the split explicitly
- open an Issue only when asymmetry blocks MVP or creates maintenance pain

### E. Unverifiable claim
A document statement cannot be confirmed from inspected code.

Action:
- mark as "needs human confirmation"
- do not convert into a confident summary

## Required Output Shape

Always return:

1. **Current confirmed reality**
   - what is implemented now
   - by platform
   - with no future features mixed in

2. **Detected gaps**
   - grouped by A/B/C/D/E

3. **Suggested next action**
   - doc-only fix
   - code change
   - GitHub Issue
   - human confirmation needed

4. **Issue draft when useful**
   Include:
   - title
   - problem
   - evidence
   - acceptance criteria
   - affected platform(s)

## Repo-Specific Review Focus

### Shared vs platform ownership
Check whether behavior is truly shared in `commonMain` or only shared at UI/state level,
while camera execution remains platform-specific.

### MVP status wording
This repo currently centers on camera MVP.
Do not present AR / VRM / Filament / avatar control as implemented unless code proves it.

### iOS wording
If iOS camera behavior is implemented in `composeApp/src/iosMain`, say so clearly.
Do not imply `iosApp` alone owns the full camera implementation.

### Dependency claims
Only mention CameraX, AVFoundation, Photos, ML Kit, ExifInterface, etc. when confirmed by source
or dependency definitions.

## Issue Draft Template

### Title
[Spec Sync] <feature or document area>: <short problem>

### Problem
The current documentation and implementation are out of sync.

### Evidence
- README says: ...
- Spec says: ...
- Code currently shows: ...

### Why it matters
- onboarding confusion
- wrong implementation expectations
- test design mismatch
- platform ownership confusion

### Acceptance Criteria
- [ ] README present-tense status matches code
- [ ] spec marks planned items as planned
- [ ] Android/iOS ownership is explicit
- [ ] no future-only features are described as shipped

## Anti-Patterns

Do not:
- guess behavior from naming alone
- rewrite the spec as if every planned item is already implemented
- flatten Android/iOS differences into fake "shared" architecture
- file issues for every asymmetry without checking intent
- report speculative API behavior without code evidence
