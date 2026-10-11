package io.github.yanniboi.freeze

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 1x1 home screen widget. Tapping it starts Freeze (shows the notification); it never locks by
 * itself, because the overlay would land on the home screen instead of the call.
 */
class FreezeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    companion object {
        /** Redraws every placed widget, e.g. after a state or permission change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FreezeWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val mode = FreezeState.mode
            val views = RemoteViews(context.packageName, R.layout.widget_freeze)
            views.setImageViewResource(
                R.id.widget_icon,
                if (mode == Mode.LOCKED) R.drawable.ic_widget_lock else R.drawable.ic_widget_lock_open,
            )
            views.setTextViewText(
                R.id.widget_label,
                context.getString(
                    when (mode) {
                        Mode.OFF -> R.string.widget_label
                        Mode.READY -> R.string.widget_ready
                        Mode.LOCKED -> R.string.widget_locked
                    },
                ),
            )

            // Without permissions, starting the service would just produce a notification that can't do
            // anything (or no notification at all), so send the user to the setup screen instead.
            val click = if (Permissions.allGranted(context)) {
                PendingIntent.getForegroundService(
                    context,
                    0,
                    FreezeService.intent(context, FreezeService.ACTION_START),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, click)
            return views
        }
    }
}
