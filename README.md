# Xbox HID Controller

Turn an **Android 9+** phone into a **Bluetooth Xbox-style gamepad**. Your MacBook (or any host) pairs it like a real controller — no Mac app required. Works with **Xbox Cloud Gaming** (`xbox.com/play`) via the browser Gamepad API.

```
Android App (Compose UI + HID Profile) ──Bluetooth──▶ Mac sees "Xbox Controller"
                                                          │
                                                          ▼
                                              Chrome / Safari → xbox.com/play
```

Typical latency: **~20–40 ms** over Bluetooth HID — playable for cloud gaming.

## Features

- Bluetooth **HID Device** mode (`BluetoothHidDevice`) — Android acts as the controller
- HID report descriptor for a standard **gamepad** (dual sticks, LT/RT, 12 buttons, hat D-pad)
- **Xbox One–style** on-screen UI (Jetpack Compose): sticks, A/B/X/Y, LB/RB, LT/RT, D-pad, View/Menu/Guide
- Foreground service pumps reports at **~125 Hz**
- One-shot **install script** via `adb`

## Requirements

| Piece | Version |
|--------|---------|
| Phone | Android **9+** (API 28), Bluetooth LE/Classic |
| Host | macOS (or Linux/Windows) with Bluetooth |
| Build machine | JDK **17**, Android SDK **34**, `adb` |
| Browser game | Chrome or Safari on [xbox.com/play](https://www.xbox.com/play) |

> **Note:** Some OEMs restrict the HID device profile. Pixel / stock Android works best. If `registerApp` fails, another HID app may already own the profile — force-stop it.

## Quick start

```bash
git clone https://github.com/fresh3nough/controller.git
cd controller

# 1) One-time host setup (SDK packages + gradle)
./scripts/setup.sh

# 2) Plug in the phone (USB debugging on), then build + install + launch
./scripts/install.sh

# 3) Optional verification
./tests/smoke_test.sh
```

### On the phone

1. Open **Xbox HID Controller**
2. Allow **Bluetooth** (and notifications) permissions
3. Tap **Connect** — status should move to **ADVERTISING**

### On the MacBook

1. **System Settings → Bluetooth**
2. Select **Xbox Controller** (or the phone’s advertised name) → **Connect**
3. Open [https://www.xbox.com/play](https://www.xbox.com/play) or [https://gamepad-tester.com](https://gamepad-tester.com)
4. Confirm the gamepad shows up, then play

### Manual adb install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.controller.xboxhid.debug/com.controller.xboxhid.MainActivity
```

## Project layout

```
controller/
├── app/src/main/java/com/controller/xboxhid/
│   ├── MainActivity.kt              # Permissions + Compose host
│   ├── hid/
│   │   ├── HidDescriptor.kt         # Xbox-style HID descriptor
│   │   ├── InputReport.kt           # Pack sticks/buttons → 15-byte report
│   │   ├── HidDeviceManager.kt      # BluetoothHidDevice register/connect/send
│   │   └── HidControllerService.kt  # Foreground service + 125 Hz pump
│   └── ui/
│       ├── ControllerScreen.kt      # Xbox One layout
│       ├── ControllerViewModel.kt
│       ├── components/              # Joystick, face buttons, D-pad, triggers
│       └── theme/
├── scripts/setup.sh                 # Host toolchain check
├── scripts/install.sh               # Build + adb install + grant perms
└── tests/smoke_test.sh              # Unit tests + APK + device checks
```

## HID report map

| Bytes | Field |
|------:|-------|
| 0–1 | Left stick X (uint16 LE, center 32768) |
| 2–3 | Left stick Y |
| 4–5 | Right stick X |
| 6–7 | Right stick Y |
| 8 | LT (0–255) |
| 9 | RT (0–255) |
| 10–11 | Buttons bitfield (A B X Y L1 R1 Back Start L3 R3 Guide Share) |
| 12 | Hat / D-pad (0=N … 7=NW, 8=release) |
| 13–14 | Reserved |

## Development

```bash
# Unit tests only
./gradlew testDebugUnitTest

# Full smoke suite
./tests/smoke_test.sh

# Reinstall quickly
./scripts/install.sh debug
```

### Permissions (manifest)

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (≤ API 30)
- `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN` (API 31+)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `POST_NOTIFICATIONS` (API 33+)

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `registerApp returned false` | Force-stop other HID/gamepad apps; toggle Bluetooth; reboot phone |
| Mac doesn’t see device | Phone must show **ADVERTISING**; put Mac Bluetooth UI in pairing mode; forget old pairings |
| Gamepad tester sees pad but no input | Ensure status is **CONNECTED**; move sticks — reports only flow while connected |
| Install unauthorized | `adb devices` → accept RSA fingerprint on phone |
| API < 28 | `BluetoothHidDevice` is unavailable |

## License

MIT
