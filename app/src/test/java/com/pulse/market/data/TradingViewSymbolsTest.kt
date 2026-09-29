package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingViewSymbolsTest {

    @Test
    fun `preferred exchange wins`() {
        val body = """
            [
              {"symbol":"<em>SOL</em>USDT","exchange":"MEXC","type":"spot"},
              {"symbol":"SOLUSDT","exchange":"BINANCE","type":"spot"}
            ]
        """.trimIndent()
        val out = TradingViewSymbols.parse(body, "SOL")
        assertEquals(TradingViewSymbols.Outcome.Found("BINANCE:SOLUSDT"), out)
    }

    @Test
    fun `falls back to first exchange when no preferred`() {
        val body = """[{"symbol":"FOOUSDT","exchange":"GATEIO","type":"spot"}]"""
        val out = TradingViewSymbols.parse(body, "FOO")
        assertEquals(TradingViewSymbols.Outcome.Found("GATEIO:FOOUSDT"), out)
    }

    @Test
    fun `empty results means not found`() {
        assertEquals(TradingViewSymbols.Outcome.NotFound, TradingViewSymbols.parse("[]", "NOPE"))
    }

    @Test
    fun `no matching pair means not found`() {
        val body = """[{"symbol":"BTCUSDT","exchange":"BINANCE","type":"spot"}]"""
        assertEquals(TradingViewSymbols.Outcome.NotFound, TradingViewSymbols.parse(body, "ETH"))
    }

    @Test
    fun `garbage body is unknown`() {
        assertEquals(TradingViewSymbols.Outcome.Unknown, TradingViewSymbols.parse("not json", "BTC"))
    }

    @Test
    fun `symbols wrapper object is accepted`() {
        val body = """{"symbols":[{"symbol":"ADAUSDT","exchange":"BINANCE"}]}"""
        assertEquals(TradingViewSymbols.Outcome.Found("BINANCE:ADAUSDT"), TradingViewSymbols.parse(body, "ADA"))
    }

    @Test
    fun `pair present but exchange blank still found`() {
        val body = """[{"symbol":"XYZUSDT","exchange":""}]"""
        assertEquals(TradingViewSymbols.Outcome.Found("XYZUSDT"), TradingViewSymbols.parse(body, "XYZ"))
    }

    @Test
    fun `strip tags removes html`() {
        assertEquals("SOLUSDT", TradingViewSymbols.stripTags("<em>SOL</em>USDT"))
    }

    @Test
    fun `clean base uppercases and strips`() {
        assertTrue(TradingViewSymbols.cleanBase(" sol! ") == "SOL")
    }
}
