package com.pulse.market.data

import android.content.Context
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

    companion object {
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

    fun load(context: Context): PumpAiConfig {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return PumpAiConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val cipherText = obj.optString("apiKeyCipher")
            // مهاجرت یک‌باره از ۱٫۱۸: مقدار قدیمی plaintext خوانده و بلافاصله
            // با Keystore بازنویسی می‌شود؛ در شکست رمزگذاری، plaintext حذف می‌شود.
            val legacyPlainText = obj.optString("apiKey")
            val apiKey = PumpAiSecretCipher.decrypt(cipherText) ?: legacyPlainText
            val config = sanitize(
                PumpAiConfig(
                    enabled = obj.optBoolean("enabled", false),
                    endpoint = obj.optString("endpoint"),
                    model = obj.optString("model"),
                    apiKey = apiKey,
                    providerSearch = obj.optBoolean("providerSearch", true)
                )
            )
            if (legacyPlainText.isNotEmpty() && !save(context, config)) {
                // اگر Keystore در دسترس نبود، پیکربندی غیرحساس را نگه دار ولی plaintext را حذف کن.
                val withoutKey = config.copy(apiKey = "")
                save(context, withoutKey)
                return@runCatching withoutKey
            }
            config
        }.getOrDefault(PumpAiConfig())
    }

    /** false یعنی Keystore کلید تازه را نپذیرفت؛ هیچ plaintextی روی دیسک نوشته نمی‌شود. */
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
