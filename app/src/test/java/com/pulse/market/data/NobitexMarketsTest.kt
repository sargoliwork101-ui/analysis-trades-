package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست خواندن پاسخ عمومی نوبیتکس */
class NobitexMarketsTest {

    @Test
    fun symbolIsNormalisedForTheApi() {
        assertEquals("btc", NobitexMarkets.normalizeSymbol(" BTC "))
        assertEquals("shib", NobitexMarkets.normalizeSymbol("Shib!"))
        assertEquals("", NobitexMarkets.normalizeSymbol("!!!"))
    }

    @Test
    fun availableMarketsAreDetected() {
        val body = """
            {"status":"ok","stats":{
              "btc-rls":{"latest":"60000000000","bestSell":"60100000000"},
              "btc-usdt":{"latest":"65000"}
            }}
        """.trimIndent()
        val result = NobitexMarkets.parse(body, "btc")
        assertEquals(NobitexMarkets.State.AVAILABLE, result.state)
        assertTrue(result.pairs.contains("تومان"))
        assertTrue(result.pairs.contains("تتر"))
        assertTrue(result.label.contains("معامله می‌شود"))
    }

    @Test
    fun missingOrPricelessMarketsCountAsUnavailable() {
        assertEquals(
            NobitexMarkets.State.UNAVAILABLE,
            NobitexMarkets.parse("""{"status":"ok","stats":{}}""", "pepe").state
        )
        assertEquals(
            NobitexMarkets.State.UNAVAILABLE,
            NobitexMarkets.parse(
                """{"status":"ok","stats":{"pepe-rls":{"latest":"0"}}}""",
                "pepe"
            ).state
        )
        assertEquals(
            NobitexMarkets.State.UNKNOWN,
            NobitexMarkets.parse("""{"status":"failed"}""", "btc").state
        )
    }
}
