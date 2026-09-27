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
    fun adviceAvoidsChasingSmallOrExtremePumps() {
        val small = PumpScanner.PumpCoin(
            id = "small", symbol = "sm", name = "Small", rank = 220,
            change1h = 8.0, change24h = 18.0, volume = 5e6, marketCap = 20e6
        )
        val extremeLargeCap = PumpScanner.PumpCoin(
            id = "large", symbol = "lg", name = "Large", rank = 5,
            change1h = 4.0, change24h = 30.0, volume = 2e9, marketCap = 20e9
        )
        assertEquals(PumpScanner.Recommendation.AVOID, small.advice.recommendation)
        assertEquals(PumpScanner.Recommendation.AVOID, extremeLargeCap.advice.recommendation)
        val unusualTurnover = PumpScanner.PumpCoin(
            id = "turnover", symbol = "tv", name = "Turnover", rank = 5,
            change1h = 2.0, change24h = 10.0, volume = 6e9, marketCap = 10e9
        )
        assertEquals(PumpScanner.Recommendation.AVOID, unusualTurnover.advice.recommendation)
        assertTrue(unusualTurnover.advice.reason.contains("حجم"))
        assertTrue(small.advice.reason.isNotBlank())
    }

    @Test
    fun adviceExplainsStoppedMomentumAndCautiousWatch() {
        val stopped = PumpScanner.PumpCoin(
            id = "stopped", symbol = "s", name = "Stopped", rank = 10,
            change1h = -1.0, change24h = 12.0, volume = 1e9, marketCap = 20e9
        )
        val moving = stopped.copy(id = "moving", change1h = 2.0)
        assertEquals(PumpScanner.Recommendation.WAIT, stopped.advice.recommendation)
        assertEquals(PumpScanner.Recommendation.WATCH, moving.advice.recommendation)
        assertTrue(stopped.advice.reason.contains("یک‌ساعته"))
    }

    @Test
    fun alertCandidateRespectsThresholdAndUsesHighestScore() {
        val below = PumpScanner.PumpCoin(
            id = "below", symbol = "b", name = "Below", change1h = 20.0, change24h = 7.9,
            score = 100.0
        )
        val first = PumpScanner.PumpCoin(
            id = "first", symbol = "f", name = "First", change1h = 1.0, change24h = 10.0,
            score = 12.0
        )
        val top = PumpScanner.PumpCoin(
            id = "top", symbol = "t", name = "Top", change1h = 5.0, change24h = 12.0,
            score = 22.0
        )
        assertEquals("top", PumpAlertEngine.selectCandidate(listOf(below, first, top), 8.0)?.id)
        assertEquals(null, PumpAlertEngine.selectCandidate(listOf(below), 8.0))
    }

    @Test
    fun resultsSortByThePeriodChosenByUserAndKeepMissingLast() {
        val hourLeader = PumpScanner.PumpCoin(
            id = "hour", symbol = "h", name = "Hour",
            change1h = 20.0, change24h = 3.0, change30d = 4.0
        )
        val monthLeader = PumpScanner.PumpCoin(
            id = "month", symbol = "m", name = "Month",
            change1h = 1.0, change24h = 8.0, change30d = 80.0
        )
        val missing = PumpScanner.PumpCoin(id = "missing", symbol = "x", name = "Missing")
        val coins = listOf(monthLeader, missing, hourLeader)

        assertEquals(
            listOf("hour", "month", "missing"),
            PumpScanner.sortByPeriod(coins, PumpSortPeriod.ONE_HOUR).map { it.id }
        )
        assertEquals(
            listOf("month", "hour", "missing"),
            PumpScanner.sortByPeriod(coins, PumpSortPeriod.ONE_MONTH).map { it.id }
        )
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

        val zeroThreshold = scan.copy(
            minChange = 0.0,
            coins = scan.coins + PumpScanner.PumpCoin(id = "missing", symbol = "m", name = "Missing")
        )
        assertEquals(listOf("a", "b"), zeroThreshold.matches.map { it.id })
    }
    @Test
    fun downsampleKeepsBoundsAndLimit() {
        val raw = (1..500).map { it.toDouble() }
        val out = PumpScanner.downsample(raw, PumpScanner.SPARK_POINTS)
        assertEquals(PumpScanner.SPARK_POINTS, out.size)
        assertEquals(1.0, out.first(), 0.0001)
        assertEquals(500.0, out.last(), 0.0001)
        assertEquals(raw, PumpScanner.downsample(raw, 1000))
    }

    @Test
    fun stageFollowsMomentum() {
        val cooling = PumpScanner.PumpCoin(id = "a", symbol = "a", name = "A", change1h = -1.2, change24h = 14.0)
        val saturated = PumpScanner.PumpCoin(id = "b", symbol = "b", name = "B", change1h = 2.0, change24h = 40.0)
        val accelerating = PumpScanner.PumpCoin(id = "c", symbol = "c", name = "C", change1h = 3.0, change24h = 12.0)
        assertEquals(PumpScanner.Stage.COOLING, cooling.stage)
        assertEquals(PumpScanner.Stage.SATURATED, saturated.stage)
        assertEquals(PumpScanner.Stage.ACCELERATING, accelerating.stage)
    }

    @Test
    fun scorePartsSumToScoreAndRangePositionIsBounded() {
        val coin = PumpScanner.PumpCoin(
            id = "d", symbol = "d", name = "D",
            price = 8.0, low24h = 4.0, high24h = 12.0,
            change1h = 2.0, change24h = 10.0,
            volume = 50.0, marketCap = 100.0
        )
        val parts = coin.scoreParts
        assertEquals(
            PumpScanner.score(coin.change1h, coin.change24h, coin.volume, coin.marketCap),
            parts.total,
            0.0001
        )
        assertEquals(0.5, coin.rangePosition24h ?: -1.0, 0.0001)
        assertTrue(coin.thinMarket)
    }

    @Test
    fun cacheKeepsChartsOnlyForTopCoins() {
        val spark = listOf(1.0, 2.0, 3.0)
        val coins = (1..70).map {
            PumpScanner.PumpCoin(id = "c$it", symbol = "c$it", name = "C$it", spark = spark)
        }
        val trimmed = PumpScanner.trimSparkForCache(
            PumpScanner.PumpScan(at = 1L, coins = coins)
        )
        assertEquals(spark, trimmed.coins.first().spark)
        assertEquals(spark, trimmed.coins[PumpScanner.SPARK_CACHED_COINS - 1].spark)
        assertTrue(trimmed.coins[PumpScanner.SPARK_CACHED_COINS].spark.isEmpty())
        assertTrue(trimmed.coins.last().spark.isEmpty())
    }
}
