package io.github.yanniboi.freeze

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object FreezeNotifications {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "freeze_control"

    fun build(context: Context, mode: Mode, overlayPermissionMissing: Boolean, autoUnlock: Boolean): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_freeze)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Android 14+ lets users swipe this away; put it straight back so Unlock is never lost.
            .setDeleteIntent(serviceIntent(context, FreezeService.ACTION_REPOST))

        when {
            mode == Mode.LOCKED -> builder
                .setContentTitle(context.getString(R.string.notif_locked_title))
                .setContentText(
                    context.getString(
                        if (autoUnlock) R.string.notif_locked_text_auto else R.string.notif_locked_text,
                    ),
                )
                // Tapping the body is the one-tap path: Android 12+ hides action buttons when collapsed.
                .setContentIntent(serviceIntent(context, FreezeService.ACTION_TOGGLE))
                .addAction(R.drawable.ic_lock_open, context.getString(R.string.action_unlock), serviceIntent(context, FreezeService.ACTION_UNLOCK))

            overlayPermissionMissing -> builder
                .setContentTitle(context.getString(R.string.notif_permission_title))
                .setContentText(context.getString(R.string.notif_permission_text))
                .setContentIntent(setupIntent(context))
                .addAction(0, context.getString(R.string.action_close), serviceIntent(context, FreezeService.ACTION_STOP))

            else -> builder
                .setContentTitle(context.getString(R.string.notif_ready_title))
                .setContentText(context.getString(R.string.notif_ready_text))
                .setContentIntent(serviceIntent(context, FreezeService.ACTION_TOGGLE))
                .addAction(R.drawable.ic_lock, context.getString(R.string.action_lock), serviceIntent(context, FreezeService.ACTION_LOCK))
                .addAction(0, context.getString(R.string.action_close), serviceIntent(context, FreezeService.ACTION_STOP))
        }
        return builder.build()
    }

    private fun ensureChannel(context: Context) {
        // Default importance keeps it in the main section of the shade rather than "Silent", which
        // matters when you're pulling the shade down one-handed mid-call. No sound, no buzz.
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_name))
            .setDescription(context.getString(R.string.channel_description))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            0,
            FreezeService.intent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun setupIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
