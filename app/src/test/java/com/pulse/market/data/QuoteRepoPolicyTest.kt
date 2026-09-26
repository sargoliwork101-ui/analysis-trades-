package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** سیاست‌های خالص سبک‌سازی کش؛ بدون نیاز به Context یا شبکه. */
class QuoteRepoPolicyTest {

    @Test
    fun diskFlushIsThrottledButRecoversFromClockRollback() {
        val now = 100_000L
        assertTrue(QuoteRepo.shouldFlushToDisk(0L, now))
        assertFalse(QuoteRepo.shouldFlushToDisk(now - 29_999L, now))
        assertTrue(QuoteRepo.shouldFlushToDisk(now - 30_000L, now))
        assertTrue(QuoteRepo.shouldFlushToDisk(now + 1L, now))
    }

    @Test
    fun pruningKeepsOnlyActiveWidgetAndAlertKeys() {
        val values = linkedMapOf(
            "crypto|bitcoin" to 1,
            "tse|فولاد" to 2,
            "old|removed" to 3
        )

        assertEquals(
            linkedMapOf("crypto|bitcoin" to 1, "tse|فولاد" to 2),
            QuoteRepo.retainActive(values, setOf("crypto|bitcoin", "tse|فولاد"))
        )
        assertTrue(QuoteRepo.retainActive(values, emptySet()).isEmpty())
    }
}
