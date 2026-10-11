package io.github.yanniboi.freeze

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings tile. Tapping it toggles the lock directly (starting Freeze if needed): you're
 * already looking at the call when you pull the shade down, so there's no Ready step.
 */
class FreezeTileService : TileService() {
    private val listener: (Mode) -> Unit = { updateTile() }

    override fun onStartListening() {
        super.onStartListening()
        FreezeState.addListener(listener)
        updateTile()
    }

    override fun onStopListening() {
        FreezeState.removeListener(listener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!Permissions.allGranted(this)) {
            launch(Intent(this, MainActivity::class.java))
            return
        }
        try {
            FreezeService.send(this, FreezeService.ACTION_TOGGLE)
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException on Android 12+ if this tap didn't count as
            // "in use". An activity launch from a tile is always allowed, and can start the service.
            Log.w(TAG, "Direct start blocked; using trampoline", e)
            launch(TrampolineActivity.intent(this, FreezeService.ACTION_TOGGLE))
        }
    }

    // The Intent overload is only reached below Android 14, where it's the only option.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val locked = FreezeState.mode == Mode.LOCKED
        tile.state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (locked) R.string.tile_locked else R.string.tile_off)
        }
        tile.updateTile()
    }

    private companion object {
        const val TAG = "FreezeTile"
    }
}
