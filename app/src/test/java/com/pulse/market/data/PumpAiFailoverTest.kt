package com.pulse.market.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** زنجیره‌ی چند سرویس هوش مصنوعی و تطبیق پارامترهای مدل‌های تازه. */
class PumpAiFailoverTest {

    private fun gemini(key: String = "k") = PumpAiBackup(
        endpoint = "https://generativelanguage.googleapis.com/v1beta",
        model = "gemini-flash-latest",
        apiKey = key
    )

    private val openAiPrimary = PumpAiConfig(
        enabled = true,
        endpoint = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
        apiKey = "sk-primary"
    )

    @Test
    fun chainKeepsPrimaryFirstThenActiveBackups() {
        val config = openAiPrimary.copy(
            backups = listOf(
                gemini(),
                PumpAiBackup(
                    endpoint = "https://openrouter.ai/api/v1",
                    model = "openai/gpt-4o-mini",
                    apiKey = "sk-or",
                    active = false
                ),
                PumpAiBackup(endpoint = "https://api.anthropic.com/v1", model = "claude-sonnet-4-5")
            )
        )

        val chain = config.chain
        assertEquals(3, chain.size)
        assertEquals("https://api.openai.com/v1", chain[0].endpoint)
        assertEquals("gemini-flash-latest", chain[1].model)
        assertEquals("claude-sonnet-4-5", chain[2].model)
        // پشتیبان خاموش هرگز وارد زنجیره نمی‌شود.
        assertTrue(chain.none { it.endpoint.contains("openrouter") })
        assertTrue(config.anyReady)
        assertEquals(2, config.readyBackupCount)
        // هر حلقه‌ی زنجیره یک سرویس تنهاست و خودش پشتیبان ندارد (بدون حلقه‌ی بی‌پایان).
        assertTrue(chain.all { it.backups.isEmpty() })
    }

    @Test
    fun incompleteOrEmptyBackupsNeverEnterTheChain() {
        val config = openAiPrimary.copy(
            backups = listOf(
                PumpAiBackup(),
                PumpAiBackup(endpoint = "https://example.com/v1"),
                PumpAiBackup(endpoint = "notaurl", model = "m")
            )
        )
        assertEquals(1, config.chain.size)
        assertEquals(0, config.readyBackupCount)
        // ردیف‌های ناقص برای تست اتصال دیده می‌شوند تا کاربر دلیل را ببیند.
        assertEquals(3, config.configuredChain.size)
    }

    @Test
    fun backupOnlySetupIsStillUsable() {
        val config = PumpAiConfig(enabled = true, backups = listOf(gemini()))
        assertFalse(config.isReady)
        assertTrue(config.anyReady)
        assertEquals(1, config.chain.size)
    }

    @Test
    fun sanitizeTrimsAndCapsBackups() {
        val config = PumpAiConfig(
            endpoint = "  https://api.openai.com/v1  ",
            model = " gpt-4o-mini ",
            apiKey = " sk-x ",
            backups = List(6) { gemini(" key$it ") }
        )
        val safe = PumpAiConfigStore.sanitize(config)
        assertEquals("https://api.openai.com/v1", safe.endpoint)
        assertEquals("gpt-4o-mini", safe.model)
        assertEquals("sk-x", safe.apiKey)
        assertEquals(PumpAiConfig.MAX_BACKUPS, safe.backups.size)
        assertEquals("key0", safe.backups.first().apiKey)
    }

    @Test
    fun chainErrorListsEveryProviderWithoutLeakingKeys() {
        val chain = openAiPrimary.copy(backups = listOf(gemini())).chain
        val lines = chain.mapIndexed { index, provider ->
            PumpAiReviewer.providerErrorLine(index, provider, "خطای تست")
        }
        val text = PumpAiReviewer.chainErrorText(lines)

        assertTrue(text.contains("api.openai.com"))
        assertTrue(text.contains("generativelanguage.googleapis.com"))
        assertTrue(text.contains("سرویس اصلی"))
        assertTrue(text.contains("پشتیبان 1"))
        assertFalse(text.contains("sk-primary"))
    }

    @Test
    fun singleProviderErrorStaysShort() {
        val line = PumpAiReviewer.providerErrorLine(0, openAiPrimary, "کلید API پذیرفته نشد")
        assertEquals("کلید API پذیرفته نشد", PumpAiReviewer.chainErrorText(listOf(line)))
    }

    @Test
    fun modernOpenAiModelsGetMaxCompletionTokens() {
        assertTrue(PumpAiReviewer.needsModernTokenParam("gpt-5-mini"))
        assertTrue(PumpAiReviewer.needsModernTokenParam("openai/o3-mini"))
        assertTrue(PumpAiReviewer.needsModernTokenParam("o1"))
        assertFalse(PumpAiReviewer.needsModernTokenParam("gpt-4o-mini"))
        assertFalse(PumpAiReviewer.needsModernTokenParam("openai/gpt-4.1"))
        assertFalse(PumpAiReviewer.needsModernTokenParam("gemini-2.0-flash"))
    }

