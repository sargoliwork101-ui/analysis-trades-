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
        sl: Double? = 5.0,
        fee: Double = 0.0
    ) = PaperTradeStore.Trade(
        id = "t1",
        coinId = "sol",
        symbol = "SOL",
        name = "Solana",
        entryPrice = entry,
        amountUsd = amount,
        openedAt = 1_000L,
        takeProfitPct = tp,
        stopLossPct = sl,
        feePct = fee
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
        assertEquals(PaperTradeStore.CloseReason.STOP_LOSS, PaperTradeStore.closeReasonFor(t, 95.0))
        assertEquals(PaperTradeStore.CloseReason.TAKE_PROFIT, PaperTradeStore.closeReasonFor(t, 111.0))
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

    @Test
    fun feesAreChargedOnBothSides() {
        val t = trade(fee = 0.2)
        // خرید: ۲۰۰ دلار منهای ۰٫۲٪ کارمزد = ۱۹۹٫۶ دلار خرید واقعی
        assertEquals(0.4, t.buyFeeUsd, 0.0001)
        assertEquals(1.996, t.units, 0.0001)
        // فروش روی ۱۱۰ دلار: ۲۱۹٫۵۶ منهای ۰٫۲٪ کارمزد
        assertEquals(0.43912, t.sellFeeUsd(110.0)!!, 0.0001)
        assertEquals(19.12088, t.profitUsd(110.0)!!, 0.0001)
        assertEquals(9.56044, t.profitPct(110.0)!!, 0.0001)
        // تغییر خام قیمت همچنان ۱۰٪ است؛ تفاوت همان کارمزد است.
        assertEquals(10.0, t.rawChangePct(110.0)!!, 0.0001)
        // سر به سر کمی بالاتر از قیمت خرید است.
        assertTrue(t.breakEvenPrice!! > t.entryPrice)
        assertEquals(0.0, t.profitUsd(t.breakEvenPrice)!!, 0.0001)
    }

    @Test
    fun walletFeeTotalCountsOpenAndClosedTrades() {
        val open = trade(fee = 0.2)
        val closed = trade(fee = 0.2).copy(id = "t9", closedAt = 5L, closePrice = 110.0)
        val fees = PaperTradeStore.totalFees(listOf(open, closed), mapOf("sol" to 110.0))
        assertEquals((0.4 + 0.43912) * 2, fees, 0.0001)
    }

    private fun plan(
        high: Double = 100.0,
        low: Double = 80.0,
        steps: Int = 3,
        sl: Double? = 10.0,
        filled: Int = 0
    ) = PaperTradeStore.Plan(
        id = "p1",
        coinId = "sol",
        symbol = "SOL",
        name = "Solana",
        totalUsd = 300.0,
        entryHigh = high,
        entryLow = low,
        steps = steps,
        filledSteps = filled,
        takeProfitPct = 12.0,
        stopLossPct = sl,
        feePct = 0.2,
        createdAt = 1L
    )

    @Test
    fun ladderSplitsTheEntryRangeFromTopToBottom() {
        assertEquals(listOf(100.0, 90.0, 80.0), PaperTradeStore.ladderPrices(100.0, 80.0, 3))
        assertEquals(listOf(100.0), PaperTradeStore.ladderPrices(100.0, 80.0, 1))
        // ترتیب ورودی مهم نیست؛ همیشه از سقف به کف
        assertEquals(listOf(100.0, 80.0), PaperTradeStore.ladderPrices(80.0, 100.0, 2))
        assertEquals(100.0, plan().stepUsd, 0.0001)
    }

    @Test
    fun stepsFillOnlyWhenPriceReachesThem() {
        val p = plan()
        assertEquals(0, PaperTradeStore.filledStepsAt(p, 105.0))
        assertEquals(1, PaperTradeStore.filledStepsAt(p, 100.0))
        assertEquals(2, PaperTradeStore.filledStepsAt(p, 88.0))
        assertEquals(3, PaperTradeStore.filledStepsAt(p, 70.0))
        // پله‌ی پرشده دوباره باز نمی‌گردد
        assertEquals(2, PaperTradeStore.filledStepsAt(p.copy(filledSteps = 2), 99.0))
    }

    @Test
    fun planIsCancelledBelowTheRangeStopLoss() {
        val p = plan()
        assertEquals(72.0, p.cancelPrice!!, 0.0001)
        assertTrue(!PaperTradeStore.planShouldCancel(p, 75.0))
        assertTrue(PaperTradeStore.planShouldCancel(p, 72.0))
        assertTrue(PaperTradeStore.planShouldCancel(p, 60.0))
        assertTrue(!PaperTradeStore.planShouldCancel(plan(sl = null), 1.0))
    }

    @Test
    fun planStaysActiveUntilAllStepsFillOrItIsCancelled() {
        assertTrue(plan().isActive)
        assertTrue(!plan(filled = 3).isActive)
        assertTrue(!plan().copy(canceledAt = 9L).isActive)
    }
}
