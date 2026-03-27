# Architecture Checklist (Android / iOS / KMP)

Use this checklist to validate findings consistently.

## 1) Layering and Boundaries

- UI layer only handles rendering and user intent mapping.
- State layer exposes immutable state and explicit actions/events.
- Domain logic is isolated from frameworks.
- Data/integration concerns are abstracted behind interfaces.
- Platform-specific APIs are isolated in platform modules.

## 2) Dependency Direction

- Dependencies point inward (UI -> domain -> data), not circularly.
- Shared KMP code depends on abstractions, not Android/iOS concrete classes.
- Constructors receive dependencies; avoid hidden singletons/service locators.

## 3) State Management

- Single source of truth per feature.
- State transitions are deterministic and observable.
- Side effects are explicit and cancellable.
- UI does not directly mutate deep services.

## 4) Concurrency and Lifecycle

- Thread/actor ownership is explicit.
- Long-running work is cancellable with lifecycle.
- Main-thread UI updates are centralized.
- Resource ownership (camera/session/tracker) has clear start-stop boundaries.

## 5) Error and Result Modeling

- Errors are modeled and propagated across boundaries.
- Recovery strategies are defined (retry, fallback, degrade gracefully).
- Platform-specific failure details are mapped into domain-safe models.

## 6) Testability

- Use cases and state reducers are unit-testable without framework runtime.
- External integrations are mockable via interfaces/protocols.
- Architecture supports deterministic tests for success/failure/cancellation.

## 7) Cross-Platform Consistency (KMP)

- Shared domain rules are not reimplemented separately on Android/iOS.
- Platform divergences are intentional and documented.
- `expect/actual` boundaries are small and purpose-driven.

## 8) Signals for Priority

Use these signals to assign severity:

- **P0**: data loss, persistent crashes, unrecoverable lifecycle/thread bugs.
- **P1**: high coupling, duplicated domain logic, difficult onboarding/testability blockers.
- **P2**: moderate maintainability issues and refactors that can be phased in.
