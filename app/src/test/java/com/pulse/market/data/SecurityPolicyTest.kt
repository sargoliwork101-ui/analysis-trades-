package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.parser.Parser

class SecurityPolicyTest {

    @Test
    fun sensitiveErrorsAreRedactedIncludingBearerAndJsonValues() {
        val input = "Authorization: Bearer abcdef123456, api_key=secret789 " +
                "password: pass123 https://example.com/path?token=leak"
        val safe = SensitiveText.redact(input, 500)
        assertFalse(safe.contains("abcdef123456"))
        assertFalse(safe.contains("secret789"))
        assertFalse(safe.contains("pass123"))
        assertFalse(safe.contains("example.com"))
        assertTrue(safe.contains("حذف شد"))
    }

    @Test
    fun cleartextDropsSensitiveHeadersButKeepsPublicOnes() {
        val headers = mapOf(
            "Authorization" to "Bearer secret",
            "X-API-Key" to "secret",
            "Cookie" to "session=secret",
            "Accept-Language" to "fa"
        )
        val safe = Http.safeHeadersForUrl("http://example.com/data", headers)
        assertEquals(mapOf("Accept-Language" to "fa"), safe)
        assertEquals(headers, Http.safeHeadersForUrl("https://example.com/data", headers))
        assertTrue(Http.isSensitiveQueryName("access_token"))
        assertTrue(Http.isSensitiveQueryName("X-API-Key"))
        assertFalse(Http.isSensitiveQueryName("vs_currency"))
    }

    @Test
    fun wallClockRollbackDoesNotLockCooldownOrMarkFutureFresh() {
        assertTrue(TimePolicy.cooldownElapsed(now = 1_000L, lastAt = 2_000L, cooldownMs = 60_000L))
        assertFalse(TimePolicy.isFresh(now = 1_000L, timestamp = 2_000L, maxAgeMs = 60_000L))
        assertFalse(TimePolicy.cooldownElapsed(now = 61_999L, lastAt = 2_000L, cooldownMs = 60_000L))
        assertTrue(TimePolicy.cooldownElapsed(now = 62_000L, lastAt = 2_000L, cooldownMs = 60_000L))
    }

    @Test
    fun customSourceUrlRequiresARealHostAndAllowsDocumentedPlaceholders() {
        assertTrue(SourceUrlPolicy.isValid("https://api.example.com/price/{symbol}"))
        assertTrue(SourceUrlPolicy.isValid("HTTP://example.com/all?ids={symbols}"))
        assertFalse(SourceUrlPolicy.isValid("https://"))
        assertFalse(SourceUrlPolicy.isValid("https:///missing-host/{symbol}"))
        assertFalse(SourceUrlPolicy.isValid("https://user:pass@example.com/price/{symbol}"))
        assertFalse(SourceUrlPolicy.isValid("https://example.com/{unknown}"))
        assertFalse(SourceUrlPolicy.isValid("javascript:alert(1)"))
    }

    @Test
    fun htmlStringParserKeepsCustomCssSelectorExtraction() {
        val doc = Parser.parse(
            """<main><div class="quote" data-kind="last"><span class="price">۱۲۳٫۴۵</span></div></main>""",
            "https://example.com/market"
        )
        val price = firstMatchingHtmlElement(doc, "div.quote[data-kind=last] > span.price")
        assertEquals("۱۲۳٫۴۵", price?.text())
    }

    @Test
    fun aiEndpointRejectsCredentialsQueryAndFragments() {
        assertTrue(PumpAiConfig.isValidEndpoint("https://api.example.com/v1"))
        assertFalse(PumpAiConfig.isValidEndpoint("https://user:pass@api.example.com/v1"))
        assertFalse(PumpAiConfig.isValidEndpoint("https://api.example.com/v1?key=secret"))
        assertFalse(PumpAiConfig.isValidEndpoint("file:///tmp/api"))
        assertFalse(
            PumpAiConfig(
                enabled = true,
                endpoint = "http://api.example.com/v1",
                model = "model",
                apiKey = "secret"
            ).isReady
        )
    }

    @Test
    fun remoteCoinTextDropsBidiOverridesAndControls() {
        assertEquals("ABCD", PumpScanner.safeRemoteText("A\u202EBC\u0000D", 20))
    }

    @Test
    fun tseUrlParserAcceptsLegacyInsCodeQuery() {
        val parsed = TseUrlParser.parse(
            "https://www.tsetmc.com/Loader.aspx?ParTree=151311&i=46348559193224090"
        )
        assertEquals("46348559193224090", parsed.insCode)
        val named = TseUrlParser.parse(
            "https://example.com/page?inscode=46348559193224090"
        )
        assertEquals("46348559193224090", named.insCode)
    }

    @Test
    fun tseSymbolMatchingHandlesArabicLettersAndHalfSpaces() {
        assertEquals("فولاد", TseService.normalizeSymbol(" فـولاد "))
        assertEquals("کیمیای", TseService.normalizeSymbol("كيميا\u200Cی"))
    }

    @Test
    fun tseBulkResponseMapsCompressedMarketWatchFields() {
        val body = """{"closingPrice":[{"insCode":"12345678901234567","lva":"فولاد","lvc":"فولاد مبارکه","pcl":1020,"pdv":1030,"py":1000,"qtj":250000}]}"""
        val byCode = TseService.parseMarketWatch(body, listOf("فولاد", "12345678901234567"))!!
        assertEquals(1020.0, byCode.getValue("فولاد").closePrice!!, 0.0)
        assertEquals(2.0, byCode.getValue("فولاد").changePct!!, 0.0001)
        assertEquals(250000.0, byCode.getValue("12345678901234567").volume!!, 0.0)
    }
}
