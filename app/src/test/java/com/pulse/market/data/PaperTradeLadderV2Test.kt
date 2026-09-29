package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های نسخه‌ی جدیدِ پله‌ای: خریدِ پله‌ای با «قیمتِ دقیقِ هر پله» و «فروش پله‌ای».
 * همه روی JVM و بدون اندروید (فقط توابع internalِ خالص).
 */
class PaperTradeLadderV2Test {

    private fun trade(
        entryPrice: Double,
        amountUsd: Double,
        feePct: Double = 0.0,
        sellSteps: List<PaperTradeStore.SellStep> = emptyList(),
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
        feePct = feePct,
        takeProfitPct = takeProfitPct,
        stopLossPct = stopLossPct,
        sellSteps = sellSteps
    )

    // ───────────── خرید پله‌ای با قیمتِ دقیق ─────────────

    @Test
    fun manualBuyStepsSortedAndFilledByMarket() {
        val steps = PaperTradeStore.buildLadderStepsFromPrices(
            currentPrice = 100.0, amountUsd = 90.0, prices = listOf(90.0, 100.0, 95.0), now = 5L
        )
        assertEquals(3, steps.size)
        // مرتب نزولی: 100، 95، 90
        assertEquals(100.0, steps[0].price, 1e-9)
        assertEquals(95.0, steps[1].price, 1e-9)
        assertEquals(90.0, steps[2].price, 1e-9)
        // فقط پله‌ای که بازار (۱۰۰) به آن رسیده پر می‌شود
        assertTrue(steps[0].filled)
        assertFalse(steps[1].filled)
        assertFalse(steps[2].filled)
        // مبلغ برابر
        assertEquals(30.0, steps[0].amountUsd, 1e-9)
        assertEquals(90.0, steps.sumOf { it.amountUsd }, 1e-9)
    }

    @Test
    fun manualBuyStepsForceAtLeastOneFillWhenAllBelowMarket() {
        val steps = PaperTradeStore.buildLadderStepsFromPrices(
            currentPrice = 100.0, amountUsd = 60.0, prices = listOf(95.0, 90.0), now = 5L
        )
        assertEquals(2, steps.size)
        // هیچ‌کدام ≥ بازار نیستند → بالاترین (۹۵) به‌اجبار همین حالا پر می‌شود
        assertEquals(95.0, steps[0].price, 1e-9)
        assertTrue(steps[0].filled)
        assertFalse(steps[1].filled)
    }

    @Test
    fun fewerThanTwoPricesMeansNoLadder() {
        assertTrue(
            PaperTradeStore.buildLadderStepsFromPrices(100.0, 90.0, listOf(95.0), 0L).isEmpty()
        )
        assertTrue(
            PaperTradeStore.buildLadderStepsFromPrices(100.0, 90.0, emptyList(), 0L).isEmpty()
        )
    }

    // ───────────── فروش پله‌ای ─────────────

    @Test
    fun sellStepsGetEqualFractionsSortedAscending() {
        val steps = PaperTradeStore.buildSellStepsFromPrices(listOf(120.0, 110.0, 130.0))
        assertEquals(3, steps.size)
        assertEquals(110.0, steps[0].price, 1e-9)
        assertEquals(130.0, steps[2].price, 1e-9)
        steps.forEach { assertEquals(1.0 / 3.0, it.fraction, 1e-9) }
        assertTrue(PaperTradeStore.buildSellStepsFromPrices(listOf(110.0)).isEmpty())
    }

    @Test
    fun fillReachedSellStepsFillsHitTargetsOnly() {
        val t = trade(
            100.0, 100.0,
            sellSteps = PaperTradeStore.buildSellStepsFromPrices(listOf(110.0, 120.0))
        )
        val after = PaperTradeStore.fillReachedSellSteps(t, 115.0, 9L)
        assertEquals(1, after.filledSellStepCount)
        assertEquals(0.5, after.soldFraction, 1e-9)
        // قیمت پایین‌تر از همه → بدون تغییر (همان شیء)
        assertTrue(PaperTradeStore.fillReachedSellSteps(t, 100.0, 9L) === t)
        // قیمت بالاتر از همه → هر دو پر
        val all = PaperTradeStore.fillReachedSellSteps(t, 125.0, 9L)
        assertEquals(2, all.filledSellStepCount)
        assertEquals(1.0, all.remainingFraction + all.soldFraction, 1e-9)
    }

    @Test
    fun partialSellRealizesProfitOnSoldPortion() {
        // بدون کارمزد؛ واحدها = 1.0 (۱۰۰ دلار ÷ ۱۰۰)
        val steps = listOf(
            PaperTradeStore.SellStep(price = 110.0, fraction = 0.5, filled = true),
            PaperTradeStore.SellStep(price = 120.0, fraction = 0.5, filled = false)
        )
        val t = trade(100.0, 100.0, sellSteps = steps)
        // نصف در ۱۱۰ فروخته (۵۵)، نصفِ باقی در قیمت فعلی ۱۱۵ (۵۷.۵) → ارزش کل ۱۱۲.۵
        assertEquals(112.5, t.exitValueUsd(115.0)!!, 1e-6)
        assertEquals(12.5, t.profitUsd(115.0)!!, 1e-6)
        assertEquals(12.5, t.profitPct(115.0)!!, 1e-6)
    }

    @Test
    fun fullySoldProfitIsIndependentOfCurrentPrice() {
        val steps = listOf(
            PaperTradeStore.SellStep(price = 110.0, fraction = 0.5, filled = true),
            PaperTradeStore.SellStep(price = 120.0, fraction = 0.5, filled = true)
        )
        val t = trade(100.0, 100.0, sellSteps = steps)
        // ۵۵ + ۶۰ = ۱۱۵ دریافتی، سود ۱۵ — بدون نیاز به قیمت فعلی
        assertEquals(15.0, t.profitUsd(null)!!, 1e-6)
        assertEquals(115.0, t.avgSoldPrice!!, 1e-6)
    }

    @Test
    fun sellLadderIgnoresSingleTakeProfitButKeepsStopLoss() {
        val t = trade(
            100.0, 100.0,
            sellSteps = PaperTradeStore.buildSellStepsFromPrices(listOf(110.0, 120.0)),
            takeProfitPct = 5.0,
            stopLossPct = 10.0
        )
        // حد سودِ تک‌مرحله‌ای نادیده گرفته می‌شود
        assertNull(PaperTradeStore.closeReasonFor(t, 130.0))
        // ولی حد ضرر روی سهمِ باقی‌مانده می‌ماند
        assertEquals(
            PaperTradeStore.CloseReason.STOP_LOSS,
            PaperTradeStore.closeReasonFor(t, 90.0)
        )
    }
}
