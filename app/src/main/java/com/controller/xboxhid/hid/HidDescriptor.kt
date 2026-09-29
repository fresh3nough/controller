package com.controller.xboxhid.hid

/**
 * Bluetooth HID report descriptor for a generic Gamepad that hosts (macOS, Chrome
 * Gamepad API, Xbox Cloud Gaming) accept as a standard controller.
 *
 * Report layout (report id 1), 15-byte body:
 *
 *  Byte  0-1 : Left stick X   (uint16 LE, 0..65535, center 32768)
 *  Byte  2-3 : Left stick Y
 *  Byte  4-5 : Right stick X
 *  Byte  6-7 : Right stick Y
 *  Byte  8   : LT             (uint8, 0..255)
 *  Byte  9   : RT             (uint8, 0..255)
 *  Byte 10   : Buttons low    (bit0=A,1=B,2=X,3=Y,4=L1,5=R1,6=Back,7=Start)
 *  Byte 11   : Buttons high   (bit0=L3,1=R3,2=Guide,3=Share)
 *  Byte 12   : Hat / D-Pad    (0=N … 7=NW, 8=released)
 *  Byte 13-14: reserved
 */
object HidDescriptor {

    const val REPORT_ID: Byte = 0x01
    const val REPORT_SIZE = 15

    const val VENDOR_ID = 0x045E   // Microsoft
    const val PRODUCT_ID = 0x028E  // Xbox 360-style (widely recognized)
    const val VERSION = 0x0110

    // BluetoothHidDevice subclass for gamepad (value from API; constant name varies)
    const val SUBCLASS_GAMEPAD: Byte = 0x08

    private fun b(vararg values: Int): ByteArray =
        ByteArray(values.size) { i -> values[i].toByte() }

    val DESCRIPTOR: ByteArray = b(
        // USAGE_PAGE (Generic Desktop)
        0x05, 0x01,
        // USAGE (Game Pad)
        0x09, 0x05,
        // COLLECTION (Application)
        0xA1, 0x01,
        // REPORT_ID (1)
        0x85, 0x01,

        // Left stick X,Y (16-bit absolute)
        0x09, 0x30, // X
        0x09, 0x31, // Y
        0x15, 0x00,
        0x26, 0xFF, 0xFF,
        0x75, 0x10,
        0x95, 0x02,
        0x81, 0x02,

        // Right stick as Z, Rz (16-bit)
        0x09, 0x32, // Z
        0x09, 0x35, // Rz
        0x15, 0x00,
        0x26, 0xFF, 0xFF,
        0x75, 0x10,
        0x95, 0x02,
        0x81, 0x02,

        // Triggers as Rx, Ry (8-bit)
        0x05, 0x01,
        0x09, 0x33, // Rx
        0x09, 0x34, // Ry
        0x15, 0x00,
        0x26, 0xFF, 0x00,
        0x75, 0x08,
        0x95, 0x02,
        0x81, 0x02,

        // 12 buttons
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x0C,
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95, 0x0C,
        0x81, 0x02,
        // pad 4 bits
        0x75, 0x01,
        0x95, 0x04,
        0x81, 0x03,

        // Hat switch (D-Pad)
        0x05, 0x01,
        0x09, 0x39,
        0x15, 0x00,
        0x25, 0x07,
        0x35, 0x00,
        0x46, 0x3B, 0x01,
        0x65, 0x14,
        0x75, 0x04,
        0x95, 0x01,
        0x81, 0x42,
        // pad 4 bits + 2 reserved bytes
        0x75, 0x04,
        0x95, 0x01,
        0x81, 0x01,
        0x75, 0x08,
        0x95, 0x02,
        0x81, 0x01,

        // END_COLLECTION
        0xC0
    )
}
