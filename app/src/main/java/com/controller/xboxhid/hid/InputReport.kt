package com.controller.xboxhid.hid

/**
 * Mutable controller state → packed HID input report.
 *
 * Stick values are normalized floats in [-1, 1].
 * Triggers are floats in [0, 1].
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
    const val AXIS_CENTER = 32768
    const val AXIS_MAX = 65535

    // Button bit positions matching descriptor order (Button 1..12)
    const val BTN_A = 1 shl 0
    const val BTN_B = 1 shl 1
    const val BTN_X = 1 shl 2
    const val BTN_Y = 1 shl 3
    const val BTN_L1 = 1 shl 4
    const val BTN_R1 = 1 shl 5
    const val BTN_BACK = 1 shl 6
    const val BTN_START = 1 shl 7
    const val BTN_L3 = 1 shl 8
    const val BTN_R3 = 1 shl 9
    const val BTN_GUIDE = 1 shl 10
    const val BTN_SHARE = 1 shl 11

    const val DPAD_UP = 1
    const val DPAD_DOWN = 2
    const val DPAD_LEFT = 4
    const val DPAD_RIGHT = 8

    /** Map normalized stick [-1,1] → uint16 LE center-biased. Y inverted for screen coords. */
    fun stickToAxis(value: Float, invertY: Boolean = false): Int {
        val v = (if (invertY) -value else value).coerceIn(-1f, 1f)
        return when {
            v <= -1f -> 0
            v >= 1f -> AXIS_MAX
            v == 0f -> AXIS_CENTER
            // Symmetric around 32768 for the open interval
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
        if (state.back) m = m or BTN_BACK
        if (state.start) m = m or BTN_START
        if (state.stickL) m = m or BTN_L3
        if (state.stickR) m = m or BTN_R3
        if (state.guide) m = m or BTN_GUIDE
        if (state.share) m = m or BTN_SHARE
        return m
    }

    /** Pack [ControllerState] into a [HidDescriptor.REPORT_SIZE]-byte report body. */
    fun pack(state: ControllerState, out: ByteArray = ByteArray(HidDescriptor.REPORT_SIZE)): ByteArray {
        require(out.size >= HidDescriptor.REPORT_SIZE) { "report buffer too small" }

        val lx = stickToAxis(state.leftX)
        val ly = stickToAxis(state.leftY, invertY = true)
        val rx = stickToAxis(state.rightX)
        val ry = stickToAxis(state.rightY, invertY = true)
        val lt = triggerToByte(state.leftTrigger)
        val rt = triggerToByte(state.rightTrigger)
        val buttons = buttonsMask(state)
        val hat = dpadToHat(state.dpad)

        out[0] = (lx and 0xFF).toByte()
        out[1] = ((lx ushr 8) and 0xFF).toByte()
        out[2] = (ly and 0xFF).toByte()
        out[3] = ((ly ushr 8) and 0xFF).toByte()
        out[4] = (rx and 0xFF).toByte()
        out[5] = ((rx ushr 8) and 0xFF).toByte()
        out[6] = (ry and 0xFF).toByte()
        out[7] = ((ry ushr 8) and 0xFF).toByte()
        out[8] = lt.toByte()
        out[9] = rt.toByte()
        out[10] = (buttons and 0xFF).toByte()
        out[11] = ((buttons ushr 8) and 0xFF).toByte()
        out[12] = (hat and 0x0F).toByte()
        out[13] = 0
        out[14] = 0
        return out
    }

    fun packNeutral(): ByteArray = pack(ControllerState())
}
