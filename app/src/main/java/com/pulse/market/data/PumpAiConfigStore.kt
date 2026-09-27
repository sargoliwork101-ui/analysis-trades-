package com.pulse.market.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI

/**
 * تنظیمات سراسریِ سرویس هوش مصنوعی پامپ.
 * کلید خارج از WidgetConfig/بکاپ و به‌صورت AES-GCM با Android Keystore ذخیره می‌شود.
 */
data class PumpAiConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val providerSearch: Boolean = true
) {
    val endpointValid: Boolean
        get() = isValidEndpoint(endpoint)

    val insecureKeyTransport: Boolean
        get() = endpoint.trim().startsWith("http://", ignoreCase = true) && apiKey.isNotBlank()

    val isReady: Boolean
        get() = endpointValid && model.isNotBlank() && !insecureKeyTransport

    /** آیا این پیکربندی دقیقاً روی یکی از سرویس‌های آماده تنظیم شده است؟ */
    fun matches(preset: Preset): Boolean =
        endpoint.trim().trimEnd('/').equals(preset.endpoint, ignoreCase = true) &&
                model.trim().equals(preset.model, ignoreCase = true)

    /** تنظیمات پیش‌فرض یک سرویس آماده (Gemini/Claude/OpenAI) */
    data class Preset(
        val id: String,
        val title: String,
        val endpoint: String,
        val model: String,
        val hint: String,
        /** آیا جست‌وجوی وب سمت سرویس برای این ارائه‌دهنده معنا دارد؟ */
        val providerSearch: Boolean
    )

    companion object {
        /**
         * سرویس‌های آماده؛ همه از مسیر سازگار با OpenAI (chat/completions) و هدر
         * Authorization: Bearer پشتیبانی می‌کنند، پس نیازی به کد اختصاصی نیست.
         */
        val PRESETS: List<Preset> = listOf(
            Preset(
                id = "gemini",
                title = "Gemini (گوگل)",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai",
                model = "gemini-2.5-flash",
                hint = "کلید رایگان از Google AI Studio گرفته می‌شود؛ مسیر سازگار با OpenAI گوگل استفاده می‌شود.",
                providerSearch = false
            ),
            Preset(
                id = "claude",
                title = "Claude (کلاد)",
                endpoint = "https://api.anthropic.com/v1",
                model = "claude-sonnet-4-5",
                hint = "کلید از console.anthropic.com؛ Anthropic مسیر سازگار با OpenAI را پشتیبانی می‌کند.",
                providerSearch = false
            ),
            Preset(
                id = "openai",
                title = "OpenAI",
                endpoint = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                hint = "کلید از platform.openai.com؛ جست‌وجوی وب سرویس هم پشتیبانی می‌شود.",
                providerSearch = true
            ),
            Preset(
                id = "openrouter",
                title = "OpenRouter",
                endpoint = "https://openrouter.ai/api/v1",
                model = "openai/gpt-4o-mini",
                hint = "یک کلید برای چند مدل؛ افزونه‌ی جست‌وجوی وب OpenRouter فعال می‌شود.",
                providerSearch = true
            )
        )

        fun isValidEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                    !uri.host.isNullOrBlank() && uri.userInfo == null &&
                    uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }
}

object PumpAiConfigStore {
    private const val PREF = "pulse_pump_ai"
    private const val KEY_CONFIG = "config"
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pendingSave: Job? = null

    @Synchronized
    fun load(context: Context): PumpAiConfig {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return PumpAiConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val cipherText = obj.optString("apiKeyCipher")
            // مهاجرت یک‌باره از ۱٫۱۸: مقدار قدیمی plaintext خوانده و بلافاصله
            // با Keystore بازنویسی می‌شود؛ در شکست رمزگذاری، plaintext حذف می‌شود.
            val legacyPlainText = obj.optString("apiKey")
            // decrypt برای ciphertext خالی رشته‌ی خالی برمی‌گرداند؛ پس فقط مقدار
            // واقعاً رمزگشایی‌شده جایگزین plaintext قدیمی می‌شود، وگرنه کلید کاربر
            // در مهاجرت از نسخه‌ی ۱٫۱۸ گم می‌شد.
            val decrypted = PumpAiSecretCipher.decrypt(cipherText)?.takeIf { it.isNotEmpty() }
            val apiKey = decrypted ?: legacyPlainText
            // هر مقدار plaintext باقی‌مانده روی دیسک باید با نسخه‌ی رمزشده بازنویسی شود.
            val needsMigration = legacyPlainText.isNotEmpty()
            val config = sanitize(
                PumpAiConfig(
                    enabled = obj.optBoolean("enabled", false),
                    endpoint = obj.optString("endpoint"),
                    model = obj.optString("model"),
                    apiKey = apiKey,
                    providerSearch = obj.optBoolean("providerSearch", true)
                )
            )
            if (needsMigration && !save(context, config)) {
                // اگر Keystore در دسترس نبود، پیکربندی غیرحساس را نگه دار ولی plaintext را حذف کن.
                val withoutKey = config.copy(apiKey = "")
                save(context, withoutKey)
                return@runCatching withoutKey
            }
            config
        }.getOrDefault(PumpAiConfig())
    }

    /**
     * ذخیره‌ی خارج از thread اصلی با debounce؛ با خروج از صفحه لغو نمی‌شود و Keystore
     * روی هر حرف تایپ‌شده اجرا نمی‌شود.
     */
    @Synchronized
    fun saveDebounced(
        context: Context,
        config: PumpAiConfig,
        onResult: (Boolean) -> Unit = {}
    ) {
        pendingSave?.cancel()
        val appContext = context.applicationContext
        pendingSave = saveScope.launch {
            delay(400)
            val result = save(appContext, config)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** false یعنی Keystore کلید تازه را نپذیرفت؛ هیچ plaintextی روی دیسک نوشته نمی‌شود. */
    @Synchronized
    fun save(context: Context, config: PumpAiConfig): Boolean {
        val safe = sanitize(config)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existingCipher = prefs.getString(KEY_CONFIG, null)?.let { raw ->
            runCatching { JSONObject(raw).optString("apiKeyCipher") }.getOrNull()
        }.orEmpty()
        val existingPlain = PumpAiSecretCipher.decrypt(existingCipher)
        val cipher = when {
            safe.apiKey.isEmpty() -> ""
            existingPlain == safe.apiKey -> existingCipher
            else -> runCatching { PumpAiSecretCipher.encrypt(safe.apiKey) }.getOrNull().orEmpty()
        }
        val secureSave = safe.apiKey.isEmpty() || cipher.isNotEmpty()
        // در شکست Keystore، تنظیم قبلی (و ciphertext سالم احتمالی) را پاک نکن.
        if (!secureSave) return false
        val raw = JSONObject()
            .put("enabled", safe.enabled)
            .put("endpoint", safe.endpoint)
            .put("model", safe.model)
            .put("apiKeyCipher", cipher)
            .put("providerSearch", safe.providerSearch)
            .toString()
        prefs.edit().putString(KEY_CONFIG, raw).apply()
        return true
    }

    private fun sanitize(config: PumpAiConfig): PumpAiConfig = config.copy(
        endpoint = config.endpoint.trim().take(500),
        model = config.model.trim().take(150),
        apiKey = config.apiKey.trim().take(1000)
    )
}
