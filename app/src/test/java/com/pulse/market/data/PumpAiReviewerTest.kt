package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpAiReviewerTest {

    @Test
    fun endpointAcceptsBaseV1AndFullUrls() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com/v1/")
        )
        assertEquals(
            "https://example.com/custom/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com/custom/chat/completions")
        )
    }

    @Test
    fun endpointKeepsGoogleCompatiblePath() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://generativelanguage.googleapis.com/v1beta/openai")
        )
        assertEquals(
            "https://api.anthropic.com/v1/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://api.anthropic.com/v1")
        )
    }

    @Test
    fun everyPresetBuildsReadyConfigAndValidEndpoint() {
        for (preset in PumpAiConfig.PRESETS) {
            val config = PumpAiConfig(
                enabled = true,
                endpoint = preset.endpoint,
                model = preset.model
            )
            assertTrue(preset.id, config.isReady)
            assertTrue(preset.id, config.matches(preset))
            assertTrue(
                preset.id,
                PumpAiReviewer.chatCompletionsEndpoint(preset.endpoint).endsWith("/chat/completions")
            )
        }
    }

    @Test
    fun reviewParsesCautiousResultAndOnlyValidNewsLinks() {
        val review = PumpAiReviewer.parseReview(
            """
            {
              "verdict":"محتاط‌تر",
              "recommendation":"فعلاً وارد نشو؛ قیمت را تعقیب نکن",
              "reason":"حجم غیرعادی است و خبر هنوز تأیید گسترده ندارد.",
              "confidence":84,
              "news":[
                {"title":"خبر معتبر","url":"https://news.example/item","relation":"افزایش حجم","source":"Example"},
                {"title":"لینک خطرناک","url":"file:///secret","relation":"نامعتبر"}
              ]
            }
            """.trimIndent()
        )
        assertEquals("فعلاً وارد نشو؛ قیمت را تعقیب نکن", review.recommendation)
        assertEquals(84, review.confidence)
        assertEquals(1, review.news.size)
        assertEquals("https://news.example/item", review.news.single().url)
        assertEquals("news.example", review.news.single().host)
    }

    @Test
    fun directBuyAdviceIsRejected() {
        val review = PumpAiReviewer.parseReview(
            """{"verdict":"همسو","recommendation":"فعلاً فقط زیر نظر بگیر","reason":"حتماً بخر؛ سود تضمینی است","confidence":99,"news":[]}"""
        )
        assertEquals("صبر کن؛ ورود عجولانه نکن", review.recommendation)
        assertTrue(review.reason.contains("برای ایمنی رد شد"))
    }

    @Test
    fun malformedResponseFallsBackToWait() {
        val review = PumpAiReviewer.parseReview("پاسخ آزاد و بدون ساختار")
        assertEquals("صبر کن؛ ورود عجولانه نکن", review.recommendation)
        assertEquals("نامطمئن", review.verdict)
        assertNull(review.confidence)
    }

    @Test
    fun httpErrorTextExplainsCauseAndKeepsServerDetail() {
        val unauthorized = PumpAiReviewer.httpErrorText(
            Http.HttpException(401, "HTTP 401 — invalid_api_key")
        )
        assertTrue(unauthorized.contains("۴۰۱"))
        assertTrue(unauthorized.contains("invalid_api_key"))

        val notFound = PumpAiReviewer.httpErrorText(Http.HttpException(404, "HTTP 404"))
        assertTrue(notFound.contains("۴۰۴"))
    }

    @Test
    fun networkErrorTextIsCategorisedAndNeverLeaksRawUrl() {
        assertTrue(
            PumpAiReviewer.networkErrorText(java.net.SocketTimeoutException("timeout"))
                .contains("زمان پاسخ")
        )
        assertTrue(
            PumpAiReviewer.networkErrorText(java.net.UnknownHostException("Unable to resolve host"))
                .contains("DNS")
        )
        val leaky = PumpAiReviewer.networkErrorText(
            IllegalStateException("failed to connect to https://secret.example/v1?key=abc")
        )
        assertTrue(!leaky.contains("secret.example"))
    }

    @Test
    fun googleGeoBlockAndKeyErrorsBecomeReadableMessages() {
        val geo = PumpAiReviewer.httpErrorText(
            Http.HttpException(
                400,
                """HTTP 400 — {"error":{"code":400,"message":"User location is not supported for the API use.","status":"FAILED_PRECONDITION"}}"""
            )
        )
        assertTrue(geo.contains("محدودیت جغرافیایی"))

        val badKey = PumpAiReviewer.httpErrorText(
            Http.HttpException(
                400,
                """HTTP 400 — {"error":{"message":"API key not valid. Please pass a valid API key."}}"""
            )
        )
        assertTrue(badKey.contains("کلید API معتبر نیست"))

        assertEquals(
            "API key not valid.",
            PumpAiReviewer.serviceMessage("""{"error":{"message":"API key not valid."}}""")
        )
    }

    @Test
    fun googleAiStudioKeysUseTheNativeGeminiPath() {
        assertTrue(PumpAiReviewer.isGeminiNative("https://generativelanguage.googleapis.com/v1beta"))
        assertTrue(
            !PumpAiReviewer.isGeminiNative("https://generativelanguage.googleapis.com/v1beta/openai")
        )
        assertTrue(!PumpAiReviewer.isGeminiNative("https://api.openai.com/v1"))

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            PumpAiReviewer.geminiEndpoint(
                "https://generativelanguage.googleapis.com/v1beta/",
                "models/gemini-flash-latest"
            )
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            PumpAiReviewer.geminiEndpoint(
                "https://generativelanguage.googleapis.com",
                "gemini-2.5-flash"
            )
        )
    }

    @Test
    fun geminiRequestAndResponseShapesAreHandled() {
        val body = PumpAiReviewer.geminiBody("system text", "user text", 32).toString()
        assertTrue(body.contains("systemInstruction"))
        assertTrue(body.contains("\"maxOutputTokens\":32"))
        assertTrue(body.contains("user text"))

        val text = PumpAiReviewer.extractGeminiContent(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]}}]}"""
        )
        assertEquals("OK", text)
        assertNull(PumpAiReviewer.extractGeminiContent("""{"candidates":[]}"""))
    }

    @Test
    fun thinkingPartsAreIgnoredAndEmptyAnswersAreExplained() {
        assertEquals(
            "پاسخ نهایی",
            PumpAiReviewer.extractGeminiContent(
                """{"candidates":[{"content":{"parts":[{"thought":true,"text":"فکر"},{"text":"پاسخ نهایی"}]}}]}"""
            )
        )

        val maxTokens = PumpAiReviewer.emptyContentReason(
            """{"candidates":[{"content":{"parts":[]},"finishReason":"MAX_TOKENS"}]}"""
        )
        assertTrue(maxTokens.contains("سقف طول پاسخ"))

        val blocked = PumpAiReviewer.emptyContentReason(
            """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        )
        assertTrue(blocked.contains("فیلتر ایمنی"))

        val openAiLength = PumpAiReviewer.emptyContentReason(
            """{"choices":[{"finish_reason":"length","message":{"content":""}}]}"""
        )
        assertTrue(openAiLength.contains("سقف طول پاسخ"))
    }
}
