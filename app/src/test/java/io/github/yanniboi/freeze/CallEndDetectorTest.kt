package io.github.yanniboi.freeze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEndDetectorTest {
    @Test
    fun `no call seen means never ended`() {
        val detector = CallEndDetector(quietPeriodMs = 3_000)
        for (t in 0L..60_000L step 1_000) assertFalse(detector.observe(inCall = false, nowMs = t))
    }

    @Test
    fun `ends after the quiet period following a call`() {
        val detector = CallEndDetector(quietPeriodMs = 3_000)
        assertFalse(detector.observe(inCall = true, nowMs = 0))
        assertFalse(detector.observe(inCall = false, nowMs = 1_000))
        assertFalse(detector.observe(inCall = false, nowMs = 3_000))
        assertTrue(detector.observe(inCall = false, nowMs = 4_000))
    }

    @Test
    fun `brief drop out of call mode does not end it`() {
        val detector = CallEndDetector(quietPeriodMs = 3_000)
        detector.observe(inCall = true, nowMs = 0)
        assertFalse(detector.observe(inCall = false, nowMs = 1_000)) // hold / switching
        assertFalse(detector.observe(inCall = false, nowMs = 2_000))
        assertFalse(detector.observe(inCall = true, nowMs = 3_000)) // back in the call
        assertFalse(detector.observe(inCall = false, nowMs = 5_000))
        assertFalse(detector.observe(inCall = false, nowMs = 7_000))
        assertTrue(detector.observe(inCall = false, nowMs = 8_000))
    }

    @Test
    fun `call starting after lock is still detected`() {
        val detector = CallEndDetector(quietPeriodMs = 3_000)
        assertFalse(detector.observe(inCall = false, nowMs = 0))
        assertFalse(detector.observe(inCall = false, nowMs = 10_000))
        assertFalse(detector.observe(inCall = true, nowMs = 11_000))
        assertFalse(detector.observe(inCall = false, nowMs = 12_000))
        assertTrue(detector.observe(inCall = false, nowMs = 15_000))
    }
}