    @Test
    fun modernPayloadDropsTemperatureAndRenamesTokenLimit() {
        val payload = buildJsonObject {
            put("model", "gpt-5-mini")
            put("temperature", 0.2)
            put("max_tokens", 1200)
        }
        val modern = PumpAiReviewer.modernizeChatPayload(payload)
        assertNull(modern["max_tokens"])
        assertNull(modern["temperature"])
        assertEquals("1200", modern["max_completion_tokens"].toString())
    }

    @Test
    fun requestPayloadOnlyTouchesOpenAiCompatibleRoutes() {
        val gpt5 = PumpAiConfig(enabled = true, endpoint = "https://api.openai.com/v1", model = "gpt-5-mini")
        val payload = buildJsonObject {
            put("model", "gpt-5-mini")
            put("temperature", 0.2)
            put("max_tokens", 1200)
        }
        val chat = PumpAiReviewer.requestPayload(
            gpt5, "https://api.openai.com/v1/chat/completions", payload
        )
        assertNotNull(chat["max_completion_tokens"])

        val geminiConfig = PumpAiConfig(
            enabled = true,
            endpoint = PumpAiReviewer.GOOGLE_BASE,
            model = "gemini-flash-latest"
        )
        val geminiBody: JsonObject = buildJsonObject { put("max_tokens", 10) }
        assertEquals(
            geminiBody,
            PumpAiReviewer.requestPayload(
                geminiConfig,
                "${PumpAiReviewer.GOOGLE_BASE}/models/gemini-flash-latest:generateContent",
                geminiBody
            )
        )
    }

    @Test
    fun serviceComplaintAboutParameterIsRepairedOnce() {
        val payload = buildJsonObject {
            put("model", "some-new-model")
            put("temperature", 0.2)
            put("max_tokens", 900)
        }
        val repaired = PumpAiReviewer.payloadForParameterError(
            payload,
            "HTTP 400 — Unsupported parameter: 'max_tokens' is not supported with this model. " +
                "Use 'max_completion_tokens' instead."
        )
        assertNotNull(repaired)
        assertNull(repaired!!["max_tokens"])
        assertEquals("900", repaired["max_completion_tokens"].toString())

        // خطاهای بی‌ربط نباید بدنه را دست‌کاری کنند.
        assertNull(PumpAiReviewer.payloadForParameterError(payload, "HTTP 401 — invalid api key"))
        assertNull(
            PumpAiReviewer.payloadForParameterError(
                buildJsonObject { put("model", "m") },
                "Unsupported parameter: 'max_tokens'"
            )
        )
    }

    @Test
    fun temperatureComplaintDropsTemperatureOnly() {
        val payload = buildJsonObject {
            put("model", "o4-mini")
            put("temperature", 0.2)
            put("max_completion_tokens", 900)
        }
        val repaired = PumpAiReviewer.payloadForParameterError(
            payload,
            "Unsupported value: 'temperature' does not support 0.2 with this model."
        )
        assertNotNull(repaired)
        assertNull(repaired!!["temperature"])
        assertEquals("900", repaired["max_completion_tokens"].toString())
        assertEquals("\"o4-mini\"", repaired["model"].toString())
    }

    @Test
    fun connectionTargetFollowsFirstReadyProvider() {
        val config = PumpAiConfig(
            enabled = true,
            endpoint = "notaurl",
            model = "",
            backups = listOf(gemini())
        )
        val target = PumpAiReviewer.connectionTarget(config)
        assertNotNull(target)
        assertEquals("generativelanguage.googleapis.com", target!!.host)
    }

    @Test
    fun reasoningModelsGetAWorkableOutputBudget() {
        // سقف ۱۶ توکنیِ تست اتصال روی مدل استدلالی، پاسخ را بی‌متن می‌کرد: توکن‌های
        // «تفکر» از همان سقف کم می‌شوند، پس برای این مدل‌ها ذخیره‌ی اضافه لازم است.
        val reserve = PumpAiReviewer.REASONING_TOKEN_RESERVE
        assertEquals(16 + reserve, PumpAiReviewer.tokenBudget("gpt-5-mini", 16))
        assertEquals(1200 + reserve, PumpAiReviewer.tokenBudget("o3-mini", 1200))
        assertEquals(4096 + reserve, PumpAiReviewer.tokenBudget("gpt-5", 4096))
        assertEquals(
            PumpAiReviewer.MAX_REASONING_TOKENS,
            PumpAiReviewer.tokenBudget("o3", PumpAiReviewer.MAX_REASONING_TOKENS)
        )
        // مدل‌های معمولی دقیقاً همان بودجه‌ی خواسته‌شده را می‌گیرند.
        assertEquals(16, PumpAiReviewer.tokenBudget("gpt-4o-mini", 16))
        assertEquals(1200, PumpAiReviewer.tokenBudget("gemini-2.0-flash", 1200))
    }

