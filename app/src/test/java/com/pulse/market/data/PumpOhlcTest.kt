package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** خواندن پاسخ OHLC کوین‌گکو */
class PumpOhlcTest {

    @Test
    fun validRowsBecomeCandles() {
        val body = """
            [[1700000000000,10.0,12.0,9.0,11.0],[1700003600000,11.0,11.5,10.5,10.8]]
        """.trimIndent()
        val candles = PumpOhlc.parse(body)
        assertEquals(2, candles.size)
        assertTrue(candles[0].bullish)
        assertTrue(!candles[1].bullish)
        assertEquals(12.0, candles[0].high, 0.0001)
        assertEquals(9.0, candles[0].low, 0.0001)
        assertEquals(listOf(11.0, 10.8), PumpOhlc.closes(candles))
    }

    @Test
    fun brokenRowsAreDropped() {
        val body = """[[0,1,2,3,4],[1700000000000,-1,2,3,4],[1700000000000,1,2],"x"]"""
        assertTrue(PumpOhlc.parse(body).isEmpty())
        assertTrue(PumpOhlc.parse("not json").isEmpty())
    }

    @Test
    fun highAndLowAlwaysContainBodies() {
        val candles = PumpOhlc.parse("""[[1700000000000,10.0,9.0,11.0,12.0]]""")
        assertEquals(1, candles.size)
        assertEquals(12.0, candles[0].high, 0.0001)
        assertEquals(10.0, candles[0].low, 0.0001)
    }

    @Test
    fun pricePointsAreParsed() {
        val body = """{"prices":[[1700000000000,10.0],[1700003600000,12.0],[0,-1]]}"""
        val points = PumpOhlc.parsePricePoints(body)
        assertEquals(2, points.size)
        assertEquals(10.0, points[0].second, 1e-9)
        assertEquals(1700003600000L, points[1].first)
    }

    @Test
    fun synthesizeCandlesBucketsPrices() {
        val points = listOf(0L to 10.0, 1L to 12.0, 2L to 8.0, 3L to 11.0)
        val candles = PumpOhlc.synthesizeCandles(points, 2)
        assertEquals(2, candles.size)
        // سطل اول: open=10 close=12 high=12 low=10
        assertEquals(10.0, candles[0].open, 1e-9)
        assertEquals(12.0, candles[0].close, 1e-9)
        assertEquals(12.0, candles[0].high, 1e-9)
        assertEquals(10.0, candles[0].low, 1e-9)
        // سطل دوم: open=8 close=11 high=11 low=8
        assertEquals(8.0, candles[1].open, 1e-9)
        assertEquals(11.0, candles[1].close, 1e-9)
    }

    @Test
    fun candlesFromValuesMakesOneCandlePerValueWhenFew() {
        val candles = PumpOhlc.candlesFromValues(listOf(10.0, 12.0, 8.0, 11.0))
        assertEquals(4, candles.size)
        assertEquals(10.0, candles[0].open, 1e-9)
        assertEquals(10.0, candles[0].close, 1e-9)
        assertTrue(PumpOhlc.candlesFromValues(listOf(5.0)).isEmpty())
    }
}
