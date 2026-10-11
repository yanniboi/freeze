package io.github.yanniboi.freeze

import android.content.Context
import androidx.core.content.edit

/** How dark the overlay is, as the alpha of a black fill. */
enum class Tint(val alpha: Int) {
    NONE(0x00),
    FAINT(0x14), // ~8%
    DIM(0x40), // ~25%
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("freeze", Context.MODE_PRIVATE)

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }

    var emergencyUnlock: Boolean
        get() = prefs.getBoolean(KEY_EMERGENCY_UNLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_EMERGENCY_UNLOCK, value) }

    var autoUnlockOnCallEnd: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UNLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_UNLOCK, value) }

    var tint: Tint
        get() = prefs.getString(KEY_TINT, null)
            ?.let { name -> Tint.entries.firstOrNull { it.name == name } }
            ?: Tint.FAINT
        set(value) = prefs.edit { putString(KEY_TINT, value.name) }

    var askedForNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, value) }

    private companion object {
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_EMERGENCY_UNLOCK = "emergency_unlock"
        const val KEY_AUTO_UNLOCK = "auto_unlock_on_call_end"
        const val KEY_TINT = "tint"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notification_permission"
    }
}