    @Test
    fun iranReachableGatewaysArePresetAndRoutable() {
        val iranian = PumpAiConfig.PRESETS.filter {
            it.endpoint.endsWith(".ir/v1") || it.id == "gapgpt"
        }
        assertTrue(iranian.isNotEmpty())
        for (preset in iranian) {
            val config = PumpAiConfig(
                enabled = true,
                endpoint = preset.endpoint,
                model = preset.model,
                apiKey = "test"
            )
            assertTrue(preset.id, config.isReady)
            assertTrue(
                preset.id,
                PumpAiReviewer.chatCompletionsEndpoint(preset.endpoint).endsWith("/v1/chat/completions")
            )
            // درگاه ایرانی نباید به مسیر بومی Gemini/Anthropic بیفتد.
            assertFalse(preset.id, PumpAiReviewer.isGeminiNative(preset.endpoint))
            assertFalse(preset.id, PumpAiReviewer.isAnthropicHost(preset.endpoint))
        }
    }

    @Test
    fun pastedKeysAreCleanedOfInvisibleAndWrappingText() {
        // کپی از پیام‌رسان فارسی: نیم‌فاصله، فاصله‌ی سخت، خط‌جدید و پیشوند Bearer.
        assertEquals("sk-or-v1-abc", PumpAiConfigStore.sanitizeKey("  sk-or-v1-abc \n"))
        assertEquals("sk-or-v1-abc", PumpAiConfigStore.sanitizeKey("Bearer sk-or-v1-abc"))
        assertEquals("sk-or-v1-abc", PumpAiConfigStore.sanitizeKey("sk-or-\u200cv1-\u00a0abc"))
        assertEquals("sk-ant-x", PumpAiConfigStore.sanitizeKey("\u202bsk-ant-x\u202c"))
        // کلیدی که واقعاً با همین حروف شروع می‌شود دست‌نخورده می‌ماند.
        assertEquals("tokenabc123", PumpAiConfigStore.sanitizeKey("tokenabc123"))
    }

    @Test
    fun keyWarningExplainsTheUsualCausesOfHttp401() {
        assertNotNull(PumpAiReviewer.keyWarning("https://openrouter.ai/api/v1", ""))
        assertNotNull(PumpAiReviewer.keyWarning("https://openrouter.ai/api/v1", "sk-ant-aaaaaaaaaaaaaaaaaaaa"))
        assertNotNull(PumpAiReviewer.keyWarning("https://api.anthropic.com/v1", "sk-or-v1-aaaaaaaaaaaaaaa"))
        assertNotNull(PumpAiReviewer.keyWarning("https://api.openai.com/v1", "sk-short"))
        assertNotNull(PumpAiReviewer.keyWarning("https://example.com/v1", "abc def ghijklmnopqrstuv"))
        // کلید درست و سرویس ناشناخته نباید هشدار بی‌مورد بگیرد.
        assertNull(PumpAiReviewer.keyWarning("https://openrouter.ai/api/v1", "sk-or-v1-0123456789abcdef"))
        assertNull(PumpAiReviewer.keyWarning("https://example.com/v1", "0123456789abcdefghijklmn"))
    }

    @Test
    fun authErrorsCarryTheKeyHintAndNeverTheKey() {
        val config = PumpAiConfig(
            enabled = true,
            endpoint = "https://openrouter.ai/api/v1",
            model = "openai/gpt-4o-mini",
            apiKey = "sk-ant-should-not-be-here"
        )
        val text = PumpAiReviewer.withKeyHint(config, 401, "کلید API پذیرفته نشد (۴۰۱)")
        assertTrue(text.contains("sk-or-"))
        assertFalse(text.contains("should-not-be-here"))
        // خطاهای غیرکلیدی پیام اضافه نمی‌گیرند.
        assertEquals("خطای ۵۰۰", PumpAiReviewer.withKeyHint(config, 500, "خطای ۵۰۰"))
    }

    @Test
    fun keyFingerprintShowsShapeWithoutTheSecret() {
        val print = PumpAiReviewer.keyFingerprint("sk-or-v1-0123456789abcdef")
        assertTrue(print.contains("sk-or"))
        assertFalse(print.contains("0123456789"))
        assertEquals("ذخیره نشده", PumpAiReviewer.keyFingerprint(""))
    }

