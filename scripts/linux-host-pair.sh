#!/usr/bin/env bash
# Prepare BlueZ on Linux to accept the Android Bluetooth HID gamepad, then
# optionally trust/pair a phone MAC.
#
# Usage:
#   ./scripts/linux-host-pair.sh                  # make adapter ready
#   ./scripts/linux-host-pair.sh AA:BB:CC:DD:EE:FF
#
# Flow that works with this app:
#   1) Run this script (pairable + discoverable + NoInputNoOutput agent)
#   2) On phone: open Xbox HID Controller → Connect (status ADVERTISING)
#   3) Phone will try to bond/connect to computers it can see (e.g. "omarchy")
#      OR on Linux: bluetoothctl connect <phone> after trust
#   4) Verify: evtest / jstest / https://gamepad-tester.com
set -euo pipefail

PHONE_MAC="${1:-}"

echo "==> Ensuring bluetoothd is up"
systemctl is-active bluetooth >/dev/null || sudo systemctl start bluetooth

# Soft runtime config (does not require rewriting conf files every run)
echo "==> Adapter pairable + discoverable"
bluetoothctl <<EOF
power on
pairable on
discoverable on
agent NoInputNoOutput
default-agent
EOF

if [[ -n "$PHONE_MAC" ]]; then
  PHONE_MAC="$(echo "$PHONE_MAC" | tr 'a-f' 'A-F')"
  echo "==> Trusting $PHONE_MAC"
  bluetoothctl trust "$PHONE_MAC" || true
  echo "==> Removing stale pairing (so HID SDP is renegotiated)"
  bluetoothctl remove "$PHONE_MAC" 2>/dev/null || true
  sleep 1
  echo "==> Scanning briefly for phone…"
  bluetoothctl --timeout 8 scan on || true
  echo "==> Pair + connect $PHONE_MAC"
  # Pair may be initiated from the phone side instead; these are best-effort.
  bluetoothctl pair "$PHONE_MAC" || true
  bluetoothctl trust "$PHONE_MAC" || true
  # NOTE: bluetoothctl connect often tries HFP first on phones and can fail.
  # The Android app calls BluetoothHidDevice.connect() which is the reliable path.
  bluetoothctl connect "$PHONE_MAC" || true
  bluetoothctl info "$PHONE_MAC" || true
fi

cat <<EOF

Host is pairable/discoverable.

On the phone:
  1. Open Xbox HID Controller
  2. Tap Connect → wait for ADVERTISING
  3. App will bond to computer-class hosts and open the HID profile

On Linux verify input node:
  journalctl -u bluetooth -f
  # after CONNECTED:
  cat /proc/bus/input/devices | grep -A5 -i 'Pixel Gamepad\|Gamepad'
  # or:
  evtest

EOF
