package com.controller.xboxhid.hid

/**
 * Mutable controller state → packed HID input report (9 bytes).
 *
 * Stick values are normalized floats in [-1, 1].
 * Triggers are floats in [0, 1].
 *
 * D-pad is dual-reported:
 *  1) Hat switch nibble (byte 2) — classic HID
 *  2) Buttons 13–16 (bits 12–15) — Chrome / standard gamepad mapping
 *     (buttons[12]=Up, [13]=Down, [14]=Left, [15]=Right)
 */
data class ControllerState(
    var leftX: Float = 0f,
    var leftY: Float = 0f,
    var rightX: Float = 0f,
    var rightY: Float = 0f,
    var leftTrigger: Float = 0f,
    var rightTrigger: Float = 0f,
    var buttonA: Boolean = false,
    var buttonB: Boolean = false,
    var buttonX: Boolean = false,
    var buttonY: Boolean = false,
    var bumperL: Boolean = false,
    var bumperR: Boolean = false,
    var back: Boolean = false,
    var start: Boolean = false,
    var stickL: Boolean = false,
    var stickR: Boolean = false,
    var guide: Boolean = false,
    var share: Boolean = false,
    /** D-pad: bit flags UP=1 DOWN=2 LEFT=4 RIGHT=8 */
    var dpad: Int = 0,
) {
    fun copyFrom(other: ControllerState) {
        leftX = other.leftX
        leftY = other.leftY
        rightX = other.rightX
        rightY = other.rightY
        leftTrigger = other.leftTrigger
        rightTrigger = other.rightTrigger
        buttonA = other.buttonA
        buttonB = other.buttonB
        buttonX = other.buttonX
        buttonY = other.buttonY
        bumperL = other.bumperL
        bumperR = other.bumperR
        back = other.back
        start = other.start
        stickL = other.stickL
        stickR = other.stickR
        guide = other.guide
        share = other.share
        dpad = other.dpad
    }
}

object InputReport {
    const val AXIS_CENTER = 128
    const val AXIS_MAX = 255

    // Button bit positions matching descriptor order (Button 1..16)
    // Chrome standard mapping expects:
    //  0=A 1=B 2=X 3=Y 4=L1 5=R1 6=L2 7=R2 8=Back 9=Start 10=L3 11=R3
    //  12=DpadUp 13=DpadDown 14=DpadLeft 15=DpadRight
    const val BTN_A = 1 shl 0
    const val BTN_B = 1 shl 1
    const val BTN_X = 1 shl 2
    const val BTN_Y = 1 shl 3
    const val BTN_L1 = 1 shl 4
    const val BTN_R1 = 1 shl 5
    const val BTN_L2 = 1 shl 6
    const val BTN_R2 = 1 shl 7
    const val BTN_BACK = 1 shl 8
    const val BTN_START = 1 shl 9
    const val BTN_L3 = 1 shl 10
    const val BTN_R3 = 1 shl 11
    // D-pad as buttons 13-16 (bits 12-15) for Chrome Gamepad API / Xbox Cloud
    const val BTN_DPAD_UP = 1 shl 12
    const val BTN_DPAD_DOWN = 1 shl 13
    const val BTN_DPAD_LEFT = 1 shl 14
    const val BTN_DPAD_RIGHT = 1 shl 15

    // Guide/Share are not in the 16-button standard map; keep as extra if needed
    // via hat-only path. We reclaim bits 12-15 for D-pad buttons.

    const val DPAD_UP = 1
    const val DPAD_DOWN = 2
    const val DPAD_LEFT = 4
    const val DPAD_RIGHT = 8

