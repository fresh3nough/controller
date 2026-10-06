package com.controller.xboxhid.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps [BluetoothHidDevice] so the phone advertises as a gamepad and can push
 * input reports to the paired host (Mac, Linux/BlueZ, Windows, etc.).
 *
 * Linux notes:
 * - BlueZ accepts incoming HID only after a BR/EDR bond (ClassicBondedOnly).
 * - The phone must call [BluetoothHidDevice.connect] toward the host; BlueZ's
 *   generic "Connect" often tries HFP first and fails noisily on phones.
 * - We therefore: advertise SDP → ensure discoverable → bond preferred/computer
 *   hosts → repeatedly [connect] while ADVERTISING.
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
        private const val PREFS = "hid_host_prefs"
        private const val KEY_PREFERRED_HOST = "preferred_host_addr"
        private const val CONNECT_RETRY_MS = 4_000L
        private const val DISCOVERY_MS = 12_000L
        private val APP_NAME = HidDescriptor.APP_NAME
        private val APP_DESCRIPTION = HidDescriptor.APP_DESCRIPTION
        private val APP_PROVIDER = HidDescriptor.APP_PROVIDER
        private val SUBCLASS: Byte = HidDescriptor.SUBCLASS_GAMEPAD
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hidDevice = AtomicReference<BluetoothHidDevice?>(null)
    private val hostDevice = AtomicReference<BluetoothDevice?>(null)
    private val registered = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val reportBuffer = ByteArray(HidDescriptor.REPORT_SIZE)
    private val stateLock = Any()
    @Volatile private var lastPacked: ByteArray? = null
    @Volatile private var connectionState = ConnectionState.IDLE
    @Volatile private var sendOk = 0L
    @Volatile private var sendFail = 0L
    @Volatile private var receiversRegistered = false
    private var retryFuture: ScheduledFuture<*>? = null
    private var discoveryStopRunnable: Runnable? = null

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
            stopConnectLoop()
            setState(ConnectionState.IDLE)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered plugged=$pluggedDevice")
            this@HidDeviceManager.registered.set(registered)
            if (registered) {
                setState(ConnectionState.ADVERTISING)
                prepareForHost()
                // Auto-connect to already-bonded hosts when possible
                tryConnectHosts(reason = "app-registered")
                startConnectLoop()
            } else {
                stopConnectLoop()
                setState(ConnectionState.IDLE)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(TAG, "onConnectionStateChanged device=$device state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> {
                    connecting.set(true)
                }
                BluetoothProfile.STATE_CONNECTED -> {
                    connecting.set(false)
                    hostDevice.set(device)
                    device?.address?.let { savePreferredHost(it) }
                    stopDiscovery()
                    setState(ConnectionState.CONNECTED)
                    // Send neutral report so host sees a live gamepad immediately
                    sendReport(InputReport.packNeutral())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connecting.set(false)
                    if (hostDevice.get() == device) {
                        hostDevice.set(null)
                    }
                    if (registered.get()) {
                        setState(ConnectionState.ADVERTISING)
                        // Keep retrying — Linux hosts often need a second connect after bond
                        tryConnectHosts(reason = "disconnected-retry")
                    } else {
                        setState(ConnectionState.IDLE)
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && id == HidDescriptor.REPORT_ID) {
                // Reply with the latest packed state (or neutral) so host polls work.
                val data = synchronized(stateLock) {
                    lastPacked?.copyOf() ?: InputReport.packNeutral()
                }
                hidDevice.get()?.replyReport(device, type, id, data)
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

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.parcelableDevice() ?: return
                    if (isLikelyHost(device)) {
                        Log.i(
                            TAG,
                            "Found host candidate ${safeName(device)} ${device.address} " +
                                "class=${device.bluetoothClass} bond=${device.bondState}"
                        )
                        // Prefer bonding computers discovered while advertising
                        maybeBondAndConnect(device, reason = "discovery")
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.parcelableDevice() ?: return
                    val bond = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val prev = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    Log.i(
                        TAG,
                        "Bond state ${safeName(device)} ${device.address}: $prev -> $bond"
                    )
                    if (bond == BluetoothDevice.BOND_BONDED && registered.get()) {
                        savePreferredHost(device.address)
                        // BlueZ often needs a short settle before HID connect
                        executor.schedule({
                            connectHid(device, reason = "bonded")
                        }, 500, TimeUnit.MILLISECONDS)
                    }
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (registered.get() && connectionState != ConnectionState.CONNECTED) {
                        tryConnectHosts(reason = "acl-connected")
                    }
                }
            }
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
        ensureReceivers()
        if (hidDevice.get() != null && registered.get()) {
            setState(ConnectionState.ADVERTISING)
            prepareForHost()
            tryConnectHosts(reason = "start-already-registered")
            startConnectLoop()
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
        stopConnectLoop()
        stopDiscovery()
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
        connecting.set(false)
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        hidDevice.set(null)
        setState(ConnectionState.IDLE)
    }

    fun release() {
        stop()
        unregisterReceivers()
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
            val ok = hid.sendReport(host, HidDescriptor.REPORT_ID.toInt() and 0xFF, data)
            if (ok) {
                lastPacked = data.copyOf()
                val n = ++sendOk
                if (n == 1L || n % 500L == 0L) {
                    Log.i(TAG, "sendReport ok=$n fail=$sendFail len=${data.size} host=${host.address}")
                }
            } else {
                val n = ++sendFail
                if (n <= 5L || n % 100L == 0L) {
                    Log.w(TAG, "sendReport returned false ok=$sendOk fail=$n")
                }
            }
            ok
        } catch (e: Exception) {
            sendFail++
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
        // Best-effort QoS for gamepad latency (~8 ms interval)
        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val ok = try {
            // outQos null: some stacks (incl. BlueZ path via Android) are happier
            // without insisting on an output QoS contract for a report-only pad.
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

    /** Make the adapter easy for Mac/Linux hosts to find as a gamepad. */
    @SuppressLint("MissingPermission")
    private fun prepareForHost() {
        val bt = adapter ?: return
        runCatching {
            // Helpful when the phone still shows as "Pixel 10…" in host scanners.
            if (bt.name != APP_NAME) {
                val renamed = bt.setName(APP_NAME)
                Log.i(TAG, "setName($APP_NAME) -> $renamed (was ${bt.name})")
            }
        }.onFailure { Log.w(TAG, "setName failed", it) }

        // setScanMode requires BLUETOOTH_PRIVILEGED on modern Android — skip it.
        // Discoverable mode is requested from the Activity via ACTION_REQUEST_DISCOVERABLE.

        // Prefer bonding the remembered host MAC even if it is not in bondedDevices yet.
        tryPreferredHost()
        startDiscovery()
    }

    /**
     * If the user (or install script) saved a host MAC, bond/connect it directly.
     * [BluetoothAdapter.getRemoteDevice] works without prior discovery.
     */
    @SuppressLint("MissingPermission")
    private fun tryPreferredHost() {
        val addr = prefs.getString(KEY_PREFERRED_HOST, null) ?: return
        val bt = adapter ?: return
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
            Log.w(TAG, "Invalid preferred host addr: $addr")
            return
        }
        val device = runCatching { bt.getRemoteDevice(addr) }.getOrNull() ?: return
        Log.i(TAG, "Preferred host $addr bond=${device.bondState}")
        maybeBondAndConnect(device, reason = "preferred-host")
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        val bt = adapter ?: return
        runCatching {
            if (bt.isDiscovering) bt.cancelDiscovery()
            val started = bt.startDiscovery()
            Log.i(TAG, "startDiscovery -> $started")
            discoveryStopRunnable?.let { mainHandler.removeCallbacks(it) }
            val stop = Runnable { stopDiscovery() }
            discoveryStopRunnable = stop
            mainHandler.postDelayed(stop, DISCOVERY_MS)
        }.onFailure { Log.w(TAG, "startDiscovery failed", it) }
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        val bt = adapter ?: return
        discoveryStopRunnable?.let { mainHandler.removeCallbacks(it) }
        discoveryStopRunnable = null
        runCatching {
            if (bt.isDiscovering) {
                bt.cancelDiscovery()
                Log.i(TAG, "cancelDiscovery")
            }
        }
    }

    private fun startConnectLoop() {
        stopConnectLoop()
        retryFuture = executor.scheduleAtFixedRate({
            if (!registered.get()) return@scheduleAtFixedRate
            if (connectionState == ConnectionState.CONNECTED) return@scheduleAtFixedRate
            if (connecting.get()) return@scheduleAtFixedRate
            tryConnectHosts(reason = "retry-loop")
        }, CONNECT_RETRY_MS, CONNECT_RETRY_MS, TimeUnit.MILLISECONDS)
    }

    private fun stopConnectLoop() {
        retryFuture?.cancel(false)
        retryFuture = null
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectHosts(reason: String) {
        if (hidDevice.get() == null) return
        val bt = adapter ?: return
        if (connectionState == ConnectionState.CONNECTED) return

        try {
            // Discovery holds the radio; pause briefly while we initiate HID connect.
            if (bt.isDiscovering) {
                bt.cancelDiscovery()
            }

            val preferred = prefs.getString(KEY_PREFERRED_HOST, null)
            // Always try preferred MAC first (even if not yet bonded / not in bondedDevices).
            if (preferred != null && BluetoothAdapter.checkBluetoothAddress(preferred)) {
                val prefDev = runCatching { bt.getRemoteDevice(preferred) }.getOrNull()
                if (prefDev != null) {
                    Log.i(TAG, "tryConnectHosts($reason) trying preferred $preferred first")
                    maybeBondAndConnect(prefDev, reason = "$reason-preferred")
                    // If we initiated bond/connect on preferred, don't spam other hosts this tick.
                    if (prefDev.bondState != BluetoothDevice.BOND_BONDED || connecting.get()) {
                        return
                    }
                }
            }

            val bonded = bt.bondedDevices?.toList().orEmpty()
            val ranked = rankHosts(bonded, preferred)

            Log.i(
                TAG,
                "tryConnectHosts($reason) preferred=$preferred bonded=${bonded.size} " +
                    "ranked=${ranked.joinToString { "${safeName(it)}/${it.address}" }}"
            )

            if (ranked.isEmpty()) {
                // No computer bonded yet — keep scanning for host candidates.
                startDiscovery()
                return
            }

            for (device in ranked) {
                // Skip preferred — already handled above.
                if (preferred != null && device.address.equals(preferred, true)) continue
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    maybeBondAndConnect(device, reason)
                    continue
                }
                if (connectHid(device, reason)) {
                    // Only poke one host at a time; retries will walk the list.
                    break
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permission for host connect", e)
            listener.onError("Bluetooth permission missing — grant Connect/Scan/Advertise")
        } catch (e: Exception) {
            Log.w(TAG, "tryConnectHosts($reason)", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun maybeBondAndConnect(device: BluetoothDevice, reason: String) {
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                connectHid(device, reason)
            }
            BluetoothDevice.BOND_BONDING -> {
                Log.i(TAG, "Already bonding ${device.address}")
            }
            else -> {
                if (!isLikelyHost(device) && !isPreferred(device)) {
                    return
                }
                Log.i(TAG, "createBond ${safeName(device)} ${device.address} ($reason)")
                val ok = runCatching { device.createBond() }.getOrDefault(false)
                Log.i(TAG, "createBond -> $ok")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectHid(device: BluetoothDevice, reason: String): Boolean {
        val hid = hidDevice.get() ?: return false
        if (connectionState == ConnectionState.CONNECTED) return true
        if (connecting.getAndSet(true)) {
            Log.d(TAG, "connectHid skip, already connecting")
            return false
        }
        return try {
            // Ensure discovery is stopped — concurrent inquiry breaks ACL/HID setup on many stacks.
            adapter?.let { if (it.isDiscovering) it.cancelDiscovery() }
            Log.i(
                TAG,
                "hid.connect ${safeName(device)} ${device.address} class=${device.bluetoothClass} ($reason)"
            )
            val ok = hid.connect(device)
            Log.i(TAG, "hid.connect(${device.address}) -> $ok")
            if (!ok) connecting.set(false)
            ok
        } catch (e: Exception) {
            connecting.set(false)
            Log.w(TAG, "hid.connect failed", e)
            false
        }
    }

    private fun rankHosts(
        bonded: List<BluetoothDevice>,
        preferred: String?,
    ): List<BluetoothDevice> {
        // NEVER fall back to "all bonded devices" — that tries HID against earbuds/cars/watches.
        val hosts = bonded.filter { isLikelyHost(it) }
        return hosts.sortedWith(
            compareByDescending<BluetoothDevice> { it.address.equals(preferred, true) }
                .thenByDescending { isComputerClass(it) }
                .thenBy { safeName(it).lowercase() }
        )
    }

    private fun isPreferred(device: BluetoothDevice): Boolean {
        val preferred = prefs.getString(KEY_PREFERRED_HOST, null) ?: return false
        return device.address.equals(preferred, ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    private fun isLikelyHost(device: BluetoothDevice): Boolean {
        if (isPreferred(device)) return true
        if (isExcludedPeripheral(device)) return false

        val name = safeName(device).lowercase()
        // Common Linux/macOS/Windows BT adapter names users leave as default
        if (nameContainsHostHint(name)) return true
        return isComputerClass(device)
    }

    /** Audio buds, watches, cars, HID peripherals — never HID-connect as a host. */
    @SuppressLint("MissingPermission")
    private fun isExcludedPeripheral(device: BluetoothDevice): Boolean {
        val cls = device.bluetoothClass
        if (cls != null) {
            when (cls.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO,
                BluetoothClass.Device.Major.WEARABLE,
                BluetoothClass.Device.Major.HEALTH,
                BluetoothClass.Device.Major.TOY,
                BluetoothClass.Device.Major.PERIPHERAL,
                BluetoothClass.Device.Major.PHONE,
                BluetoothClass.Device.Major.IMAGING,
                BluetoothClass.Device.Major.NETWORKING -> return true
            }
        }
        val name = safeName(device).lowercase()
        val blocked = listOf(
            "bud", "headphone", "headset", "airpod", "earphone", "speaker", "soundcore",
            "jlab", "jbl", "beats", "galaxy watch", "pixel watch", "watch", "subaru",
            "toyota", "honda", "car", "vehicle", "keyboard", "mouse", "whip",
        )
        return blocked.any { name.contains(it) }
    }

    private fun nameContainsHostHint(name: String): Boolean =
        name.contains("macbook") ||
            name.contains("imac") ||
            name.contains("mac mini") ||
            name.contains("mac pro") ||
            name.contains("omarchy") ||
            name.contains("desktop") ||
            name.contains("laptop") ||
            name.contains("notebook") ||
            name.contains("pc-") ||
            name.endsWith("-pc") ||
            name.endsWith(" pc") ||
            name.contains("bluez") ||
            name.contains("ubuntu") ||
            name.contains("fedora") ||
            name.contains("arch linux") ||
            name.contains("windows") ||
            name.contains("thinkpad") ||
            name.contains("xps") ||
            name.contains("framework")

    private fun isComputerClass(device: BluetoothDevice): Boolean {
        val cls = device.bluetoothClass ?: return false
        // Only true computers — do NOT treat UNCATEGORIZED as a host (earbuds often use it).
        return cls.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER
    }

    private fun savePreferredHost(address: String) {
        prefs.edit().putString(KEY_PREFERRED_HOST, address).apply()
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun ensureReceivers() {
        if (receiversRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(btReceiver, filter)
            }
            receiversRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "registerReceiver failed", e)
        }
    }

    private fun unregisterReceivers() {
        if (!receiversRegistered) return
        runCatching { context.unregisterReceiver(btReceiver) }
        receiversRegistered = false
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
            @SuppressLint("MissingPermission")
            val n = d.name
            n ?: d.address
        } catch (_: SecurityException) {
            d.address
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
