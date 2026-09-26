package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SourceHealth(
    val sourceId: String,
    val lastCheckedAt: Long = 0L,
    val lastSuccessAt: Long = 0L,
    val lastFailureAt: Long = 0L,
    val consecutiveFailures: Int = 0,
    val successCount: Int = 0,
    val totalCount: Int = 0,
    val lastError: String = "",
    val endpointHost: String = "",
    val usingFallback: Boolean = false,
    val responseMs: Long = 0L,
    val anomalyCount: Int = 0
) {
    val isHealthy: Boolean
        get() = lastSuccessAt > 0L && consecutiveFailures == 0 && successCount > 0
}

/** وضعیت آخرین دسترسی هر منبع؛ فقط اطلاعات فنی و نام میزبان ذخیره می‌شود، نه URL یا هدر حساس. */
object SourceHealthStore {
    private const val PREF = "pulse_source_health"
    private const val KEY = "health"
    private const val MAX_SOURCES = 100
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(SourceHealth.serializer())

    @Synchronized
    fun load(context: Context): List<SourceHealth> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .filter { it.sourceId.isNotBlank() }
            .distinctBy { it.sourceId }
            .take(MAX_SOURCES)
    }

    @Synchronized
    fun record(
        context: Context,
        sourceId: String,
        quotes: List<Quote>,
        responseMs: Long,
        endpoint: Fetcher.EndpointUse?
    ) {
        val now = System.currentTimeMillis()
        val map = load(context).associateBy { it.sourceId }.toMutableMap()
        val old = map[sourceId] ?: SourceHealth(sourceId)
        val success = quotes.count { it.price?.isFinite() == true && it.error == null }
        val error = safeError(quotes.firstOrNull { it.error != null }?.error.orEmpty())
        map[sourceId] = old.copy(
            lastCheckedAt = now,
            lastSuccessAt = if (success > 0) now else old.lastSuccessAt,
            lastFailureAt = if (success == 0) now else old.lastFailureAt,
            consecutiveFailures = if (success > 0) 0 else (old.consecutiveFailures + 1).coerceAtMost(9999),
            successCount = success,
            totalCount = quotes.size,
            lastError = when {
                success == quotes.size && quotes.isNotEmpty() -> ""
                error.isNotBlank() -> error
                quotes.isEmpty() -> "نمادی برای آزمایش منبع وجود ندارد"
                else -> "برخی نمادها پاسخ معتبر ندادند"
            },
            endpointHost = endpoint?.host.orEmpty().take(200),
            usingFallback = endpoint?.fallback ?: false,
            responseMs = responseMs.coerceAtLeast(0L)
        )
        save(context, map.values.toList())
    }

    @Synchronized
    fun recordFailure(context: Context, sourceId: String, message: String) {
        record(
            context = context,
            sourceId = sourceId,
            quotes = listOf(Quote(code = "health", label = "health", sourceId = sourceId, error = message.take(160))),
            responseMs = 0L,
            endpoint = Fetcher.lastEndpoint(sourceId)
        )
    }

    @Synchronized
    fun recordAnomalies(context: Context, sourceId: String, count: Int) {
        if (count <= 0) return
        val map = load(context).associateBy { it.sourceId }.toMutableMap()
        val old = map[sourceId] ?: SourceHealth(sourceId)
        map[sourceId] = old.copy(anomalyCount = (old.anomalyCount + count).coerceAtMost(9999))
        save(context, map.values.toList())
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun safeError(message: String): String = message
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[آدرس حذف شد]")
        .replace(Regex("(?i)(token|api[_-]?key|authorization)\\s*[:=]\\s*\\S+"), "\$1=[حذف شد]")
        .take(160)

    private fun save(context: Context, values: List<SourceHealth>) {
        val safe = values.sortedByDescending { it.lastCheckedAt }.take(MAX_SOURCES)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(serializer, safe))
            .apply()
    }
}
