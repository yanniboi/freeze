package io.github.yanniboi.freeze

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Adds and removes the full-screen window that swallows touches.
 *
 * TYPE_APPLICATION_OVERLAY windows sit above every app (including the dialer) but below the status
 * bar and notification shade, so the shade can still be pulled down to unlock.
 */
class OverlayController(private val context: Context, private val onEmergencyUnlock: () -> Unit) {
    private var view: View? = null
    private var windowManager: WindowManager? = null

    /** Shows the overlay; returns false if it couldn't (usually the permission was revoked). */
    fun show(prefs: Prefs): Boolean {
        if (view != null) return true
        if (!Permissions.canDrawOverlays(context)) return false

        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // A service isn't tied to a display, so get one first; a window context then gets the right
            // configuration (rotation, density) for the overlay.
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            context.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            context
        }
        val wm = windowContext.getSystemService(WindowManager::class.java)
        val overlay = OverlayView(windowContext, prefs.tint.alpha, prefs.emergencyUnlock, onEmergencyUnlock)

        return try {
            wm.addView(overlay, layoutParams(prefs.keepScreenOn))
            view = overlay
            windowManager = wm
            true
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException if the permission vanished between check and add.
            Log.w(TAG, "Couldn't add overlay", e)
            false
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            windowManager?.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay was already gone", e)
        }
        view = null
        windowManager = null
    }

    private fun layoutParams(keepScreenOn: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or // keys/volume still reach the call
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (keepScreenOn) flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "Freeze"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0 // cover the whole screen, bar areas included
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private companion object {
        const val TAG = "FreezeOverlay"
    }
}

/** The see-through layer itself: eats every touch, shows a lock badge that fades out. */
@SuppressLint("ViewConstructor")
private class OverlayView(
    context: Context,
    tintAlpha: Int,
    private val emergencyUnlockEnabled: Boolean,
    private val onEmergencyUnlock: () -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val tapCounter = TapCounter()
    private val handler = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.argb(tintAlpha, 0, 0, 0))
        contentDescription = context.getString(R.string.overlay_description)

        val padding = (10 * density).toInt()
        val badge = ImageView(context).apply {
            setImageResource(R.drawable.ic_lock)
            setBackgroundResource(R.drawable.overlay_badge_bg)
            setPadding(padding, padding, padding, padding)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val size = (48 * density).toInt()
        addView(
            badge,
            LayoutParams(size, size, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (120 * density).toInt()
            },
        )
        badge.animate().alpha(BADGE_RESTING_ALPHA).setStartDelay(BADGE_FADE_DELAY_MS).setDuration(600).start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && emergencyUnlockEnabled && isInUnlockCorner(event)) {
            performClick()
            // Posted: unlocking removes this window, which shouldn't happen mid-dispatch.
            if (tapCounter.onTap(event.eventTime)) handler.post(onEmergencyUnlock)
        }
        return true // swallow everything
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Top-right corner, tall enough to reach below the status bar (which takes its own touches). */
    private fun isInUnlockCorner(event: MotionEvent): Boolean =
        event.x >= width - CORNER_WIDTH_DP * density && event.y <= CORNER_HEIGHT_DP * density

    private companion object {
        const val CORNER_WIDTH_DP = 120
        const val CORNER_HEIGHT_DP = 180
        const val BADGE_RESTING_ALPHA = 0.2f
        const val BADGE_FADE_DELAY_MS = 2_000L
    }
}
