---
name: mobile-architecture-review
description: Review Android and iOS codebases for architecture-level improvement opportunities and produce prioritized, actionable findings. Use when a user asks to assess maintainability, modularity, state management, dependency boundaries, testability, threading/concurrency safety, or platform separation in Kotlin/Swift/KMP projects.
---

# Mobile Architecture Review

## Overview

Use this skill to identify structural issues in Android and iOS implementations and propose concrete remediation steps.
Focus on architecture, not micro-style comments.

## Review Workflow

1. Map the code structure before judging quality.
2. Detect architecture smells using the checklist in `references/architecture-checklist.md`.
3. Validate each finding with file-level evidence.
4. Prioritize by impact and implementation risk.
5. Return a concise report with quick wins first.

## Scope Mapping

Start by classifying files into these layers when possible:

- UI/presentation (Compose/SwiftUI/ViewController)
- State holders (ViewModel/store/controller)
- Domain/use-case layer
- Data/integration layer (repositories, camera, tracking SDK wrappers, parsing)
- Platform bridge layer (Android/iOS-specific implementations, expect/actual)

If layering is unclear, call out the ambiguity as a finding.

## Detection Heuristics

Check for high-signal problems:

- Feature logic coupled directly to platform APIs.
- ViewModel/store doing I/O, parsing, camera, and rendering orchestration at once.
- One-way data flow violations (UI mutating deep dependencies directly).
- Missing protocol/interface seams around external libraries.
- Business rules duplicated across Android/iOS implementations.
- Shared KMP code depending on platform details without abstraction.
- Hidden concurrency assumptions (main-thread-only operations not encoded in architecture).
- Error handling and lifecycle ownership spread across multiple layers.
- Hard-to-test design (constructors directly instantiate concrete dependencies).

## Output Format

For each finding, provide:

- **Title**: short architecture issue.
- **Why it matters**: maintainability/reliability/product velocity impact.
- **Evidence**: specific file paths and brief observation.
- **Recommendation**: architecture-level fix and migration strategy.
- **Priority**: P0/P1/P2.
- **Effort**: S/M/L.

Then add:

- **Top 3 quick wins** (high impact, low effort)
- **Sequencing plan** (what to fix first and why)

## Guardrails

- Prefer incremental refactoring over full rewrites.
- Avoid suggesting patterns that conflict with existing stack unless benefit is explicit.
- Distinguish confirmed issues from hypotheses.
- If context is partial, state assumptions clearly.
- Keep recommendations platform-aware: Android-only, iOS-only, or shared KMP.

## Resources

- Use `references/architecture-checklist.md` as the primary review checklist.
