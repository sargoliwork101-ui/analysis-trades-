package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های منطق خالصِ «خرید پله‌ای» شبیه‌ساز — بدون اندروید، روی JVM.
 * فقط توابع internalِ PaperTradeStore بررسی می‌شوند (بدون Context).
 */
class PaperTradeLadderTest {

    private fun trade(
        entryPrice: Double,
        amountUsd: Double,
        steps: List<PaperTradeStore.LadderStep> = emptyList(),
        takeProfitPct: Double? = null,
        stopLossPct: Double? = null
    ) = PaperTradeStore.Trade(
        id = "t",
        coinId = "solana",
        symbol = "SOL",
        name = "Solana",
        entryPrice = entryPrice,
        amountUsd = amountUsd,
        openedAt = 1_000L,
        takeProfitPct = takeProfitPct,
        stopLossPct = stopLossPct,
        steps = steps
    )

    @Test
    fun singleStepMeansNoLadder() {
        assertTrue(PaperTradeStore.buildLadderSteps(100.0, 100.0, 1, 10.0, 0L).isEmpty())
        assertTrue(PaperTradeStore.buildLadderSteps(100.0, 100.0, 4, null, 0L).isEmpty())
    }

    @Test
    fun ladderStepsSpanRangeAndFirstFillsNow() {
        val steps = PaperTradeStore.buildLadderSteps(100.0, 100.0, 4, 9.0, 5L)
        assertEquals(4, steps.size)
        assertEquals(100.0, steps.first().price, 1e-6)
        assertEquals(91.0, steps.last().price, 1e-6)
        // فقط پله‌ی اول همان لحظه پر می‌شود
        assertTrue(steps[0].filled)
        assertFalse(steps[1].filled)
        assertFalse(steps[3].filled)
        // مجموع مبلغ پله‌ها = کل مبلغ
        assertEquals(100.0, steps.sumOf { it.amountUsd }, 1e-6)
    }

    @Test
    fun averageEntryDropsAsLowerStepsFill() {
        val steps = PaperTradeStore.buildLadderSteps(100.0, 100.0, 4, 9.0, 5L)
        val base = PaperTradeStore.recomputeFromFills(trade(100.0, 100.0, steps))
        // فقط پله‌ی اول پر است → میانگین = ۱۰۰ و مبلغ مؤثر = ۲۵
        assertEquals(100.0, base.entryPrice, 1e-6)
        assertEquals(25.0, base.amountUsd, 1e-6)

        // قیمت به ۹۴ می‌رسد → پله‌های ۹۷ و ۹۴ هم پر می‌شوند
        val filled = PaperTradeStore.fillReachedSteps(base, 94.0, 10L)
        assertEquals(3, filled.filledStepCount)
        assertEquals(75.0, filled.amountUsd, 1e-6)
        // میانگین هزینه بین کف و سقفِ پرشده و کمتر از ۱۰۰
        assertTrue(filled.entryPrice in 94.0..100.0)
        val expected = 75.0 / (25.0 / 100.0 + 25.0 / 97.0 + 25.0 / 94.0)
        assertEquals(expected, filled.entryPrice, 1e-6)
    }

    @Test
    fun stopLossUsesRecomputedAverage() {
        val steps = PaperTradeStore.buildLadderSteps(100.0, 100.0, 2, 10.0, 5L)
        val t = PaperTradeStore.recomputeFromFills(
            trade(100.0, 100.0, steps, stopLossPct = 5.0)
        )
        // با یک پله‌ی پرشده، میانگین=۱۰۰ و حد ضرر روی ۹۵
        assertEquals(PaperTradeStore.CloseReason.STOP_LOSS, PaperTradeStore.closeReasonFor(t, 95.0))
        assertEquals(null, PaperTradeStore.closeReasonFor(t, 96.0))
    }

    @Test
    fun fillReachedStepsIsNoOpWhenNothingReached() {
        val steps = PaperTradeStore.buildLadderSteps(100.0, 100.0, 3, 10.0, 5L)
        val base = PaperTradeStore.recomputeFromFills(trade(100.0, 100.0, steps))
        // قیمت بالای همه‌ی پله‌های پرنشده → هیچ تغییری نباید بدهد (همان شیء)
        val same = PaperTradeStore.fillReachedSteps(base, 100.0, 20L)
        assertTrue(same === base)
    }
}
