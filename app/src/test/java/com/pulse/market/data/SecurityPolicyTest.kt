package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
