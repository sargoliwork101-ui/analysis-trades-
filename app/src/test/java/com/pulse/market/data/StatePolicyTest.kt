package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatePolicyTest {

    @Test
    fun alertHistoryIdIncludesWidgetOwner() {
        val at = 1_700_000_000_000L
        val first = AlertEngine.eventId("widget_10", "copied-rule", at)
        val second = AlertEngine.eventId("widget_11", "copied-rule", at)
        assertNotEquals(first, second)
        assertEquals(first, AlertEngine.eventId("widget_10", "copied-rule", at))
    }

    @Test
    fun pumpScanTimestampRecoversFromClockRollbackButRejectsDuplicatesAndOldCache() {
        val nowAfterRollback = 1_000L
        assertTrue(
            PumpAlertEngine.shouldEvaluateScan(
                scanAt = nowAfterRollback,
                lastSeen = 100_000L,
                now = nowAfterRollback
            )
        )
        assertFalse(PumpAlertEngine.shouldEvaluateScan(1_000L, 1_000L, 1_000L))
        assertFalse(PumpAlertEngine.shouldEvaluateScan(1_000L, 0L, 400_001L))
    }

    @Test
    fun pumpNotificationUsesLargeStableOwnerSpace() {
        assertEquals(
            PumpAlertEngine.notificationId("widget_7"),
            PumpAlertEngine.notificationId("widget_7")
        )
        val ids = (1..5_000).map { PumpAlertEngine.notificationId("widget_$it") }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun partialSourceResponseIsNotHealthy() {
        assertTrue(
            SourceHealth(
                sourceId = "ok",
                lastSuccessAt = 1L,
                successCount = 2,
                totalCount = 2
            ).isHealthy
        )
        assertFalse(
            SourceHealth(
                sourceId = "partial",
                lastSuccessAt = 1L,
                successCount = 1,
                totalCount = 2,
                lastError = "برخی نمادها پاسخ معتبر ندادند"
            ).isHealthy
        )
    }

    @Test
    fun quoteCacheKeysCannotCollideWhenValuesContainDelimiter() {
        assertNotEquals(QuoteRepo.key("a|b", "c"), QuoteRepo.key("a", "b|c"))
    }
}
