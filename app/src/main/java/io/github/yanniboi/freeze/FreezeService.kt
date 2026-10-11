package io.github.yanniboi.freeze

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Owns the lock: the foreground notification, the overlay, and the call-end monitor. Everything else
 * (widget, tile, notification buttons, setup screen) just sends it an action.
 */
class FreezeService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayController
    private lateinit var callMonitor: CallMonitor
    private val handler = Handler(Looper.getMainLooper())
    private val endTestLock = Runnable { unlock() }
    private var overlayPermissionMissing = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        overlay = OverlayController(this) { unlock() }
        callMonitor = CallMonitor(this) { onCallEnded() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayPermissionMissing && Permissions.canDrawOverlays(this)) overlayPermissionMissing = false
        if (FreezeState.mode == Mode.OFF) {
            FreezeState.set(Mode.READY)
            refreshWidgetAndTile()
        }
        // Every start came through startForegroundService, so startForeground must be called promptly,
        // even for STOP.
        showForeground()

        when (intent?.action) {
            ACTION_LOCK -> lock()
            ACTION_UNLOCK -> unlock()
            ACTION_TOGGLE -> if (FreezeState.mode == Mode.LOCKED) unlock() else lock()
            ACTION_TEST_LOCK -> {
                lock()
                if (FreezeState.mode == Mode.LOCKED) handler.postDelayed(endTestLock, TEST_LOCK_MS)
            }
            ACTION_STOP -> stop()
            // ACTION_START / ACTION_REPOST: being in the foreground with a fresh notification is all they need.
        }
        // Never restart after being killed: the phone must not come back locked on its own.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        callMonitor.stop()
        overlay.hide()
        FreezeState.set(Mode.OFF)
        refreshWidgetAndTile()
        super.onDestroy()
    }

    private fun lock() {
        if (FreezeState.mode == Mode.LOCKED) return
        if (!overlay.show(prefs)) {
            overlayPermissionMissing = true
            setMode(Mode.READY)
            return
        }
        overlayPermissionMissing = false
        if (prefs.autoUnlockOnCallEnd) callMonitor.start()
        setMode(Mode.LOCKED)
    }

    private fun unlock() {
        handler.removeCallbacks(endTestLock)
        callMonitor.stop()
        overlay.hide()
        if (FreezeState.mode == Mode.LOCKED) setMode(Mode.READY)
    }

    private fun stop() {
        unlock()
        setMode(Mode.OFF)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onCallEnded() {
        if (FreezeState.mode != Mode.LOCKED) return
        unlock()
        buzz()
    }

    private fun setMode(mode: Mode) {
        FreezeState.set(mode)
        if (mode != Mode.OFF) showForeground()
        refreshWidgetAndTile()
    }

    private fun showForeground() {
        val notification = FreezeNotifications.build(
            this,
            FreezeState.mode,
            overlayPermissionMissing,
            prefs.autoUnlockOnCallEnd,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, FreezeNotifications.NOTIFICATION_ID, notification, type)
    }

    private fun refreshWidgetAndTile() {
        FreezeWidgetProvider.refresh(this)
        try {
            TileService.requestListeningState(this, ComponentName(this, FreezeTileService::class.java))
        } catch (e: RuntimeException) {
            // Some devices throw if the tile isn't added; the tile refreshes itself when shown anyway.
            Log.d(TAG, "Tile refresh skipped", e)
        }
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private const val TAG = "FreezeService"
        private const val TEST_LOCK_MS = 5_000L

        const val ACTION_START = "io.github.yanniboi.freeze.action.START"
        const val ACTION_LOCK = "io.github.yanniboi.freeze.action.LOCK"
        const val ACTION_UNLOCK = "io.github.yanniboi.freeze.action.UNLOCK"
        const val ACTION_TOGGLE = "io.github.yanniboi.freeze.action.TOGGLE"
        const val ACTION_TEST_LOCK = "io.github.yanniboi.freeze.action.TEST_LOCK"
        const val ACTION_STOP = "io.github.yanniboi.freeze.action.STOP"
        const val ACTION_REPOST = "io.github.yanniboi.freeze.action.REPOST"

        fun intent(context: Context, action: String): Intent =
            Intent(context, FreezeService::class.java).setAction(action)

        /** Starts (or pokes) the service. Can throw if Android blocks a background start. */
        fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(context, intent(context, action))
        }
    }
}
