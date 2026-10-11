package io.github.yanniboi.freeze

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Calls [onCallEnded] when the call that was active during the lock finishes.
 *
 * Watches the audio mode rather than the telephony call state: MODE_IN_CALL covers regular calls,
 * MODE_IN_COMMUNICATION covers WhatsApp/Signal/Meet and other VoIP apps, and none of it needs a
 * permission. It polls once a second, only while locked; that's one cheap system call while the
 * screen is on anyway.
 */
class CallMonitor(context: Context, private val onCallEnded: () -> Unit) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var detector = CallEndDetector()
    private var running = false

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            if (detector.observe(isInCall(), SystemClock.uptimeMillis())) {
                stop()
                onCallEnded()
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        detector = CallEndDetector()
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
    }

    // The newer constants are compile-time ints, so they're safe to compare against on older releases.
    @SuppressLint("InlinedApi")
    private fun isInCall(): Boolean = when (audioManager.mode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING,
        AudioManager.MODE_CALL_REDIRECT,
        AudioManager.MODE_COMMUNICATION_REDIRECT,
        -> true
        else -> false
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
    }
}
