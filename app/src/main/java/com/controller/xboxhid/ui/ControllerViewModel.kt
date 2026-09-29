package com.controller.xboxhid.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.controller.xboxhid.hid.ControllerState
import com.controller.xboxhid.hid.HidControllerService
import com.controller.xboxhid.hid.HidDeviceManager
import com.controller.xboxhid.hid.InputReport

class ControllerViewModel(app: Application) : AndroidViewModel(app),
    HidControllerService.StatusListener {

    var connectionState by mutableStateOf(HidDeviceManager.ConnectionState.IDLE)
        private set
    var statusText by mutableStateOf("Tap Connect to advertise as Xbox Controller")
        private set
    var bound by mutableStateOf(false)
        private set

    // Live UI mirror of pressed controls
    var uiState by mutableStateOf(ControllerState())
        private set

    private var service: HidControllerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as HidControllerService.LocalBinder).getService()
            service = s
            bound = true
            s.addStatusListener(this@ControllerViewModel)
            connectionState = s.connectionState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.removeStatusListener(this@ControllerViewModel)
            service = null
            bound = false
        }
    }

    init {
        val ctx = getApplication<Application>()
        HidControllerService.start(ctx)
        ctx.bindService(
            Intent(ctx, HidControllerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        val ctx = getApplication<Application>()
        service?.removeStatusListener(this)
        if (bound) {
            runCatching { ctx.unbindService(connection) }
        }
        super.onCleared()
    }

    override fun onStatus(state: HidDeviceManager.ConnectionState, detail: String?) {
        connectionState = state
        statusText = detail ?: state.name
    }

    fun connect() {
        HidControllerService.start(getApplication())
        service?.startAdvertising()
    }

    fun disconnect() {
        service?.stopAdvertising()
    }

    private fun push(mutator: ControllerState.() -> Unit) {
        val next = uiState.copy().apply(mutator)
        uiState = next
        service?.setFullState(next)
    }

    fun setLeftStick(x: Float, y: Float) = push {
        leftX = x.coerceIn(-1f, 1f)
        leftY = y.coerceIn(-1f, 1f)
    }

    fun setRightStick(x: Float, y: Float) = push {
        rightX = x.coerceIn(-1f, 1f)
        rightY = y.coerceIn(-1f, 1f)
    }

    fun setLeftTrigger(v: Float) = push { leftTrigger = v.coerceIn(0f, 1f) }
    fun setRightTrigger(v: Float) = push { rightTrigger = v.coerceIn(0f, 1f) }

    fun setButtonA(v: Boolean) = push { buttonA = v }
    fun setButtonB(v: Boolean) = push { buttonB = v }
    fun setButtonX(v: Boolean) = push { buttonX = v }
    fun setButtonY(v: Boolean) = push { buttonY = v }
    fun setBumperL(v: Boolean) = push { bumperL = v }
    fun setBumperR(v: Boolean) = push { bumperR = v }
    fun setBack(v: Boolean) = push { back = v }
    fun setStart(v: Boolean) = push { start = v }
    fun setStickL(v: Boolean) = push { stickL = v }
    fun setStickR(v: Boolean) = push { stickR = v }
    fun setGuide(v: Boolean) = push { guide = v }
    fun setShare(v: Boolean) = push { share = v }

    fun setDpadBit(bit: Int, pressed: Boolean) = push {
        dpad = if (pressed) dpad or bit else dpad and bit.inv()
    }

    fun pressDpadUp(v: Boolean) = setDpadBit(InputReport.DPAD_UP, v)
    fun pressDpadDown(v: Boolean) = setDpadBit(InputReport.DPAD_DOWN, v)
    fun pressDpadLeft(v: Boolean) = setDpadBit(InputReport.DPAD_LEFT, v)
    fun pressDpadRight(v: Boolean) = setDpadBit(InputReport.DPAD_RIGHT, v)
}
