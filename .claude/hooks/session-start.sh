#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environment
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 300000}'

cd "${CLAUDE_PROJECT_DIR}"

# ── 1. Fix Gradle daemon JVM vendor constraint ────────────────────────────────
# gradle/gradle-daemon-jvm.properties defaults to JetBrains JDK which can't be
# provisioned in all environments. Remove the vendor line so Gradle accepts the
# available OpenJDK 21 instead.
DAEMON_JVM_PROPS="${CLAUDE_PROJECT_DIR}/gradle/gradle-daemon-jvm.properties"
if [ -f "$DAEMON_JVM_PROPS" ] && grep -q "toolchainVendor" "$DAEMON_JVM_PROPS"; then
  sed -i '/toolchainVendor/d' "$DAEMON_JVM_PROPS"
fi

# ── 2. Android SDK setup ──────────────────────────────────────────────────────
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  echo "[session-start] Downloading Android cmdline-tools..."
  if curl -sf --max-time 120 -o /tmp/cmdline-tools.zip "$CMDLINE_TOOLS_URL"; then
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
    mv /tmp/cmdline-tools-extract/cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"
    rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extract
    echo "[session-start] Android cmdline-tools installed."
  else
    echo "[session-start] WARNING: Could not download Android cmdline-tools. Skipping Android SDK setup."
  fi
fi

if [ -x "$SDKMANAGER" ]; then
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  echo "export ANDROID_HOME=$ANDROID_SDK_ROOT" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools" >> "$CLAUDE_ENV_FILE"

  echo "[session-start] Accepting Android SDK licenses..."
  yes | "$SDKMANAGER" --licenses 2>/dev/null || true

  echo "[session-start] Installing Android SDK components..."
  "$SDKMANAGER" \
    "build-tools;36.0.0" \
    "platforms;android-36" \
    "platform-tools" || true
fi

# ── 3. Download Gradle dependencies ──────────────────────────────────────────
echo "[session-start] Warming up Gradle dependency cache..."
./gradlew :composeApp:dependencies --no-daemon --quiet 2>/dev/null || \
  echo "[session-start] WARNING: Gradle dependency download incomplete (network or SDK issue)."

# ── 4. Node.js dependencies for discord-codex-bot ────────────────────────────
if [ -f "${CLAUDE_PROJECT_DIR}/discord-codex-bot/package.json" ]; then
  echo "[session-start] Installing npm packages for discord-codex-bot..."
  cd "${CLAUDE_PROJECT_DIR}/discord-codex-bot"
  npm install
  cd "${CLAUDE_PROJECT_DIR}"
fi

echo "[session-start] Done."