    /**
     * Map normalized stick [-1,1] → uint8 center-biased (0..255, center 128).
     *
     * Convention matches HID + Chrome Gamepad API:
     *   -1 → 0,  0 → 128,  +1 → 255
     * Joystick UI uses screen coords (y+ = down). Passing y through unchanged means
     * physical stick-up (y=-1) packs as 0 → browser axis -1 ("up"). Do **not** invert
     * here or look cameras pitch into the floor (Dragonwilds / cloud games).
     */
    fun stickToAxis(value: Float, invertY: Boolean = false): Int {
        val v = (if (invertY) -value else value).coerceIn(-1f, 1f)
        return when {
            v <= -1f -> 0
            v >= 1f -> AXIS_MAX
            v == 0f -> AXIS_CENTER
            v < 0f -> (AXIS_CENTER + (v * AXIS_CENTER)).toInt().coerceIn(0, AXIS_MAX)
            else -> (AXIS_CENTER + (v * (AXIS_MAX - AXIS_CENTER))).toInt().coerceIn(0, AXIS_MAX)
        }
    }

    fun triggerToByte(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)

    /**
     * HID hat values: 0=N,1=NE,2=E,3=SE,4=S,5=SW,6=W,7=NW, 8=null/released
     */
    fun dpadToHat(dpad: Int): Int {
        val up = dpad and DPAD_UP != 0
        val down = dpad and DPAD_DOWN != 0
        val left = dpad and DPAD_LEFT != 0
        val right = dpad and DPAD_RIGHT != 0
        return when {
            up && right -> 1
            up && left -> 7
            down && right -> 3
            down && left -> 5
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> 8
        }
    }

    fun buttonsMask(state: ControllerState): Int {
        var m = 0
        if (state.buttonA) m = m or BTN_A
        if (state.buttonB) m = m or BTN_B
        if (state.buttonX) m = m or BTN_X
        if (state.buttonY) m = m or BTN_Y
        if (state.bumperL) m = m or BTN_L1
        if (state.bumperR) m = m or BTN_R1
        if (state.leftTrigger >= 0.12f) m = m or BTN_L2
        if (state.rightTrigger >= 0.12f) m = m or BTN_R2
        if (state.back) m = m or BTN_BACK
        if (state.start) m = m or BTN_START
        if (state.stickL) m = m or BTN_L3
        if (state.stickR) m = m or BTN_R3
        // D-pad as discrete buttons for Chrome standard mapping (indices 12-15)
        if (state.dpad and DPAD_UP != 0) m = m or BTN_DPAD_UP
        if (state.dpad and DPAD_DOWN != 0) m = m or BTN_DPAD_DOWN
        if (state.dpad and DPAD_LEFT != 0) m = m or BTN_DPAD_LEFT
        if (state.dpad and DPAD_RIGHT != 0) m = m or BTN_DPAD_RIGHT
        return m
    }

    /** Pack [ControllerState] into a [HidDescriptor.REPORT_SIZE]-byte report body. */
    fun pack(state: ControllerState, out: ByteArray = ByteArray(HidDescriptor.REPORT_SIZE)): ByteArray {
        require(out.size >= HidDescriptor.REPORT_SIZE) { "report buffer too small" }

        val buttons = buttonsMask(state)
        val hat = dpadToHat(state.dpad)
        val lx = stickToAxis(state.leftX)
        // No Y invert: UI y=-1 (up) → 0, y=+1 (down) → 255 — Chrome/Xbox Cloud standard
        val ly = stickToAxis(state.leftY)
        val rx = stickToAxis(state.rightX)
        val ry = stickToAxis(state.rightY)
        val lt = triggerToByte(state.leftTrigger)
        val rt = triggerToByte(state.rightTrigger)

        out[0] = (buttons and 0xFF).toByte()
        out[1] = ((buttons ushr 8) and 0xFF).toByte()
        out[2] = (hat and 0x0F).toByte()
        out[3] = lx.toByte()
        out[4] = ly.toByte()
        out[5] = rx.toByte()
        out[6] = ry.toByte()
        out[7] = lt.toByte()
        out[8] = rt.toByte()
        return out
    }

    fun packNeutral(): ByteArray = pack(ControllerState())
}
