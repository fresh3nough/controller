#!/usr/bin/env bash
# Host-side setup: verify JDK, Android SDK components, gradle wrapper.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "==> Java"
if ! command -v java >/dev/null; then
  echo "Install JDK 17+ first."; exit 1
fi
java -version

echo "==> Android SDK at $ANDROID_HOME"
if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "ANDROID_HOME not found. Install Android SDK / command-line tools."
  exit 1
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ -x "$SDKMANAGER" ]]; then
  echo "==> Ensuring platform android-34 + build-tools 34.0.0 + platform-tools"
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
else
  echo "sdkmanager not found — assuming packages already installed."
fi

echo "==> adb"
adb version | head -1

if [[ ! -x "$ROOT/gradlew" ]]; then
  echo "==> Generating Gradle wrapper"
  if command -v gradle >/dev/null; then
    gradle wrapper --gradle-version 8.7
  else
    echo "gradlew missing and no system gradle — re-run from a machine with gradle once."
    exit 1
  fi
fi

echo "==> Resolving dependencies (first build may take a few minutes)"
./gradlew --no-daemon tasks >/dev/null

echo
echo "Setup OK. Connect your phone with USB debugging, then:"
echo "  ./scripts/install.sh"
echo "  ./tests/smoke_test.sh"
