package com.controller.xboxhid.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputReportTest {

    @Test
    fun pack_neutral_is_centered_and_empty() {
        val r = InputReport.packNeutral()
        assertEquals(HidDescriptor.REPORT_SIZE, r.size)

        // buttons zero
        assertEquals(0.toByte(), r[0])
        assertEquals(0.toByte(), r[1])
        // hat released = 8
        assertEquals(8.toByte(), r[2])
        // sticks centered at 128
        assertEquals(128.toByte(), r[3])
        assertEquals(128.toByte(), r[4])
        assertEquals(128.toByte(), r[5])
        assertEquals(128.toByte(), r[6])
        // triggers zero
        assertEquals(0.toByte(), r[7])
        assertEquals(0.toByte(), r[8])
    }

    @Test
    fun stickToAxis_extremes() {
        assertEquals(0, InputReport.stickToAxis(-1f))
        assertEquals(InputReport.AXIS_CENTER, InputReport.stickToAxis(0f))
        assertEquals(InputReport.AXIS_MAX, InputReport.stickToAxis(1f))
    }

    @Test
    fun stickToAxis_invertY() {
        val up = InputReport.stickToAxis(-1f, invertY = true)
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
    fun buttonsMask_face_and_shoulders() {
        val s = ControllerState(
            buttonA = true,
            buttonB = true,
            buttonX = true,
            buttonY = true,
            bumperL = true,
            bumperR = true,
            leftTrigger = 1f,
            rightTrigger = 1f,
            back = true,
            start = true,
            stickL = true,
            stickR = true,
            guide = true,
            share = true,
        )
        val m = InputReport.buttonsMask(s)
        assertEquals(
            InputReport.BTN_A or InputReport.BTN_B or InputReport.BTN_X or InputReport.BTN_Y or
                InputReport.BTN_L1 or InputReport.BTN_R1 or InputReport.BTN_L2 or InputReport.BTN_R2 or
                InputReport.BTN_BACK or InputReport.BTN_START or InputReport.BTN_L3 or
                InputReport.BTN_R3 or InputReport.BTN_GUIDE or InputReport.BTN_SHARE,
            m
        )
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

        assertEquals(InputReport.AXIS_MAX, r[3].toInt() and 0xFF) // LX
        assertEquals(InputReport.AXIS_MAX, r[4].toInt() and 0xFF) // LY inverted up
        assertEquals(0, r[5].toInt() and 0xFF) // RX
        assertEquals(0, r[6].toInt() and 0xFF) // RY inverted down
        assertEquals(255, r[7].toInt() and 0xFF)
        assertEquals(127, r[8].toInt() and 0xFF)
        assertTrue((r[0].toInt() and InputReport.BTN_A) != 0)
        assertTrue((r[0].toInt() and InputReport.BTN_R1) != 0)
        assertTrue((r[0].toInt() and InputReport.BTN_L2) != 0) // LT digital
        assertEquals(0, r[2].toInt() and 0x0F) // hat north
    }

    @Test
    fun descriptor_is_well_formed() {
        val d = HidDescriptor.DESCRIPTOR
        assertTrue(d.size > 20)
        assertEquals(0x05.toByte(), d[0])
        assertEquals(0x01.toByte(), d[1])
        assertEquals(0x09.toByte(), d[2])
        assertEquals(0x05.toByte(), d[3])
        assertEquals(0xA1.toByte(), d[4])
        assertEquals(0xC0.toByte(), d.last())
        assertTrue(
            d.toList().windowed(2).any {
                it[0] == 0x85.toByte() && it[1] == HidDescriptor.REPORT_ID
            }
        )
    }

    @Test
    fun subclass_is_gamepad() {
        // AOSP BluetoothHidDevice.SUBCLASS2_GAMEPAD == 2
        assertEquals(0x02.toByte(), HidDescriptor.SUBCLASS_GAMEPAD)
    }
}
