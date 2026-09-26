package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های امتیاز و سطح ریسک اسکنر پامپ — منطق خالص، بدون شبکه */
class PumpScannerTest {

    @Test
    fun growthAndVolumeRaiseTheScore() {
        val quiet = PumpScanner.score(change1h = 0.0, change24h = 1.0, volume = 1e6, marketCap = 1e9)
        val pumping = PumpScanner.score(change1h = 5.0, change24h = 30.0, volume = 5e8, marketCap = 1e9)
        assertTrue("کوین در حال پامپ باید امتیاز بالاتری بگیرد", pumping > quiet)
    }

    @Test
    fun turnoverIsCappedAtOne() {
        // حجمی چند برابر ارزش بازار، بیشتر از سقف شمرده نمی‌شود
        val capped = PumpScanner.score(null, null, volume = 10_000.0, marketCap = 1_000.0)
        assertEquals(50.0, capped, 1e-9)
    }

    @Test
    fun missingDataIsSafe() {
        assertEquals(0.0, PumpScanner.score(null, null, null, null), 1e-9)
        // ارزش بازار صفر نباید تقسیم بر صفر بسازد
        assertEquals(0.0, PumpScanner.score(null, null, 100.0, 0.0), 1e-9)
        assertEquals(0.0, PumpScanner.score(Double.NaN, Double.POSITIVE_INFINITY, 100.0, 0.0), 1e-9)
    }

    @Test
    fun riskFollowsRankAndMarketCap() {
        val big = PumpScanner.PumpCoin(id = "bitcoin", symbol = "btc", name = "Bitcoin", rank = 1, marketCap = 1e12)
        val mid = PumpScanner.PumpCoin(id = "x", symbol = "x", name = "X", rank = 60, marketCap = 1e9)
        val small = PumpScanner.PumpCoin(id = "y", symbol = "y", name = "Y", rank = 300, marketCap = 1e7)
        assertEquals(PumpScanner.Risk.LOW, big.risk)
        assertEquals(PumpScanner.Risk.MEDIUM, mid.risk)
        assertEquals(PumpScanner.Risk.HIGH, small.risk)
    }

    @Test
    fun coinBecomesWidgetSymbolWithDollarUnit() {
        val coin = PumpScanner.PumpCoin(id = "solana", symbol = "sol", name = "Solana")
        val sym = coin.toSymbolDef()
        assertEquals("solana", sym.code)
        assertEquals("Solana (SOL)", sym.label)
        assertEquals(PumpScanner.CRYPTO_SOURCE_ID, sym.sourceId)
        assertEquals("$", sym.unit)
    }

    @Test
    fun matchesRespectThreshold() {
        val scan = PumpScanner.PumpScan(
            universe = 100,
            minChange = 8.0,
            coins = listOf(
                PumpScanner.PumpCoin(id = "a", symbol = "a", name = "A", change24h = 12.0),
                PumpScanner.PumpCoin(id = "b", symbol = "b", name = "B", change24h = 3.0)
            )
        )
        assertEquals(listOf("a"), scan.matches.map { it.id })
    }
}
