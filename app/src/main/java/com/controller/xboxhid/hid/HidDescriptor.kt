package com.controller.xboxhid.hid

/**
 * Standard Bluetooth HID Game Pad descriptor that macOS + Chrome Gamepad API accept.
 *
 * Important design choices for macOS:
 * - Generic name (not "Xbox Controller") so IOHID does not expect Microsoft's XInput HID
 * - 8-bit axes (0..255, center 128) — widely recognized
 * - Buttons first, then hat, then sticks, then triggers
 * - Report ID 1
 *
 * Report body (9 bytes) after report id is handled by the stack:
 *
 *  Byte 0-1 : Buttons bitfield (16 buttons)
 *             bit0=A 1=B 2=X 3=Y 4=L1 5=R1 6=L2 7=R2
 *             bit8=Back 9=Start 10=L3 11=R3 12=Guide 13=Share
 *  Byte 2   : Hat / D-Pad (low nibble: 0=N..7=NW, 8=release; high nibble pad)
 *  Byte 3   : Left stick X  (0..255, center 128)
 *  Byte 4   : Left stick Y
 *  Byte 5   : Right stick X
 *  Byte 6   : Right stick Y
 *  Byte 7   : LT (0..255)
 *  Byte 8   : RT (0..255)
 */
object HidDescriptor {

    const val REPORT_ID: Byte = 0x01
    const val REPORT_SIZE = 9

    /** Bluetooth HID SDP subclass: Gamepad (AOSP BluetoothHidDevice.SUBCLASS2_GAMEPAD). */
    const val SUBCLASS_GAMEPAD: Byte = 0x02

    /**
     * Advertised local name. Avoid "Xbox Controller" — macOS may expect proprietary
     * Microsoft reports and ignore a generic HID gamepad with that name.
     */
    const val APP_NAME = "Pixel Gamepad"
    const val APP_DESCRIPTION = "Bluetooth HID Gamepad"
    const val APP_PROVIDER = "controller"

    private fun b(vararg values: Int): ByteArray =
        ByteArray(values.size) { i -> (values[i] and 0xFF).toByte() }

    val DESCRIPTOR: ByteArray = b(
        // USAGE_PAGE (Generic Desktop)
        0x05, 0x01,
        // USAGE (Game Pad)
        0x09, 0x05,
        // COLLECTION (Application)
        0xA1, 0x01,
        // REPORT_ID (1)
        0x85, 0x01,

        // ----- 16 buttons -----
        // USAGE_PAGE (Button)
        0x05, 0x09,
        // USAGE_MINIMUM (1)
        0x19, 0x01,
        // USAGE_MAXIMUM (16)
        0x29, 0x10,
        // LOGICAL_MINIMUM (0)
        0x15, 0x00,
        // LOGICAL_MAXIMUM (1)
        0x25, 0x01,
        // REPORT_SIZE (1)
        0x75, 0x01,
        // REPORT_COUNT (16)
        0x95, 0x10,
        // INPUT (Data,Var,Abs)
        0x81, 0x02,

        // ----- Hat switch (D-Pad) -----
        // Logical max 8 so released (8) is in-range for macOS/Chrome parsers.
        // USAGE_PAGE (Generic Desktop)
        0x05, 0x01,
        // USAGE (Hat switch)
        0x09, 0x39,
        // LOGICAL_MINIMUM (0)
        0x15, 0x00,
        // LOGICAL_MAXIMUM (8)  — 0..7 directions, 8 = neutral/released
        0x25, 0x08,
        // PHYSICAL_MINIMUM (0)
        0x35, 0x00,
        // PHYSICAL_MAXIMUM (315)
        0x46, 0x3B, 0x01,
        // UNIT (Eng Rot:Angular Pos)
        0x65, 0x14,
        // REPORT_SIZE (4)
        0x75, 0x04,
        // REPORT_COUNT (1)
        0x95, 0x01,
        // INPUT (Data,Var,Abs,Null State)
        0x81, 0x42,
        // Pad 4 bits
        0x65, 0x00, // UNIT (None)
        0x75, 0x04,
        0x95, 0x01,
        0x81, 0x01, // INPUT (Const)

        // ----- Sticks: X, Y, Z, Rz as 8-bit absolute (0..255) -----
        0x09, 0x30, // X  (left X)
        0x09, 0x31, // Y  (left Y)
        0x09, 0x32, // Z  (right X)
        0x09, 0x35, // Rz (right Y)
        0x15, 0x00,
        0x26, 0xFF, 0x00,
        0x75, 0x08,
        0x95, 0x04,
        0x81, 0x02,

        // ----- Triggers: Rx, Ry as 8-bit (0..255) -----
        0x09, 0x33, // Rx (LT)
        0x09, 0x34, // Ry (RT)
        0x15, 0x00,
        0x26, 0xFF, 0x00,
        0x75, 0x08,
        0x95, 0x02,
        0x81, 0x02,

        // END_COLLECTION
        0xC0
    )
}
