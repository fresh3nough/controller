#!/usr/bin/env bash
# Build the debug APK and install it on a connected Android phone via adb.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ROOT:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

PKG_DEBUG="com.controller.xboxhid.debug"
PKG_RELEASE="com.controller.xboxhid"
APK_DEBUG="app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="app/build/outputs/apk/release/app-release-unsigned.apk"

BUILD_TYPE="${1:-debug}"
DO_LAUNCH=1
DO_GRANT=1

usage() {
  cat <<EOF
Usage: $(basename "$0") [debug|release] [--no-launch] [--no-grant]

Builds the Android Xbox HID controller APK and installs it on the first
authorized adb device.

Environment:
  ANDROID_HOME   Android SDK path (default: \$HOME/Android/Sdk)

After install:
  1. Open "Xbox HID Controller" on the phone
  2. Allow Bluetooth permissions
  3. Tap Connect
  4. On your Mac: System Settings → Bluetooth → pair "Xbox Controller"
  5. Open https://www.xbox.com/play in Chrome/Safari and play
EOF
}

for arg in "${@:2}"; do
  case "$arg" in
    --no-launch) DO_LAUNCH=0 ;;
    --no-grant) DO_GRANT=0 ;;
    -h|--help) usage; exit 0 ;;
  esac
done

echo "==> Checking adb device…"
if ! adb get-state 2>/dev/null | grep -q device; then
  echo "No authorized device. Connect the phone via USB, enable Developer Options"
  echo "and USB debugging, then accept the RSA prompt."
  adb devices -l
  exit 1
fi

adb devices -l
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
SDK="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Device: $MODEL (API $SDK)"

if [[ "$SDK" -lt 28 ]]; then
  echo "ERROR: Android 9+ (API 28) required for BluetoothHidDevice."
  exit 1
fi

echo "==> Building $BUILD_TYPE…"
case "$BUILD_TYPE" in
  debug)
    ./gradlew --no-daemon assembleDebug
    APK="$APK_DEBUG"
    PKG="$PKG_DEBUG"
    ;;
  release)
    ./gradlew --no-daemon assembleRelease
    APK="$APK_RELEASE"
    PKG="$PKG_RELEASE"
    ;;
  *)
    usage; exit 1 ;;
esac

if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK"
  exit 1
fi
echo "APK: $APK ($(du -h "$APK" | awk '{print $1}'))"

echo "==> Installing…"
# Replace existing install; allow downgrade for dev iteration
adb install -r -d "$APK"

if [[ "$DO_GRANT" -eq 1 ]]; then
  echo "==> Granting runtime permissions…"
  if [[ "$SDK" -ge 31 ]]; then
    adb shell pm grant "$PKG" android.permission.BLUETOOTH_CONNECT || true
    adb shell pm grant "$PKG" android.permission.BLUETOOTH_ADVERTISE || true
    adb shell pm grant "$PKG" android.permission.BLUETOOTH_SCAN || true
  else
    adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
  fi
  if [[ "$SDK" -ge 33 ]]; then
    adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  fi
fi

if [[ "$DO_LAUNCH" -eq 1 ]]; then
  echo "==> Launching…"
  adb shell am start -n "$PKG/com.controller.xboxhid.MainActivity" || \
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1
fi

cat <<EOF

Installed $PKG on $MODEL.

Next steps:
  • On phone: tap Connect (allow any Bluetooth prompts)
  • On MacBook: System Settings → Bluetooth → select "Xbox Controller" → Connect
  • Open https://www.xbox.com/play — the browser Gamepad API should see the pad
  • Optional check on Mac: hold Option in System Information → Bluetooth, or
    visit https://gamepad-tester.com

EOF
