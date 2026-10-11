package io.github.yanniboi.freeze

/**
 * Detects [required] taps within [windowMs]. Used for the emergency unlock: deliberate for an adult,
 * unlikely for a toddler. Plain Kotlin so it can be unit tested.
 */
class TapCounter(private val required: Int = 5, private val windowMs: Long = 3_000) {
    private val taps = ArrayDeque<Long>()

    /** Records a tap at [nowMs]; returns true (and resets) when the sequence is complete. */
    fun onTap(nowMs: Long): Boolean {
        taps.addLast(nowMs)
        while (nowMs - taps.first() > windowMs) taps.removeFirst()
        if (taps.size >= required) {
            taps.clear()
            return true
        }
        return false
    }
}
