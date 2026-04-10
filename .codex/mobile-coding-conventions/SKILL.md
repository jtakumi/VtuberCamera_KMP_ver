---
name: mobile-coding-conventions
description: Guide Android, iOS, and KMP implementation with this repo's mobile coding conventions. Use when writing, reviewing, or refactoring Kotlin or Swift code and you need checks for official naming and formatting, source file organization, method comments, naming consistency, swallowed errors, string resources, null safety, MVVM or ObservableObject responsibilities, Jetpack Compose or SwiftUI patterns, error handling, and final newline.
---

# Mobile Coding Conventions

## Overview

Apply the repository's mobile implementation conventions before or during code changes.
Use this skill to keep Kotlin and Swift code aligned with official style guidance and the project's UI/state-management expectations.

## Workflow

1. Identify the target surface first: Android, iOS, shared KMP, or a mixed change.
2. Read [references/conventions.md](references/conventions.md) and focus only on the sections relevant to that surface.
3. Check cross-cutting rules before style nits:
   - user-visible strings come from resources
   - nullability and failure states are handled explicitly
   - methods have a short comment above them when the function's role or side effects need explanation
   - package, file, type, and method names describe the same responsibility
   - UI state and business logic are owned by the correct layer
   - caught errors are not silently ignored
4. Apply platform-specific review:
   - Kotlin/KMP: file organization, naming, formatting, idiomatic Kotlin, public/shared API clarity
   - Swift/iOS: API naming, argument labels, DocC, state ownership, SwiftUI-friendly structure
5. Finish with the output checklist and call out any assumptions when context is partial.

## Focus Areas

- Kotlin / KMP conventions
- Swift / iOS conventions
- String resource usage
- Null safety and error handling
- Method-level intent comments
- Naming consistency across package, file, type, and method boundaries
- Swallowed error prevention
- MVVM and state ownership
- Compose / SwiftUI implementation shape
- Final hygiene checks such as trailing newline

## Output Expectations

When using this skill in a review or implementation task:

- prioritize violations that affect correctness, maintainability, or UX over low-signal style comments
- tie findings to specific files or code paths when possible
- distinguish confirmed issues from suggested improvements
- keep recommendations platform-aware and incremental

## Resources

- Read [references/conventions.md](references/conventions.md) for the full checklist and decision points.
- Treat [SKILL.md](../../.github/skills/mobile-coding-conventions/SKILL.md) as the upstream repo source to mirror when this Codex skill needs updating.
