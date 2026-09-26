package com.pulse.market.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAnomalyDetectorTest {

    @Test
    fun smallMovementIsAccepted() {
        assertFalse(PriceAnomalyDetector.inspect(100.0, 120.0, null).suspicious)
    }

    @Test
    fun unexplainedLargeJumpIsQuarantined() {
        assertTrue(PriceAnomalyDetector.inspect(100.0, 180.0, null).suspicious)
    }

    @Test
    fun officialChangeCanExplainLargeJump() {
        assertFalse(PriceAnomalyDetector.inspect(100.0, 180.0, 80.0).suspicious)
    }

    @Test
    fun secondSimilarSampleConfirmsCandidate() {
        val now = 1_000_000L
        val candidate = PriceAnomalyCandidate(price = 180.0, firstSeenAt = now - 60_000L)
        assertTrue(PriceAnomalyDetector.confirms(candidate, 182.0, now))
        assertFalse(PriceAnomalyDetector.confirms(candidate, 210.0, now))
    }
}
