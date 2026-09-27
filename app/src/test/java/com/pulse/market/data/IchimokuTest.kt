package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های ایچیموکو — منطق خالص و مستقل از UI */
class IchimokuTest {

    @Test
    fun shortSeriesHasNoIndicator() {
        assertNull(Ichimoku.of(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun midPointsUseWindowHighAndLow() {
        val values = listOf(10.0, 12.0, 8.0, 14.0)
        val mids = Ichimoku.midPoints(values, 2)
        assertNull(mids[0])
        assertEquals(11.0, mids[1]!!, 0.0001)
        assertEquals(10.0, mids[2]!!, 0.0001)
        assertEquals(11.0, mids[3]!!, 0.0001)
    }

    @Test
    fun cloudIsShiftedForwardAndPositionIsDetected() {
        val rising = (1..40).map { 100.0 + it }
        val series = Ichimoku.of(rising)
        assertNotNull(series)
        val s = series!!
        assertTrue(s.displacement > 0)
        assertEquals(rising.size + s.displacement, s.spanA.size)
        assertTrue(s.lastTenkan!! > s.lastKijun!!)
        assertEquals(
            Ichimoku.CloudPosition.ABOVE,
            Ichimoku.position(rising.last(), s.currentSpanA, s.currentSpanB)
        )
        assertEquals(
            Ichimoku.CloudPosition.BELOW,
            Ichimoku.position(1.0, s.currentSpanA, s.currentSpanB)
        )
        assertEquals(
            Ichimoku.CloudPosition.UNKNOWN,
            Ichimoku.position(null, s.currentSpanA, s.currentSpanB)
        )
        assertTrue(Ichimoku.summary(rising.last(), s).contains("بالای ابر"))
    }
}
