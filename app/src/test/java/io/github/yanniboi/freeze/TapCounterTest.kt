package io.github.yanniboi.freeze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapCounterTest {
    @Test
    fun `five quick taps trigger`() {
        val counter = TapCounter(required = 5, windowMs = 3_000)
        repeat(4) { assertFalse(counter.onTap(it * 200L)) }
        assertTrue(counter.onTap(800))
    }

    @Test
    fun `slow taps never trigger`() {
        val counter = TapCounter(required = 5, windowMs = 3_000)
        repeat(20) { assertFalse(counter.onTap(it * 1_000L)) }
    }

    @Test
    fun `taps older than the window are forgotten`() {
        val counter = TapCounter(required = 5, windowMs = 3_000)
        repeat(4) { counter.onTap(it * 100L) } // 0..300
        // Next tap is outside the window of all four, so this starts a new sequence.
        assertFalse(counter.onTap(5_000))
        repeat(3) { assertFalse(counter.onTap(5_100L + it * 100)) }
        assertTrue(counter.onTap(5_500))
    }

    @Test
    fun `resets after triggering`() {
        val counter = TapCounter(required = 5, windowMs = 3_000)
        repeat(4) { counter.onTap(it * 100L) }
        assertTrue(counter.onTap(400))
        assertFalse(counter.onTap(500))
    }
}
