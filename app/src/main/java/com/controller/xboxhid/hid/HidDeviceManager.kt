package com.controller.xboxhid.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps [BluetoothHidDevice] so the phone advertises as a gamepad and can push
 * input reports to the paired host (MacBook, etc.).
 */
class HidDeviceManager(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStateChanged(state: ConnectionState)
        fun onError(message: String)
    }

    enum class ConnectionState {
        IDLE,
        REGISTERING,
        ADVERTISING,
        CONNECTED,
        ERROR,
    }

    companion object {
        private const val TAG = "HidDeviceManager"
        private const val APP_NAME = "Xbox Controller"
        private const val APP_DESCRIPTION = "Bluetooth Xbox-style HID Gamepad"
        private const val APP_PROVIDER = "controller"
        private val SUBCLASS: Byte = HidDescriptor.SUBCLASS_GAMEPAD
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val executor = Executors.newSingleThreadExecutor()
    private val hidDevice = AtomicReference<BluetoothHidDevice?>(null)
    private val hostDevice = AtomicReference<BluetoothDevice?>(null)
    private val registered = AtomicBoolean(false)
    private val reportBuffer = ByteArray(HidDescriptor.REPORT_SIZE)
    private val stateLock = Any()
    @Volatile private var connectionState = ConnectionState.IDLE

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            hidDevice.set(hid)
            registerApp(hid)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice.set(null)
            hostDevice.set(null)
            registered.set(false)
            setState(ConnectionState.IDLE)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered plugged=$pluggedDevice")
            this@HidDeviceManager.registered.set(registered)
            if (registered) {
                setState(ConnectionState.ADVERTISING)
                // Auto-connect to already-bonded hosts when possible
                tryConnectBonded()
            } else {
                setState(ConnectionState.IDLE)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(TAG, "onConnectionStateChanged device=$device state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hostDevice.set(device)
                    setState(ConnectionState.CONNECTED)
                    // Send neutral report so host sees a live gamepad immediately
                    sendReport(InputReport.packNeutral())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (hostDevice.get() == device) {
                        hostDevice.set(null)
                    }
                    if (registered.get()) {
                        setState(ConnectionState.ADVERTISING)
                    } else {
                        setState(ConnectionState.IDLE)
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id == HidDescriptor.REPORT_ID) {
                hidDevice.get()?.replyReport(device, type, id, InputReport.packNeutral())
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // Host output reports (rumble etc.) — ignore for now
            Log.d(TAG, "onSetReport type=$type id=$id len=${data?.size}")
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            Log.d(TAG, "onInterruptData id=$reportId len=${data?.size}")
        }
    }

    fun currentState(): ConnectionState = connectionState

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun start() {
        val bt = adapter
        if (bt == null) {
            setState(ConnectionState.ERROR)
            listener.onError("No Bluetooth adapter")
            return
        }
        if (!bt.isEnabled) {
            setState(ConnectionState.ERROR)
            listener.onError("Bluetooth is disabled")
            return
        }
        if (hidDevice.get() != null && registered.get()) {
            setState(ConnectionState.ADVERTISING)
            tryConnectBonded()
            return
        }
        setState(ConnectionState.REGISTERING)
        val ok = bt.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            setState(ConnectionState.ERROR)
            listener.onError("Failed to get HID_DEVICE profile proxy")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val hid = hidDevice.get()
        val host = hostDevice.get()
        try {
            if (hid != null && host != null) {
                hid.disconnect(host)
            }
            if (hid != null && registered.get()) {
                hid.unregisterApp()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop error", e)
        }
        hostDevice.set(null)
        registered.set(false)
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        hidDevice.set(null)
        setState(ConnectionState.IDLE)
    }

    fun release() {
        stop()
        executor.shutdownNow()
    }

    /** Thread-safe: pack and send latest controller state. */
    @SuppressLint("MissingPermission")
    fun sendState(state: ControllerState): Boolean {
        val packed = InputReport.pack(state, reportBuffer)
        return sendReport(packed)
    }

    @SuppressLint("MissingPermission")
    private fun sendReport(data: ByteArray): Boolean {
        val hid = hidDevice.get() ?: return false
        val host = hostDevice.get() ?: return false
        return try {
            hid.sendReport(host, HidDescriptor.REPORT_ID.toInt() and 0xFF, data)
        } catch (e: Exception) {
            Log.w(TAG, "sendReport failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp(hid: BluetoothHidDevice) {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            APP_NAME,
            APP_DESCRIPTION,
            APP_PROVIDER,
            SUBCLASS,
            HidDescriptor.DESCRIPTOR
        )
        // Best-effort QoS for gamepad latency
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val ok = try {
            hid.registerApp(sdp, null, inQos, executor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "registerApp failed", e)
            listener.onError("registerApp failed: ${e.message}")
            false
        }
        if (!ok) {
            setState(ConnectionState.ERROR)
            listener.onError("HID registerApp returned false — another HID app may be active")
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectBonded() {
        val hid = hidDevice.get() ?: return
        val bt = adapter ?: return
        try {
            val bonded = bt.bondedDevices ?: emptySet()
            for (device in bonded) {
                // Prefer connecting; host can also initiate from Bluetooth settings
                Log.i(TAG, "Attempting connect to bonded ${device.name} ${device.address}")
                hid.connect(device)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT for bonded scan", e)
        } catch (e: Exception) {
            Log.w(TAG, "tryConnectBonded", e)
        }
    }

    private fun setState(state: ConnectionState) {
        synchronized(stateLock) {
            if (connectionState == state) return
            connectionState = state
        }
        listener.onStateChanged(state)
    }

    fun hostName(): String? {
        val d = hostDevice.get() ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // name access needs permission; already requested by app
            }
            @SuppressLint("MissingPermission")
            val n = d.name
            n ?: d.address
        } catch (_: SecurityException) {
            d.address
        }
    }
}
