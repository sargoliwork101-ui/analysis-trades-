package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های شبیه‌ساز معامله — منطق خالص، بدون اندروید */
class PaperTradeStoreTest {

    private fun trade(
        entry: Double = 100.0,
        amount: Double = 200.0,
        tp: Double? = 10.0,
        sl: Double? = 5.0
    ) = PaperTradeStore.Trade(
        id = "t1",
        coinId = "sol",
        symbol = "SOL",
        name = "Solana",
        entryPrice = entry,
        amountUsd = amount,
        openedAt = 1_000L,
        takeProfitPct = tp,
        stopLossPct = sl
    )

    @Test
    fun levelsAndProfitAreComputedFromEntry() {
        val t = trade()
        assertEquals(110.0, t.takeProfitPrice!!, 0.0001)
        assertEquals(95.0, t.stopLossPrice!!, 0.0001)
        assertEquals(2.0, t.units, 0.0001)
        assertEquals(20.0, t.profitPct(120.0)!!, 0.0001)
        assertEquals(40.0, t.profitUsd(120.0)!!, 0.0001)
        assertEquals(-10.0, t.profitPct(90.0)!!, 0.0001)
    }

    @Test
    fun takeProfitAndStopLossTrigger() {
        val t = trade()
        assertNull(PaperTradeStore.closeReasonFor(t, 105.0))
        assertEquals(PaperTradeStore.CloseReason.TAKE_PROFIT, PaperTradeStore.closeReasonFor(t, 110.0))
        assertEquals(PaperTradeStore.CloseReason.STOP_LOSS, PaperTradeStore.closeReasonFor(t, 94.0))
        // اگر هر دو سطح در یک به‌روزرسانی رد شده باشند، حد ضرر محافظه‌کارانه مقدم است.
        val wide = trade(tp = 1.0, sl = 1.0)
        assertEquals(PaperTradeStore.CloseReason.STOP_LOSS, PaperTradeStore.closeReasonFor(wide, 50.0))
        // معامله‌ی بسته دیگر بسته نمی‌شود.
        val closed = t.copy(closedAt = 2_000L, closePrice = 110.0)
        assertNull(PaperTradeStore.closeReasonFor(closed, 200.0))
    }

    @Test
    fun triggerPriceUsesTheLevelNotTheGapPrice() {
        val t = trade()
        assertEquals(
            110.0,
            PaperTradeStore.triggerPrice(t, PaperTradeStore.CloseReason.TAKE_PROFIT, 180.0),
            0.0001
        )
        assertEquals(
            95.0,
            PaperTradeStore.triggerPrice(t, PaperTradeStore.CloseReason.STOP_LOSS, 10.0),
            0.0001
        )
    }

    @Test
    fun summaryAddsRealizedAndOpenProfit() {
        val openTrade = trade()
        val winner = trade().copy(id = "t2", closedAt = 2_000L, closePrice = 110.0)
        val loser = trade().copy(id = "t3", closedAt = 2_000L, closePrice = 95.0)
        val summary = PaperTradeStore.summarize(
            listOf(openTrade, winner, loser),
            mapOf("sol" to 120.0)
        )
        assertEquals(1, summary.openCount)
        assertEquals(2, summary.closedCount)
        assertEquals(1, summary.wins)
        assertEquals(1, summary.losses)
        assertEquals(20.0 + (-10.0), summary.realizedUsd, 0.0001)
        assertEquals(40.0, summary.openUsd, 0.0001)
        assertEquals(50.0, summary.winRatePct, 0.0001)
    }

    @Test
    fun resultTextShowsSignedPercentAndAmount() {
        val text = PaperTradeStore.resultText(trade(), 120.0, persianDigits = false)
        assertTrue(text.startsWith("+"))
        assertTrue(text.contains("20.00"))
        assertTrue(text.contains("40.00"))
    }
}
