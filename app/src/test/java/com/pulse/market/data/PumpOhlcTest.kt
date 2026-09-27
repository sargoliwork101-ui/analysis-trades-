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
}
