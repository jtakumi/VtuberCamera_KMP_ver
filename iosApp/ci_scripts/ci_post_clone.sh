#!/bin/sh

set -eu

# Xcode Cloud does not provide a Java runtime, but the Kotlin framework
# integration invokes Gradle during the Xcode build.
if ! java -version >/dev/null 2>&1; then
  brew list --versions openjdk@17 >/dev/null 2>&1 || brew install openjdk@17
fi

echo "Java setup complete"
