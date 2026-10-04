package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun genericCompletionUsesConfiguredModelWithoutWebPlugin() {
        val config = PumpAiConfig(
            enabled = true,
            endpoint = "https://openrouter.ai/api/v1",
            model = "test/model",
            providerSearch = true
        )
        val route = PumpAiReviewer.completionRoutes(config, "system", "user", 900).single()
        val body = route.payload.toString()

        assertTrue(route.endpoint.endsWith("/chat/completions"))
        assertTrue(body.contains("test/model"))
        assertTrue(body.contains("system"))
        assertFalse(body.contains("plugins"))
        assertFalse(body.contains("web_search_options"))
    }

    @Test
    fun heavyCompletionCanExtendEveryRouteToFiveMinutes() {
        assertEquals(300, PumpAiReviewer.completionTimeout(60, 300))
        assertEquals(180, PumpAiReviewer.completionTimeout(180, 30))
        assertEquals(300, PumpAiReviewer.completionTimeout(60, 900))
        assertEquals(60, PumpAiReviewer.completionTimeout(60, null))
    }

    @Test
    fun endpointKeepsGoogleCompatiblePath() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://generativelanguage.googleapis.com/v1beta/openai")
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            PumpAiReviewer.anthropicEndpoint("https://api.anthropic.com/v1")
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
            if (preset.id == "claude") {
                assertTrue(
                    preset.id,
                    PumpAiReviewer.anthropicEndpoint(preset.endpoint).endsWith("/v1/messages")
                )
            } else {
                assertTrue(
                    preset.id,
                    PumpAiReviewer.chatCompletionsEndpoint(preset.endpoint).endsWith("/chat/completions")
                )
            }
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
    fun officialAnthropicUsesMessagesProtocolAndHeaders() {
        val config = PumpAiConfig(
            enabled = true,
            endpoint = "https://api.anthropic.com/v1",
            model = "claude-sonnet-4-5",
            apiKey = "secret-test-key",
            providerSearch = false
        )
        val coin = PumpScanner.PumpCoin(id = "sol", symbol = "sol", name = "Solana")
        val route = PumpAiReviewer.reviewRoutes(config, coin).single()

        assertTrue(route.nativeAnthropic)
        assertEquals("https://api.anthropic.com/v1/messages", route.endpoint)
        assertTrue(route.payload.toString().contains("\"system\""))
        val request = PumpAiReviewer.buildRequest(config, route.endpoint, route.payload)
        assertEquals("secret-test-key", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        assertNull(request.header("Authorization"))
        assertEquals(
            "پاسخ کلاد",
            PumpAiReviewer.extractAnthropicContent(
                """{"type":"message","content":[{"type":"thinking","thinking":"x"},{"type":"text","text":"پاسخ کلاد"}],"stop_reason":"end_turn"}"""
            )
        )
    }

    @Test
    fun newsCompletionUsesStructuredOutputWithPlainFallback() {
        val schema = NewsAiSummarizer.outputSchema()
        val gemini = PumpAiReviewer.completionRoutes(
            PumpAiConfig(
                enabled = true,
                endpoint = "https://generativelanguage.googleapis.com/v1beta",
                model = "gemini-2.5-flash"
            ),
            "system", "user", 4096, schema
        )
        assertTrue(gemini.first().payload.toString().contains("responseSchema"))
        assertTrue(gemini.first().payload.toString().contains("persianTitle"))
        assertTrue(gemini.any { it.label.contains("متن ساده") })

        val openAi = PumpAiReviewer.completionRoutes(
            PumpAiConfig(
                enabled = true,
                endpoint = "https://api.openai.com/v1",
                model = "gpt-4o-mini"
            ),
            "system", "user", 4096, schema
        )
        assertTrue(openAi.first().payload.toString().contains("response_format"))
        assertTrue(!openAi.last().payload.toString().contains("response_format"))
    }

    @Test
    fun transientFailuresHaveBoundedRetryAndQualityLabels() {
        assertTrue(PumpAiReviewer.isRetryableHttp(Http.HttpException(503, "overloaded")))
        assertTrue(PumpAiReviewer.isRetryableHttp(Http.HttpException(429, "slow_down", 2_000L)))
        assertFalse(PumpAiReviewer.isRetryableHttp(Http.HttpException(429, "insufficient_quota")))
        assertFalse(PumpAiReviewer.isRetryableHttp(Http.HttpException(429, "slow", 120_000L)))
        assertEquals(2_000L, PumpAiReviewer.retryDelayMillis(0, 2_000L, 200L))
        assertEquals(750L, PumpAiReviewer.retryDelayMillis(0, null, 0L))
        assertEquals("عالی", PumpAiReviewer.connectionQuality(1_000L))
        assertEquals("کند", PumpAiReviewer.connectionQuality(30_000L))
        assertEquals(45, PumpAiReviewer.CONNECTION_TEST_TIMEOUT_SECONDS)
    }

    @Test
    fun connectionTargetShowsTheExactDirectProtocolDestination() {
        val gemini = PumpAiReviewer.connectionTarget(
            PumpAiConfig(
                endpoint = "https://generativelanguage.googleapis.com/v1beta",
                model = "gemini-2.0-flash"
            )
        )!!
        assertEquals("Gemini بومی", gemini.route)
        assertEquals("generativelanguage.googleapis.com", gemini.host)
        assertTrue(gemini.path.endsWith("/models/gemini-2.0-flash:generateContent"))

        val claude = PumpAiReviewer.connectionTarget(
            PumpAiConfig(
                endpoint = "https://api.anthropic.com/v1",
                model = "claude-sonnet-4-5"
            )
        )!!
        assertEquals("Claude بومی", claude.route)
        assertEquals("https://api.anthropic.com/v1/messages", claude.display)
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

    @Test
    fun geminiUsesStructuredOutputAndRetriesMalformedFunctionCalls() {
        val structured = PumpAiReviewer.geminiBody("s", "u", 4096, jsonOutput = true).toString()
        assertTrue(structured.contains("responseMimeType"))
        assertTrue(structured.contains("responseSchema"))
        assertTrue(structured.contains("recommendation"))

        val plain = PumpAiReviewer.geminiBody("s", "u", 512).toString()
        assertTrue(!plain.contains("responseSchema"))

        val malformed = """{"candidates":[{"finishReason":"MALFORMED_FUNCTION_CALL","content":{"parts":[]}}]}"""
        assertTrue(PumpAiReviewer.isRetryableEmptyAnswer(malformed))
        assertTrue(PumpAiReviewer.emptyContentReason(malformed).contains("فراخوانی تابع"))
        assertTrue(
            !PumpAiReviewer.isRetryableEmptyAnswer(
                """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}"""
            )
        )
    }

    @Test
    fun bothGeminiRoutesAreKeptAndTriedInOrder() {
        val coin = PumpScanner.PumpCoin(id = "sol", symbol = "sol", name = "Solana", change24h = 12.0)
        val nativeConfig = PumpAiConfig(
            enabled = true,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-flash-latest"
        )
        val routes = PumpAiReviewer.reviewRoutes(nativeConfig, coin)
        assertEquals(3, routes.size)
        assertTrue(routes[0].nativeGemini && routes[0].payload.toString().contains("responseSchema"))
        assertTrue(routes[1].nativeGemini && !routes[1].payload.toString().contains("responseSchema"))
        assertTrue(!routes[2].nativeGemini)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            routes[2].endpoint
        )

        val compatConfig = nativeConfig.copy(
            endpoint = "https://generativelanguage.googleapis.com/v1beta/openai",
            model = "gemini-2.0-flash"
        )
        val compatRoutes = PumpAiReviewer.reviewRoutes(compatConfig, coin)
        assertEquals(3, compatRoutes.size)
        assertTrue(!compatRoutes[0].nativeGemini)
        assertTrue(!compatRoutes[1].nativeGemini)
        assertTrue(compatRoutes[2].nativeGemini)

        val openAi = PumpAiConfig(enabled = true, endpoint = "https://api.openai.com/v1", model = "gpt-4o-mini")
        val openAiRoutes = PumpAiReviewer.reviewRoutes(openAi, coin)
        assertEquals(3, openAiRoutes.size)
        assertTrue(openAiRoutes[0].payload.toString().contains("web_search_options"))
        assertTrue(openAiRoutes[0].payload.toString().contains("response_format"))
        assertTrue(openAiRoutes[0].providerSearchRequested)
        assertTrue(openAiRoutes.drop(1).none { it.providerSearchRequested })
        assertTrue(!openAiRoutes.last().payload.toString().contains("web_search_options"))
        assertTrue(!openAiRoutes.last().payload.toString().contains("response_format"))
    }

    @Test
    fun keyAndQuotaErrorsDoNotRetryOtherRoutes() {
        assertTrue(PumpAiReviewer.isFatalServiceError(Http.HttpException(401, "HTTP 401"), "کلید"))
        assertTrue(!PumpAiReviewer.isFatalServiceError(Http.HttpException(400, "HTTP 400"), "خطا"))
    }

    @Test
    fun reviewParsesTheTechnicalDecisionFields() {
        val review = PumpAiReviewer.parseReview(
            """
            {"verdict":"محتاط‌تر","recommendation":"صبر کن؛ ورود عجولانه نکن",
             "action":"ورود پله‌ای فقط بالای ۱٫۲۰ دلار","entry":"۱٫۱۰ تا ۱٫۱۵ دلار",
             "stopLoss":"۱٫۰۲ دلار","targets":"هدف ۱: ۱٫۳۵ | هدف ۲: ۱٫۵۰","timeframe":"۲۴ تا ۷۲ ساعت",
             "invalidation":"بسته‌شدن زیر کف ۲۴ ساعته","technical":"قیمت بالای ابر ایچیموکو",
             "project":"تیم X روی شبکه Y","catalysts":"آنلاک توکن هفته‌ی آینده","risks":"نقدشوندگی کم",
             "summary":"حرکت ادامه دارد ولی ورود در سقف پرریسک است.","confidence":62,"news":[]}
            """.trimIndent()
        )
        assertEquals("۱٫۰۲ دلار", review.stopLoss)
        assertTrue(review.action.contains("پله‌ای"))
        assertTrue(review.targets.contains("هدف ۲"))
        assertTrue(review.project.contains("شبکه"))
        assertTrue(review.summary.isNotBlank())
        assertEquals(62, review.confidence)
    }

    @Test
    fun onlyGuaranteedProfitClaimsAreRejected() {
        val ok = PumpAiReviewer.parseReview(
            """{"verdict":"همسو","recommendation":"فعلاً فقط زیر نظر بگیر","reason":"در صورت تثبیت، خرید پله‌ای منطقی است","news":[]}"""
        )
        assertTrue(ok.reason.contains("خرید پله‌ای"))

        val blocked = PumpAiReviewer.parseReview(
            """{"verdict":"همسو","recommendation":"فعلاً فقط زیر نظر بگیر","reason":"سود تضمینی دارد","news":[]}"""
        )
        assertTrue(blocked.reason.contains("سود تضمینی/بدون ریسک"))
    }

    @Test
    fun promptCarriesTechnicalContextIncludingIchimoku() {
        val coin = PumpScanner.PumpCoin(
            id = "sol", symbol = "sol", name = "Solana",
            price = 150.0, change1h = 1.0, change24h = 12.0,
            high24h = 155.0, low24h = 135.0, volume = 5e8, marketCap = 7e10,
            spark = (1..40).map { 100.0 + it }
        )
        val prompt = PumpAiReviewer.userPrompt(coin)
        assertTrue(prompt.contains("ایچیموکو"))
        assertTrue(prompt.contains("فاصله تا ATH") || prompt.contains("سقف ۲۴ ساعته"))
        assertTrue(prompt.contains("گردش حجم"))

        val system = PumpAiReviewer.systemPrompt()
        assertTrue(system.contains("stopLoss"))
        assertTrue(system.contains("project"))
        assertTrue(system.contains("کلی‌گویی ممنوع"))
    }

    @Test
    fun thinkingIsDisabledForFastAnswersOnSupportedModels() {
        assertTrue(PumpAiReviewer.supportsThinkingBudget("gemini-2.5-flash"))
        assertTrue(PumpAiReviewer.supportsThinkingBudget("gemini-flash-latest"))
        assertTrue(!PumpAiReviewer.supportsThinkingBudget("gemini-2.0-flash"))

        val body = PumpAiReviewer.geminiBody("s", "u", 2048, jsonOutput = true, disableThinking = true)
            .toString()
        assertTrue(body.contains("thinkingConfig"))
        assertTrue(body.contains("\"thinkingBudget\":0"))
        assertTrue(!PumpAiReviewer.geminiBody("s", "u", 512).toString().contains("thinkingConfig"))
    }

    @Test
    fun fallbackRoutesUseShorterTimeouts() {
        val coin = PumpScanner.PumpCoin(id = "sol", symbol = "sol", name = "Solana", change24h = 9.0)
        val routes = PumpAiReviewer.reviewRoutes(
            PumpAiConfig(
                enabled = true,
                endpoint = "https://generativelanguage.googleapis.com/v1beta",
                model = "gemini-2.5-flash"
            ),
            coin
        )
        assertEquals(PumpAiReviewer.REQUEST_TIMEOUT_SECONDS, routes[0].timeoutSeconds)
        assertEquals(PumpAiReviewer.FALLBACK_TIMEOUT_SECONDS, routes[1].timeoutSeconds)
        assertTrue(routes[0].payload.toString().contains("thinkingConfig"))
    }
}
