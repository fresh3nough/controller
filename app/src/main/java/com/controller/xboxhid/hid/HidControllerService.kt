package com.controller.xboxhid.hid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.controller.xboxhid.MainActivity
import com.controller.xboxhid.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that owns the HID connection and pumps input reports ~125 Hz
 * so touch UI updates stay smooth without blocking the main thread.
 */
class HidControllerService : Service(), HidDeviceManager.Listener {

    inner class LocalBinder : Binder() {
        fun getService(): HidControllerService = this@HidControllerService
    }

    interface StatusListener {
        fun onStatus(state: HidDeviceManager.ConnectionState, detail: String?)
    }

    private val binder = LocalBinder()
    private val statusListeners = CopyOnWriteArrayList<StatusListener>()
    private lateinit var hidManager: HidDeviceManager

    private val state = ControllerState()
    private val pending = AtomicReference(ControllerState())
    private val pump = Executors.newSingleThreadScheduledExecutor()
    private var pumpFuture: ScheduledFuture<*>? = null

    @Volatile
    var lastState: HidDeviceManager.ConnectionState = HidDeviceManager.ConnectionState.IDLE
        private set

    override fun onCreate() {
        super.onCreate()
        createChannel()
        hidManager = HidDeviceManager(this, this)
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notification_text)))
        // 125 Hz report pump
        pumpFuture = pump.scheduleAtFixedRate({
            val snap = pending.get()
            synchronized(state) {
                state.copyFrom(snap)
                if (lastState == HidDeviceManager.ConnectionState.CONNECTED) {
                    hidManager.sendState(state)
                }
            }
        }, 0, 8, TimeUnit.MILLISECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> hidManager.start()
            ACTION_STOP -> {
                hidManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        pumpFuture?.cancel(true)
        pump.shutdownNow()
        hidManager.release()
        super.onDestroy()
    }

    fun startAdvertising() {
        hidManager.start()
    }

    fun stopAdvertising() {
        hidManager.stop()
    }

    fun updateState(mutator: (ControllerState) -> Unit) {
        val next = pending.get().copy()
        mutator(next)
        pending.set(next)
    }

    fun setFullState(s: ControllerState) {
        pending.set(s.copy())
    }

    fun addStatusListener(l: StatusListener) {
        statusListeners.add(l)
        l.onStatus(lastState, hidManager.hostName())
    }

    fun removeStatusListener(l: StatusListener) {
        statusListeners.remove(l)
    }

    fun connectionState(): HidDeviceManager.ConnectionState = hidManager.currentState()

    override fun onStateChanged(state: HidDeviceManager.ConnectionState) {
        lastState = state
        val detail = when (state) {
            HidDeviceManager.ConnectionState.CONNECTED ->
                "Connected to ${hidManager.hostName() ?: "host"}"
            HidDeviceManager.ConnectionState.ADVERTISING ->
                getString(R.string.status_advertising)
            HidDeviceManager.ConnectionState.REGISTERING ->
                "Registering HID app…"
            HidDeviceManager.ConnectionState.IDLE ->
                getString(R.string.status_idle)
            HidDeviceManager.ConnectionState.ERROR ->
                getString(R.string.status_error)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(detail))
        statusListeners.forEach { it.onStatus(state, detail) }
    }

    override fun onError(message: String) {
        statusListeners.forEach {
            it.onStatus(HidDeviceManager.ConnectionState.ERROR, message)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "hid_controller"
        const val NOTIF_ID = 42
        const val ACTION_START = "com.controller.xboxhid.START"
        const val ACTION_STOP = "com.controller.xboxhid.STOP"
        // Bump so caches/logs make the descriptor revision obvious
        const val HID_PROTOCOL_REV = "gamepad-v1.3-8bit-ui"

        fun start(context: Context) {
            val i = Intent(context, HidControllerService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, HidControllerService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
