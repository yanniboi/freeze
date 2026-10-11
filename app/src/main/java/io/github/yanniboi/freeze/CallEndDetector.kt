package io.github.yanniboi.freeze

/**
 * Decides when a call has ended, from a stream of "is a call active right now?" samples.
 *
 * A call only counts as ended once one has been seen and the phone has then stayed out of call mode
 * for [quietPeriodMs]. That rides out the brief mode changes during hold or call switching, and it
 * means locking with no call active (e.g. a child watching a video) never auto-unlocks.
 *
 * Plain Kotlin, driven by the caller's clock, so it can be unit tested.
 */
class CallEndDetector(private val quietPeriodMs: Long = 3_000) {
    private var sawCall = false
    private var quietSinceMs: Long? = null

    /** Feeds one sample; returns true once the call is considered over. */
    fun observe(inCall: Boolean, nowMs: Long): Boolean {
        if (inCall) {
            sawCall = true
            quietSinceMs = null
            return false
        }
        if (!sawCall) return false
        val quietSince = quietSinceMs ?: nowMs.also { quietSinceMs = it }
        return nowMs - quietSince >= quietPeriodMs
    }
}
