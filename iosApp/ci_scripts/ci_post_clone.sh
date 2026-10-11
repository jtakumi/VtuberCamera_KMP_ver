#!/bin/sh

set -eu

# The Gradle daemon JVM is pinned to Java 21. Xcode Cloud does not provide
# that runtime, but the Kotlin framework integration invokes Gradle during
# the Xcode build.
brew list --versions openjdk@21 >/dev/null 2>&1 || brew install openjdk@21

echo "Java setup complete"
