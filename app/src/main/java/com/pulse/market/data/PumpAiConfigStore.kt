package com.pulse.market.data

import android.content.Context
import org.json.JSONObject

/**
 * تنظیمات سراسریِ سرویس هوش مصنوعی پامپ.
 * کلید عمداً خارج از WidgetConfig و بکاپ دستی نگه داشته می‌شود و فقط در فضای خصوصی
 * برنامه ذخیره می‌شود (allowBackup برنامه نیز خاموش است).
 */
data class PumpAiConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val providerSearch: Boolean = true
) {
    val isReady: Boolean
        get() = endpoint.trim().let { it.startsWith("https://") || it.startsWith("http://") } &&
                model.isNotBlank()
}

object PumpAiConfigStore {
    private const val PREF = "pulse_pump_ai"
    private const val KEY_CONFIG = "config"

    fun load(context: Context): PumpAiConfig {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG, null) ?: return PumpAiConfig()
        return runCatching {
            val obj = JSONObject(raw)
            sanitize(
                PumpAiConfig(
                    enabled = obj.optBoolean("enabled", false),
                    endpoint = obj.optString("endpoint"),
                    model = obj.optString("model"),
                    apiKey = obj.optString("apiKey"),
                    providerSearch = obj.optBoolean("providerSearch", true)
                )
            )
        }.getOrDefault(PumpAiConfig())
    }

    fun save(context: Context, config: PumpAiConfig) {
        val safe = sanitize(config)
        val raw = JSONObject()
            .put("enabled", safe.enabled)
            .put("endpoint", safe.endpoint)
            .put("model", safe.model)
            .put("apiKey", safe.apiKey)
            .put("providerSearch", safe.providerSearch)
            .toString()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, raw)
            .apply()
    }

    private fun sanitize(config: PumpAiConfig): PumpAiConfig = config.copy(
        endpoint = config.endpoint.trim().take(500),
        model = config.model.trim().take(150),
        apiKey = config.apiKey.trim().take(1000)
    )
}
