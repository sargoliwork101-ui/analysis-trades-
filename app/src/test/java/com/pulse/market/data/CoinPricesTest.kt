package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** خواندن قیمت لحظه‌ای چند کوین دلخواه */
class CoinPricesTest {

    @Test
    fun idsAreSanitizedAndDeduplicated() {
        val ids = CoinPrices.sanitizeIds(listOf(" Solana ", "solana", "bit!coin", "", "the-open-network"))
        assertEquals(listOf("solana", "bitcoin", "the-open-network"), ids)
    }

    @Test
    fun pricesAreParsedAndInvalidOnesDropped() {
        val body = """{"solana":{"usd":150.25},"pepe":{"usd":0},"ton":{"eur":5.0}}"""
        val prices = CoinPrices.parse(body)
        assertEquals(1, prices.size)
        assertEquals(150.25, prices["solana"]!!, 0.0001)
        assertTrue(CoinPrices.parse("nope").isEmpty())
    }
}
