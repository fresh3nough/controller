# Pixel Gamepad

Turn an **Android 9+** phone into a **Bluetooth Xbox-style gamepad** with a neon cyberpunk skin. Your MacBook pairs it like a real controller — no Mac app required. Works with **Xbox Cloud Gaming** (`xbox.com/play`) via the browser Gamepad API.

**v1.4.3** — Right-stick look Y on HID Rx (Chrome `axes[3]`), L3/R3 no longer fire on stick drag, larger ABXY, L3↔D-pad gap, R3 unclipped. **v1.4.1** — Stick Y polarity (look up = HID 0). **v1.4.0** — D-pad dual-report + cyberpunk neon UI.

```
Android App (Compose UI + HID Profile) ──Bluetooth──▶ Mac sees "Pixel Gamepad"
                                                          │
                                                          ▼
                                              Chrome / Safari → xbox.com/play
```

Typical latency: **~20–40 ms** over Bluetooth HID — playable for cloud gaming.

## Features

- Bluetooth **HID Device** mode (`BluetoothHidDevice`) — Android acts as the controller
- HID report descriptor for a standard **gamepad** (8-bit dual sticks, LT/RT, 16 buttons, hat D-pad)
- **D-pad dual-report**: hat switch **and** buttons 13–16 so Chrome / Xbox Cloud map Up/Down/Left/Right correctly
- **Cyberpunk neon** UI (red / black / orange) — Jetpack Compose Xbox One geometry
- Equal-size dual sticks; face buttons +20% fatter; extra gap between A and right stick
- **L3 / R3 independent** of stick drag (no accidental click while walking)
- Right stick Y mapped to HID **Rx** so Chrome Gamepad API `axes[3]` gets look vertical
- Adaptive layout: L3↔D-pad gap, larger D-pad, R3 fully on-screen
- Advertises as **Pixel Gamepad** (generic HID) so macOS does not expect proprietary Xbox reports
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
2. Select **Pixel Gamepad** / the phone name → **Connect**
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
│   │   ├── HidDescriptor.kt         # Standard gamepad HID descriptor
│   │   ├── InputReport.kt           # Pack sticks/buttons → 9-byte report
│   │   ├── HidDeviceManager.kt      # BluetoothHidDevice register/connect/send
│   │   └── HidControllerService.kt  # Foreground service + 125 Hz pump
│   └── ui/
│       ├── ControllerScreen.kt      # Xbox One geometry layout
│       ├── ControllerViewModel.kt
│       ├── components/              # Joystick, face buttons, D-pad, triggers
│       └── theme/
├── scripts/setup.sh                 # Host toolchain check
├── scripts/install.sh               # Build + adb install + grant perms
└── tests/smoke_test.sh              # Unit tests + APK + device checks
```

## HID report map (report id 1)

| Byte | Field |
|-----:|-------|
| 0–1 | Buttons bitfield (A B X Y L1 R1 L2 R2 / Back Start L3 R3 Guide Share) |
| 2 | Hat / D-pad (0=N … 7=NW, 8=release) |
| 3 | Left stick X (uint8, center 128) |
| 4 | Left stick Y |
| 5 | Right stick X |
| 6 | Right stick Y |
| 7 | LT (0–255) |
| 8 | RT (0–255) |

Advertised name: **Pixel Gamepad** (generic HID gamepad subclass `0x02`).

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


## Camera looks at the ground (Dragonwilds / cloud games)

v1.4.1 packs stick **up → HID 0** (Chrome standard). Keep in-game **Invert Y / Vertical** **unchecked**.
If look is still flipped after updating the app, toggle Invert Y once, or re-pair Bluetooth.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `registerApp returned false` | Force-stop other HID/gamepad apps; toggle Bluetooth; reboot phone |
| Mac pairs but **no input** on gamepad-tester / Xbox Cloud | 1) On Mac: forget the device  2) On phone: Disconnect then Connect  3) Re-pair as **Pixel Gamepad** (not old "Xbox Controller")  4) Hard-refresh gamepad-tester |
| Mac shows phone as audio/headset | Unpair both sides; phone must advertise HID gamepad SDP before pairing |
| Still nothing after re-pair | `adb logcat -s HidDeviceManager:I HidDeviceService:V` — look for `report sent` and `onConnectionStateChanged CONNECTED` |
| Mac doesn’t see device | Phone must show **ADVERTISING**; put Mac Bluetooth UI in pairing mode; forget old pairings |
| Gamepad tester sees pad but no input | Ensure status is **CONNECTED**; move sticks — reports only flow while connected |
| Install unauthorized | `adb devices` → accept RSA fingerprint on phone |
| API < 28 | `BluetoothHidDevice` is unavailable |

## License

MIT


## Troubleshooting (macOS)

If Bluetooth shows the phone connected but [gamepad-tester.com](https://gamepad-tester.com) / Xbox Cloud Gaming show no pads:

1. On the phone, status must be **CONNECTED** (not only Advertising).
2. Forget the device on the Mac (**System Settings → Bluetooth → ℹ → Forget**), force-stop the app, reopen, tap **Connect**, then re-pair as **Pixel Gamepad**.
3. HID report descriptor changes only take effect after a full re-pair (SDP is cached).
4. Confirm reports on the Mac: a HID monitor should see 10-byte reports (`01 …`) at ~125 Hz while the phone is connected.
5. Wireless ADB: on the phone enable **Wireless debugging → Pair device with pairing code**, then on the Mac:

```bash
adb pair <phone-ip>:<pair-port>
adb connect <phone-ip>:<connect-port>
```
