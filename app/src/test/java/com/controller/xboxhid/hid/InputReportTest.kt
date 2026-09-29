package com.controller.xboxhid.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputReportTest {

    @Test
    fun pack_neutral_is_centered_and_empty() {
        val r = InputReport.packNeutral()
        assertEquals(HidDescriptor.REPORT_SIZE, r.size)

        assertEquals(0.toByte(), r[0])
        assertEquals(0.toByte(), r[1])
        assertEquals(8.toByte(), r[2]) // hat released
        assertEquals(128.toByte(), r[3])
        assertEquals(128.toByte(), r[4])
        assertEquals(128.toByte(), r[5])
        assertEquals(128.toByte(), r[6])
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
        // Optional flip helper still works when explicitly requested
        val up = InputReport.stickToAxis(-1f, invertY = true)
        val down = InputReport.stickToAxis(1f, invertY = true)
        assertEquals(InputReport.AXIS_MAX, up)
        assertEquals(0, down)
    }

    @Test
    fun pack_stick_up_is_hid_zero() {
        // UI screen-up is y=-1; must pack as 0 so Chrome axis reads -1 (look/move up)
        val up = InputReport.pack(ControllerState(leftY = -1f, rightY = -1f))
        assertEquals(0, up[4].toInt() and 0xFF) // LY
        assertEquals(0, up[6].toInt() and 0xFF) // RY
        val down = InputReport.pack(ControllerState(leftY = 1f, rightY = 1f))
        assertEquals(InputReport.AXIS_MAX, down[4].toInt() and 0xFF)
        assertEquals(InputReport.AXIS_MAX, down[6].toInt() and 0xFF)
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
    fun buttonsMask_face_shoulders_and_dpad_buttons() {
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
            dpad = InputReport.DPAD_UP or InputReport.DPAD_LEFT,
        )
        val m = InputReport.buttonsMask(s)
        assertEquals(
            InputReport.BTN_A or InputReport.BTN_B or InputReport.BTN_X or InputReport.BTN_Y or
                InputReport.BTN_L1 or InputReport.BTN_R1 or InputReport.BTN_L2 or InputReport.BTN_R2 or
                InputReport.BTN_BACK or InputReport.BTN_START or InputReport.BTN_L3 or
                InputReport.BTN_R3 or InputReport.BTN_DPAD_UP or InputReport.BTN_DPAD_LEFT,
            m
        )
    }

    @Test
    fun dpad_dual_reported_as_hat_and_buttons() {
        val s = ControllerState(dpad = InputReport.DPAD_RIGHT)
        val r = InputReport.pack(s)
        // hat east = 2
        assertEquals(2, r[2].toInt() and 0x0F)
        // button 16 (bit 15) = D-pad right → high button byte bit 7
        assertTrue(((r[1].toInt() and 0xFF) and (InputReport.BTN_DPAD_RIGHT ushr 8)) != 0)
        // only right bit among dpad buttons
        val high = r[1].toInt() and 0xFF
        assertEquals(InputReport.BTN_DPAD_RIGHT ushr 8, high and 0xF0)
    }

    @Test
    fun pack_full_deflection_and_buttons() {
        val s = ControllerState(
            leftX = 1f,
            leftY = -1f, // UI up → HID 0
            rightX = -1f,
            rightY = 1f, // UI down → HID 255
            leftTrigger = 1f,
            rightTrigger = 0.5f,
            buttonA = true,
            bumperR = true,
            dpad = InputReport.DPAD_UP,
        )
        val r = InputReport.pack(s)

        assertEquals(InputReport.AXIS_MAX, r[3].toInt() and 0xFF) // LX right
        assertEquals(0, r[4].toInt() and 0xFF) // LY up
        assertEquals(0, r[5].toInt() and 0xFF) // RX left
        assertEquals(InputReport.AXIS_MAX, r[6].toInt() and 0xFF) // RY down
        assertEquals(255, r[7].toInt() and 0xFF)
        assertEquals(127, r[8].toInt() and 0xFF)
        assertTrue((r[0].toInt() and InputReport.BTN_A) != 0)
        assertTrue((r[0].toInt() and InputReport.BTN_R1) != 0)
        assertTrue((r[0].toInt() and InputReport.BTN_L2) != 0)
        assertEquals(0, r[2].toInt() and 0x0F) // hat north
        // D-pad up also as button bit 12
        assertTrue((r[1].toInt() and (InputReport.BTN_DPAD_UP ushr 8)) != 0)
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
    fun descriptor_axes_map_to_standard_gamepad_indices() {
        // Chrome sorts Generic Desktop axes by usage id:
        // X Y Z Rx Ry Rz → axes[0..5] = LSX LSY RSX RSY LT RT
        val d = HidDescriptor.DESCRIPTOR
        val usages = mutableListOf<Int>()
        var i = 0
        while (i < d.size - 1) {
            // Look for USAGE (0x09) under Generic Desktop for axis range 0x30..0x35
            if (d[i] == 0x09.toByte()) {
                val u = d[i + 1].toInt() and 0xFF
                if (u in 0x30..0x35) usages.add(u)
            }
            i++
        }
        assertEquals(
            listOf(0x30, 0x31, 0x32, 0x33, 0x34, 0x35), // X Y Z Rx Ry Rz
            usages
        )
    }

    @Test
    fun subclass_is_gamepad() {
        assertEquals(0x02.toByte(), HidDescriptor.SUBCLASS_GAMEPAD)
    }
}