    @Test
    fun economyModeShrinksPromptAndOutputBudget() {
        val coin = PumpScanner.PumpCoin(
            id = "sol", symbol = "sol", name = "Solana",
            price = 150.0, change1h = 1.0, change24h = 12.0,
            high24h = 155.0, low24h = 135.0, volume = 5e8, marketCap = 7e10,
            spark = (1..40).map { 100.0 + it }
        )
        val full = PumpAiReviewer.systemPrompt(economy = false)
        val compact = PumpAiReviewer.systemPrompt(economy = true)
        assertTrue(compact.length < full.length * 2 / 3)
        // قواعد حیاتی نباید در نسخه‌ی فشرده گم شود.
        for (key in listOf("stopLoss", "project", "invalidation", "news", "کلی‌گویی ممنوع")) {
            assertTrue(key, compact.contains(key))
        }
        val longPrompt = PumpAiReviewer.userPrompt(coin, economy = false)
        val shortPrompt = PumpAiReviewer.userPrompt(coin, economy = true)
        assertTrue(shortPrompt.length < longPrompt.length)
        assertTrue(shortPrompt.contains("ایچیموکو"))
        assertTrue(shortPrompt.contains("Solana"))

        val economy = PumpAiConfig(
            enabled = true,
            endpoint = "https://openrouter.ai/api/v1",
            model = "openai/gpt-4o-mini",
            economyMode = true
        )
        val rich = economy.copy(economyMode = false)
        assertEquals(800, PumpAiReviewer.reviewOutputTokens(economy, nativeRoute = false))
        assertEquals(1200, PumpAiReviewer.reviewOutputTokens(rich, nativeRoute = false))
        assertEquals(1100, PumpAiReviewer.reviewOutputTokens(economy, nativeRoute = true))
        assertEquals(2048, PumpAiReviewer.reviewOutputTokens(rich, nativeRoute = true))
        assertEquals(2457, PumpAiReviewer.completionTokens(economy, 4096))
        assertEquals(4096, PumpAiReviewer.completionTokens(rich, 4096))
        // سقف خیلی کوچک نباید پاسخ را ناقص کند.
        assertEquals(768, PumpAiReviewer.completionTokens(economy, 900))

        val body = PumpAiReviewer.reviewRoutes(economy, coin).first().payload.toString()
        assertTrue(body.contains("\"max_tokens\":800"))
        assertTrue(body.contains("\"max_results\":3"))
    }

    @Test
    fun usageIsReadFromEveryProviderShape() {
        val openai = PumpAiReviewer.parseUsage(
            """{"usage":{"prompt_tokens":1200,"completion_tokens":300,"total_tokens":1500}}"""
        )
        assertEquals(1200, openai?.input)
        assertEquals(300, openai?.output)
        val anthropic = PumpAiReviewer.parseUsage("""{"usage":{"input_tokens":90,"output_tokens":40}}""")
        assertEquals(130, anthropic?.total)
        val gemini = PumpAiReviewer.parseUsage(
            """{"usageMetadata":{"promptTokenCount":500,"candidatesTokenCount":200,"thoughtsTokenCount":50}}"""
        )
        assertEquals(500, gemini?.input)
        assertEquals(250, gemini?.output)
        assertNull(PumpAiReviewer.parseUsage("""{"choices":[]}"""))
        // تخمین جایگزین وقتی سرویس چیزی گزارش نمی‌کند.
        assertEquals(3, PumpAiReviewer.estimateTokens("abcdefghijk"))
    }

    @Test
    fun dailyBudgetStopsNewRequestsButZeroMeansUnlimited() {
        assertNull(PumpAiReviewer.budgetMessage(budget = 0, usedToday = 999_999))
        assertNull(PumpAiReviewer.budgetMessage(budget = 10_000, usedToday = 9_999))
        val blocked = PumpAiReviewer.budgetMessage(budget = 10_000, usedToday = 10_000)
        assertTrue(blocked!!.contains("سقف مصرف روزانه"))
        assertTrue(blocked.contains("10000"))
    }

    @Test
    fun economyPolicyIsSharedWithBackupProviders() {
        val config = PumpAiConfig(
            enabled = true,
            endpoint = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            apiKey = "sk-0123456789abcdefghij",
            economyMode = true,
            dailyTokenBudget = 50_000,
            reuseMinutes = 30,
            backups = listOf(
                PumpAiBackup(
                    endpoint = "https://api.avalai.ir/v1",
                    model = "gpt-4o-mini",
                    apiKey = "aa-0123456789abcdefghij"
                )
            )
        )
        val backup = config.chain[1]
        assertTrue(backup.economyMode)
        assertEquals(50_000, backup.dailyTokenBudget)
        assertEquals(30, backup.reuseMinutes)
    }
}
