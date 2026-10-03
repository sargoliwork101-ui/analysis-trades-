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

}
