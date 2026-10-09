#!/usr/bin/env bash
#
# Generates iosApp/iosApp.xcodeproj from iosApp/project.yml with XcodeGen.
#
# The .xcodeproj is not tracked by git, so run this after cloning, after switching
# branches, and after editing iosApp/project.yml. CI and Bitrise call it before xcodebuild.
#
# Requires XcodeGen (macOS): `brew install xcodegen`.

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SPEC="${REPO_ROOT}/iosApp/project.yml"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "error: xcodegen not found. Install it with: brew install xcodegen" >&2
  exit 1
fi

if [[ ! -f "${SPEC}" ]]; then
  echo "error: ${SPEC} not found." >&2
  exit 1
fi

xcodegen --version
xcodegen generate --spec "${SPEC}" --project "${REPO_ROOT}/iosApp"
