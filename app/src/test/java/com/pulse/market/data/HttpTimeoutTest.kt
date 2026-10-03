package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class HttpTimeoutTest {

    @Test
    fun longCallTimeoutExtendsReadWriteAndWholeCallTogether() {
        assertNull(Http.boundedCallTimeoutSeconds(null))
        assertEquals(3, Http.boundedCallTimeoutSeconds(1))
        assertEquals(300, Http.boundedCallTimeoutSeconds(300))
        assertEquals(300, Http.boundedCallTimeoutSeconds(900))

        val regular = Http.clientForTimeout(null)
        assertEquals(15_000, regular.readTimeoutMillis)
        assertEquals(10_000, regular.writeTimeoutMillis)
        assertEquals(25_000, regular.callTimeoutMillis)

        val long = Http.clientForTimeout(300)
        assertEquals(300_000, long.readTimeoutMillis)
        assertEquals(300_000, long.writeTimeoutMillis)
        assertEquals(300_000, long.callTimeoutMillis)
    }

    @Test
    fun retryAfterSupportsSecondsAndRfcDate() {
        assertEquals(5_000L, Http.retryAfterMillis("5", nowMillis = 1_000L))
        assertNull(Http.retryAfterMillis("-1", nowMillis = 1_000L))
        assertNull(Http.retryAfterMillis("not-a-date", nowMillis = 1_000L))

        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val target = format.parse("Wed, 21 Oct 2015 07:28:00 GMT")!!.time
        assertEquals(
            10_000L,
            Http.retryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT", target - 10_000L)
        )
    }
}
