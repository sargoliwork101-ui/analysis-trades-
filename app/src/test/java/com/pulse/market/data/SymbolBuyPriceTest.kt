package com.pulse.market.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تستِ «قیمت خرید» هر نماد و محاسبه‌ی درصد سود/زیان که روی ویجت زیرِ همان نماد
 * نمایش داده می‌شود.
 */
class SymbolBuyPriceTest {

    @Test
    fun profitIsPositiveWhenPriceAboveBuy() {
        val sym = SymbolDef(code = "bitcoin", label = "Bitcoin", buyPrice = 100.0)
        // ۱۲۵ از ۱۰۰ یعنی ۲۵٪ سود
        assertEquals(25.0, sym.profitPct(125.0)!!, 1e-9)
    }

    @Test
    fun lossIsNegativeWhenPriceBelowBuy() {
        val sym = SymbolDef(code = "eth", label = "ETH", buyPrice = 200.0)
        // ۱۵۰ از ۲۰۰ یعنی ۲۵٪ زیان
        assertEquals(-25.0, sym.profitPct(150.0)!!, 1e-9)
    }

    @Test
    fun nullWhenNoBuyPriceOrInvalidInputs() {
        assertNull(SymbolDef(code = "x", label = "X").profitPct(100.0))
        assertNull(SymbolDef(code = "x", label = "X", buyPrice = 0.0).profitPct(100.0))
        assertNull(SymbolDef(code = "x", label = "X", buyPrice = -5.0).profitPct(100.0))
        assertNull(SymbolDef(code = "x", label = "X", buyPrice = 100.0).profitPct(null))
        assertNull(SymbolDef(code = "x", label = "X", buyPrice = 100.0).profitPct(Double.NaN))
    }

    @Test
    fun buyPriceAndDateSurviveJsonRoundTripAndDefaultToNull() {
        val json = Json { ignoreUnknownKeys = true }
        val original = SymbolDef(
            code = "sol", label = "Solana", sourceId = "crypto",
            buyPrice = 42.5, buyDate = 1_700_000_000_000L
        )
        val restored = json.decodeFromString(SymbolDef.serializer(), json.encodeToString(SymbolDef.serializer(), original))
        assertEquals(42.5, restored.buyPrice!!, 1e-9)
        assertEquals(1_700_000_000_000L, restored.buyDate)

        // سازگاری با تنظیماتِ قدیمی که فیلدهای buyPrice/buyDate نداشتند → null
        val legacy = json.decodeFromString(SymbolDef.serializer(), """{"code":"btc","label":"BTC"}""")
        assertNull(legacy.buyPrice)
        assertNull(legacy.buyDate)
        assertTrue(legacy.profitPct(100.0) == null)
    }
}
