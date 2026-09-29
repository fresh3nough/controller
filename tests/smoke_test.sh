#!/usr/bin/env bash
# Local smoke tests: unit tests + APK existence + optional device checks.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$ROOT:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; NC=$'\033[0m'
pass() { echo "${GRN}PASS${NC} $*"; }
fail() { echo "${RED}FAIL${NC} $*"; exit 1; }
info() { echo "${YLW}--${NC} $*"; }

FAILED=0
check() {
  local name="$1"; shift
  if "$@"; then pass "$name"; else echo "${RED}FAIL${NC} $name"; FAILED=$((FAILED+1)); fi
}

info "Project root: $ROOT"

# 1) Wrapper present
check "gradlew exists" test -x "$ROOT/gradlew"

# 2) Unit tests
info "Running unit tests…"
./gradlew --no-daemon testDebugUnitTest
check "unit tests" test $? -eq 0

# 3) Debug APK build artifact
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  info "APK missing — assembling…"
  ./gradlew --no-daemon assembleDebug
fi
check "debug APK exists" test -f "$APK"
check "debug APK non-empty" test -s "$APK"

# 4) APK badging sanity (aapt)
AAPT="$ANDROID_HOME/build-tools/34.0.0/aapt"
if [[ -x "$AAPT" ]]; then
  BADGING="$($AAPT dump badging "$APK" 2>/dev/null || true)"
  echo "$BADGING" | grep -q "package: name='com.controller.xboxhid.debug'" \
    && pass "package id com.controller.xboxhid.debug" \
    || { echo "$BADGING" | head -5; fail "unexpected package id"; }
  echo "$BADGING" | grep -q "uses-permission: name='android.permission.BLUETOOTH_CONNECT'" \
    && pass "BLUETOOTH_CONNECT permission" \
    || fail "missing BLUETOOTH_CONNECT"
  echo "$BADGING" | grep -q "uses-permission: name='android.permission.BLUETOOTH_ADVERTISE'" \
    && pass "BLUETOOTH_ADVERTISE permission" \
    || fail "missing BLUETOOTH_ADVERTISE"
else
  info "aapt not found — skipping badging checks"
fi

# 5) Source structure
for f in \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/controller/xboxhid/MainActivity.kt \
  app/src/main/java/com/controller/xboxhid/hid/HidDescriptor.kt \
  app/src/main/java/com/controller/xboxhid/hid/InputReport.kt \
  app/src/main/java/com/controller/xboxhid/hid/HidDeviceManager.kt \
  app/src/main/java/com/controller/xboxhid/hid/HidControllerService.kt \
  app/src/main/java/com/controller/xboxhid/ui/ControllerScreen.kt
 do
  check "source $f" test -f "$ROOT/$f"
done

# 6) Optional device checks
if adb get-state 2>/dev/null | grep -q device; then
  pass "adb device connected"
  MODEL="$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  SDK="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  info "device model=$MODEL sdk=$SDK"
  if [[ "${SDK:-0}" -ge 28 ]]; then
    pass "device API >= 28 (HID_DEVICE supported)"
  else
    echo "${RED}FAIL${NC} device API $SDK < 28"
    FAILED=$((FAILED+1))
  fi
  # bluetooth feature
  if adb shell pm list features 2>/dev/null | grep -q "android.hardware.bluetooth"; then
    pass "device has bluetooth feature"
  else
    echo "${RED}FAIL${NC} no bluetooth feature"
    FAILED=$((FAILED+1))
  fi
else
  info "no adb device — skipping device checks"
fi

echo
if [[ "$FAILED" -eq 0 ]]; then
  echo "${GRN}All smoke tests passed.${NC}"
  exit 0
else
  echo "${RED}$FAILED smoke test(s) failed.${NC}"
  exit 1
fi
