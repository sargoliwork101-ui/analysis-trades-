package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** کش کوچک خبرها برای بازشدن فوری/آفلاین؛ هیچ کلید یا داده‌ی شخصی در آن نیست. */
object NewsCacheStore {
    private const val PREF = "pulse_market_news"
    private const val KEY = "latest"
    private const val MAX_ITEMS = 40
    const val FRESH_MS = 10 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Snapshot(
        val fetchedAt: Long = 0L,
        val items: List<MarketNewsItem> = emptyList()
    )

    fun load(context: Context): Snapshot? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        return runCatching { json.decodeFromString(Snapshot.serializer(), raw) }
            .getOrNull()
            ?.takeIf { it.items.isNotEmpty() }
    }

    fun save(context: Context, fetchedAt: Long, items: List<MarketNewsItem>) {
        val safe = Snapshot(fetchedAt, items.take(MAX_ITEMS))
        val raw = runCatching { json.encodeToString(Snapshot.serializer(), safe) }.getOrNull() ?: return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}
