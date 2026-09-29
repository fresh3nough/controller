package com.controller.xboxhid.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputReportTest {

    @Test
    fun pack_neutral_is_centered_and_empty() {
        val r = InputReport.packNeutral()
        assertEquals(HidDescriptor.REPORT_SIZE, r.size)

        // sticks centered at 32768 = 0x8000 LE
        assertEquals(0x00.toByte(), r[0])
        assertEquals(0x80.toByte(), r[1])
        assertEquals(0x00.toByte(), r[2])
        assertEquals(0x80.toByte(), r[3])
        assertEquals(0x00.toByte(), r[4])
        assertEquals(0x80.toByte(), r[5])
        assertEquals(0x00.toByte(), r[6])
        assertEquals(0x80.toByte(), r[7])

        // triggers zero
        assertEquals(0.toByte(), r[8])
        assertEquals(0.toByte(), r[9])

        // buttons zero
        assertEquals(0.toByte(), r[10])
        assertEquals(0.toByte(), r[11])

        // hat released = 8
        assertEquals(8.toByte(), r[12])
    }

    @Test
    fun stickToAxis_extremes() {
        assertEquals(0, InputReport.stickToAxis(-1f))
        assertEquals(InputReport.AXIS_CENTER, InputReport.stickToAxis(0f))
        assertEquals(InputReport.AXIS_MAX, InputReport.stickToAxis(1f))
    }

    @Test
    fun stickToAxis_invertY() {
        // positive screen-down should become low axis when inverted
        val up = InputReport.stickToAxis(-1f, invertY = true) // stick forward
        val down = InputReport.stickToAxis(1f, invertY = true)
        assertEquals(InputReport.AXIS_MAX, up)
        assertEquals(0, down)
    }

    @Test
    fun triggerToByte_range() {
        assertEquals(0, InputReport.triggerToByte(0f))
        assertEquals(255, InputReport.triggerToByte(1f))
        assertEquals(127, InputReport.triggerToByte(0.5f))
        assertEquals(0, InputReport.triggerToByte(-5f))
        assertEquals(255, InputReport.triggerToByte(5f))
    }

    @Test
    fun dpadToHat_cardinals_and_diagonals() {
        assertEquals(8, InputReport.dpadToHat(0))
        assertEquals(0, InputReport.dpadToHat(InputReport.DPAD_UP))
        assertEquals(2, InputReport.dpadToHat(InputReport.DPAD_RIGHT))
        assertEquals(4, InputReport.dpadToHat(InputReport.DPAD_DOWN))
        assertEquals(6, InputReport.dpadToHat(InputReport.DPAD_LEFT))
        assertEquals(1, InputReport.dpadToHat(InputReport.DPAD_UP or InputReport.DPAD_RIGHT))
        assertEquals(5, InputReport.dpadToHat(InputReport.DPAD_DOWN or InputReport.DPAD_LEFT))
    }

    @Test
    fun buttonsMask_all_face_and_shoulders() {
        val s = ControllerState(
            buttonA = true,
            buttonB = true,
            buttonX = true,
            buttonY = true,
            bumperL = true,
            bumperR = true,
            back = true,
            start = true,
            stickL = true,
            stickR = true,
            guide = true,
            share = true,
        )
        val m = InputReport.buttonsMask(s)
        assertEquals(0x0FFF, m and 0x0FFF)
    }

    @Test
    fun pack_full_deflection_and_buttons() {
        val s = ControllerState(
            leftX = 1f,
            leftY = -1f, // up on screen → after invert = forward = max
            rightX = -1f,
            rightY = 1f,
            leftTrigger = 1f,
            rightTrigger = 0.5f,
            buttonA = true,
            bumperR = true,
            dpad = InputReport.DPAD_UP,
        )
        val r = InputReport.pack(s)

        fun u16(lo: Byte, hi: Byte) = (lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)

        assertEquals(InputReport.AXIS_MAX, u16(r[0], r[1]))
        assertEquals(InputReport.AXIS_MAX, u16(r[2], r[3])) // inverted Y up
        assertEquals(0, u16(r[4], r[5]))
        assertEquals(0, u16(r[6], r[7])) // inverted Y down
        assertEquals(255, r[8].toInt() and 0xFF)
        assertEquals(127, r[9].toInt() and 0xFF)
        assertEquals(InputReport.BTN_A, r[10].toInt() and 0xFF and InputReport.BTN_A)
        assertTrue((r[10].toInt() and InputReport.BTN_R1) != 0)
        assertEquals(0, r[12].toInt() and 0x0F) // hat north
    }

    @Test
    fun descriptor_is_well_formed() {
        val d = HidDescriptor.DESCRIPTOR
        assertTrue(d.size > 20)
        // starts with USAGE_PAGE Generic Desktop + USAGE Game Pad + COLLECTION
        assertEquals(0x05.toByte(), d[0])
        assertEquals(0x01.toByte(), d[1])
        assertEquals(0x09.toByte(), d[2])
        assertEquals(0x05.toByte(), d[3])
        assertEquals(0xA1.toByte(), d[4])
        // ends with END_COLLECTION
        assertEquals(0xC0.toByte(), d.last())
        // contains report id
        assertTrue(d.toList().windowed(2).any { it[0] == 0x85.toByte() && it[1] == HidDescriptor.REPORT_ID })
    }
}
