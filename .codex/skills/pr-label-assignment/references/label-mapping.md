# PR Label Mapping

This Codex reference mirrors the workspace skill reference.
Use it to derive PR labels from changed files while keeping confirmed labels separate from provisional candidates.

## Confirmed Existing Labels

- `dependencies`
  - Source: `.github/dependabot.yml`
  - Use for dependency update PRs and Dependabot version bumps

- `automerge-candidate`
  - Source: `.github/workflows/dependabot-auto-merge.yml`
  - Use only for Dependabot PRs that match the workflow policy

- `manual-review-required`
  - Source: `.github/workflows/dependabot-auto-merge.yml`
  - Use only for Dependabot PRs that do not match the workflow policy

## Provisional Candidate Labels

- `android`
  - Android-specific source set and Android build changes

- `ios`
  - iOS-specific source set, Swift, Xcode, or iosApp changes

- `kmp`
  - Shared commonMain, expect/actual, or shared model changes

- `docs`
  - README, docs, specs, and other documentation-only changes

- `ci`
  - GitHub Actions, Bitrise, build automation, and workflow changes

- `bot`
  - discord-codex-bot, agent customization, or automation helper changes

## Heuristics

1. Prefer `dependencies` for dependency-only PRs.
2. Prefer `docs` alone for documentation-only changes.
3. Prefer `kmp` for shared changes and add platform labels only when they materially help.
4. Do not suggest workflow auto-merge labels outside the Dependabot context.
5. Keep the final set small and evidence-backed.
