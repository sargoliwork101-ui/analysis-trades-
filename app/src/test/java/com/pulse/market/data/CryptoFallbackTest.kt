package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تستِ منبعِ پشتیبانِ کریپتو (Coinpaprika): پارسِ پاسخ و تطبیقِ شناسه‌ی CoinGecko
 * با شناسه/slug/نمادِ paprika — بدونِ نیاز به شبکه.
 */
class CryptoFallbackTest {

    private val sample = """
        [
          {"id":"btc-bitcoin","name":"Bitcoin","symbol":"BTC","rank":1,
           "circulating_supply":19000000,"total_supply":21000000,
           "quotes":{"USD":{"price":65000.5,"volume_24h":12345.0,"market_cap":1200000000.0,
             "percent_change_1h":0.5,"percent_change_24h":2.5,"percent_change_7d":-3.0,
             "percent_change_30d":10.0,"ath_price":69000.0,"percent_from_price_ath":-5.8}}},
          {"id":"eth-ethereum","name":"Ethereum","symbol":"ETH","rank":2,
           "quotes":{"USD":{"price":3200.0,"percent_change_24h":-1.2,"volume_24h":9999.0,"market_cap":400000000.0}}},
          {"id":"sol-solana","name":"Solana","symbol":"SOL","rank":5,
           "quotes":{"USD":{"price":150.0,"percent_change_24h":4.0}}},
          {"id":"xrp-xrp","name":"XRP","symbol":"XRP","rank":6,
           "quotes":{"USD":{"price":0.55,"percent_change_24h":1.0}}},
          {"id":"bnb-binance-coin","name":"BNB","symbol":"BNB","rank":4,
           "quotes":{"USD":{"price":580.0,"percent_change_24h":0.8}}}
        ]
    """.trimIndent()

    @Test
    fun parsesFieldsAndSlug() {
        val list = CryptoFallback.parse(sample)
        assertEquals(3, list.size)
        val btc = list.first { it.id == "btc-bitcoin" }
        assertEquals("bitcoin", btc.slug)       // بخشِ بعد از خط تیره == شناسه‌ی CoinGecko
        assertEquals("BTC", btc.symbol)
        assertEquals(65000.5, btc.price!!, 0.0001)
        assertEquals(2.5, btc.change24h!!, 0.0001)
        assertEquals(1200000000.0, btc.marketCap!!, 0.1)
        assertEquals(1, btc.rank)
    }

    @Test
    fun matchesCoinGeckoIdBySlug() {
        val list = CryptoFallback.parse(sample)
        // شناسه‌ی CoinGecko «solana» باید به «sol-solana» (از راهِ slug) وصل شود.
        val resolved = CryptoFallback.resolveFrom(list, listOf("solana", "bitcoin", "ethereum"))
        assertEquals("sol-solana", resolved["solana"]?.id)
        assertEquals("btc-bitcoin", resolved["bitcoin"]?.id)
        assertEquals("eth-ethereum", resolved["ethereum"]?.id)
    }

    @Test
    fun matchesByPaprikaIdAndSymbol() {
        val list = CryptoFallback.parse(sample)
        val byId = CryptoFallback.resolveFrom(list, listOf("btc-bitcoin"))
        assertEquals("btc-bitcoin", byId["btc-bitcoin"]?.id)
        val bySymbol = CryptoFallback.resolveFrom(list, listOf("eth"))
        assertEquals("eth-ethereum", bySymbol["eth"]?.id)
    }

    @Test
    fun mapsKnownCoinGeckoIdsThatDifferFromPaprika() {
        val list = CryptoFallback.parse(sample)
        // شناسه‌ی CoinGecko «ripple»/«binancecoin» با هیچ id/slug/نمادِ paprika مستقیم یکی
        // نیست؛ نگاشتِ نام باید آن‌ها را به XRP/BNB وصل کند.
        val resolved = CryptoFallback.resolveFrom(list, listOf("ripple", "binancecoin"))
        assertEquals("xrp-xrp", resolved["ripple"]?.id)
        assertEquals("bnb-binance-coin", resolved["binancecoin"]?.id)
    }

    @Test
    fun unknownIdReturnsNothing() {
        val list = CryptoFallback.parse(sample)
        val resolved = CryptoFallback.resolveFrom(list, listOf("no-such-coin"))
        assertTrue(resolved.isEmpty())
        assertNull(resolved["no-such-coin"])
    }
}
