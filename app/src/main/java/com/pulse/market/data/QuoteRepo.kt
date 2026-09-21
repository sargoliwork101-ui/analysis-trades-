package com.pulse.market.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * گرفتن قیمت‌ها به‌صورت موازی + کش کردن آخرین نتیجه،
 * تا ویجت حتی وقتی شبکه قطع است عدد قبلی را نشان بدهد.
 */
object QuoteRepo {

    private const val PREF = "pulse_cache"
    private const val KEY_QUOTES = "quotes"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun loadCached(context: Context): List<Quote> {
        val raw = prefs(context).getString(KEY_QUOTES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Quote.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveCached(context: Context, quotes: List<Quote>) {
        prefs(context).edit()
            .putString(KEY_QUOTES, json.encodeToString(ListSerializer(Quote.serializer()), quotes))
            .putLong("ts", System.currentTimeMillis())
            .apply()
    }

    fun lastUpdated(context: Context): Long = prefs(context).getLong("ts", 0L)

    /** گرفتن همه‌ی نمادهای انتخاب‌شده به‌صورت هم‌زمان */
    suspend fun refresh(context: Context, cfg: WidgetConfig): List<Quote> {
        val source = ConfigStore.resolveSource(context, cfg.sourceId) ?: return emptyList()
        val symbols = cfg.symbols.ifEmpty { source.symbols.take(cfg.rows) }
        if (symbols.isEmpty()) return emptyList()

        val quotes = coroutineScope {
            symbols.map { sym -> async { Fetcher.fetch(source, sym) } }.awaitAll()
        }

        // اگر همه خطا دادند، کش قدیمی را نگه دار
        if (quotes.any { it.price != null }) saveCached(context, quotes)
        return quotes
    }
}
