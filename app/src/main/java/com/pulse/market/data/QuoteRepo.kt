package com.pulse.market.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * گرفتن قیمت‌ها به‌صورت موازی + کش مشترک بین ویجت‌ها:
 * هر نماد فقط یک بار از شبکه گرفته می‌شود، حتی اگر چند ویجت آن را نشان دهند.
 */
object QuoteRepo {

    private const val PREF = "pulse_cache"
    private const val KEY_QUOTES = "quotes"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** کلید یکتای هر نماد: منبع + کد */
    fun key(sourceId: String, code: String) = "$sourceId|$code"

    // ───────────── کش ─────────────

    fun loadCachedMap(context: Context): Map<String, Quote> {
        val raw = prefs(context).getString(KEY_QUOTES, null) ?: return emptyMap()
        val list = runCatching {
            json.decodeFromString(ListSerializer(Quote.serializer()), raw)
        }.getOrDefault(emptyList())
        return list.associateBy { key(it.sourceId, it.code) }
    }

    private fun mergeCached(context: Context, fresh: List<Quote>) {
        val merged = loadCachedMap(context).toMutableMap()
        fresh.forEach { merged[key(it.sourceId, it.code)] = it }
        prefs(context).edit()
            .putString(
                KEY_QUOTES,
                json.encodeToString(ListSerializer(Quote.serializer()), merged.values.toList())
            )
            .putLong("ts", System.currentTimeMillis())
            .apply()
    }

    fun lastUpdated(context: Context): Long = prefs(context).getLong("ts", 0L)

    // ───────────── شبکه ─────────────

    /**
     * نمادهایی که این پیکربندی باید نشان دهد:
     * (منبع، نماد) — انتخاب صریح یا چند نماد پیش‌فرض از هر منبع
     */
    suspend fun wantedFor(context: Context, cfg: WidgetConfig): List<Pair<String, SymbolDef>> {
        val sourceIds = cfg.activeSourceIds
        return sourceIds.flatMap { sid ->
            val syms = cfg.symbolsOf(sid).ifEmpty {
                ConfigStore.resolveSource(context, sid)
                    ?.symbols?.take(maxOf(1, 4 / sourceIds.size))
                    ?: emptyList()
            }
            syms.map { sid to it }
        }
    }

    /**
     * گرفتن همه‌ی نمادهای همه‌ی ویجت‌ها با کم‌ترین درخواست ممکن:
     * هر منبع فقط یک بار (و دسته‌ای) پرسیده می‌شود؛ نتیجه در کش مشترک ادغام می‌شود.
     */
    suspend fun refreshMany(
        context: Context,
        wantedPerWidget: List<List<Pair<String, SymbolDef>>>
    ): Map<String, Quote> {
        val bySource: Map<String, List<SymbolDef>> = wantedPerWidget.flatten()
            .distinctBy { it.first to it.second.code }
            .groupBy({ it.first }, { it.second })
        if (bySource.isEmpty()) return loadCachedMap(context)

        val fetched = coroutineScope {
            bySource.map { (sid, syms) ->
                async {
                    val src = ConfigStore.resolveSource(context, sid) ?: return@async emptyList()
                    Fetcher.fetchAll(src, syms)
                }
            }.awaitAll().flatten()
        }

        // اگر همه خطا دادند، کش قدیمی نگه داشته می‌شود
        if (fetched.any { it.price != null }) mergeCached(context, fetched)
        return loadCachedMap(context) + fetched.associateBy { key(it.sourceId, it.code) }
    }
}
