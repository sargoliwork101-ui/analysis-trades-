package com.pulse.market.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * گرفتن قیمت‌ها به‌صورت موازی + کش مشترک بین ویجت‌ها:
 * هر نماد فقط یک بار از شبکه گرفته می‌شود، حتی اگر چند ویجت آن را نشان دهند.
 *
 * «تاریخچه‌ی قیمت» هم اینجا روی گوشی ذخیره می‌شود: با هر به‌روزرسانی سالم یک نقطه
 * به سری هر نماد اضافه می‌شود تا نمودار مینیاتوری (sparkline) برای همه‌ی منابع —
 * حتی بورس تهران که سری آماده نمی‌دهد — شکل بگیرد. تاریخچه به‌ازای هر «نماد»
 * نگه داشته می‌شود (نه هر ویجت) تا ویجت‌هایی که نماد یکسان دارند داده را مشترک
 * استفاده کنند و حافظه تلف نشود؛ تعداد نقاطِ «نمایش» را هر ویجت خودش تعیین می‌کند.
 */
object QuoteRepo {

    private const val PREF = "pulse_cache"
    private const val KEY_QUOTES = "quotes"
    private const val KEY_HISTORY = "price_history"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val historySerializer =
        MapSerializer(String.serializer(), ListSerializer(Double.serializer()))

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
        // فقط داده‌ی سالم (با قیمت) در کش نوشته می‌شود تا با خطا، مقدار قبلی از بین نرود
        fresh.filter { it.price != null }.forEach { merged[key(it.sourceId, it.code)] = it }
        prefs(context).edit()
            .putString(
                KEY_QUOTES,
                json.encodeToString(ListSerializer(Quote.serializer()), merged.values.toList())
            )
            .putLong("ts", System.currentTimeMillis())
            .apply()
    }

    fun lastUpdated(context: Context): Long = prefs(context).getLong("ts", 0L)

    // ───────────── تاریخچه‌ی قیمت برای نمودار مینیاتوری ─────────────

    private fun loadHistory(context: Context): MutableMap<String, List<Double>> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString(historySerializer, raw)
        }.getOrDefault(emptyMap<String, List<Double>>()).toMutableMap()
    }

    /**
     * با هر موفقیتِ خواندن، یک نقطه به سری نماد اضافه می‌شود — حتی اگر قیمت تغییر
     * نکرده باشد؛ این‌طور نمودار برای بورس تهران و روزهای بسته‌ی بازار هم به‌درستی
     * و بدون وابستگی به «تغییر قیمت» به‌تدریج شکل می‌گیرد.
     */
    private fun appendHistory(context: Context, fresh: List<Quote>) {
        val good = fresh.filter { it.price != null }
        if (good.isEmpty()) return
        val hist = loadHistory(context)
        good.forEach { q ->
            val k = key(q.sourceId, q.code)
            val series = (hist[k] ?: emptyList()).toMutableList()
            series += q.price!!
            hist[k] = series.takeLast(SPARK_HISTORY_MAX)
        }
        prefs(context).edit()
            .putString(KEY_HISTORY, json.encodeToString(historySerializer, hist))
            .apply()
    }

    /**
     * اگر منبع سری آماده‌ی نمودار نداده (مثل بورس تهران یا منابع دلخواه بدون sparkPath)،
     * از تاریخچه‌ی محلی سری می‌سازد؛ سری آماده‌ی منبع همیشه اولویت دارد.
     */
    fun withLocalSpark(context: Context, quotes: List<Quote>): List<Quote> {
        val hist = loadHistory(context)
        return quotes.map { q ->
            val local = hist[key(q.sourceId, q.code)] ?: emptyList()
            if (q.spark.size >= 3 || local.size < 3) q else q.copy(spark = local)
        }
    }

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
                    ?.symbols?.take(maxOf(1, com.pulse.market.data.MAX_SYMBOLS / sourceIds.size))
                    ?: emptyList()
            }
            syms.map { sid to it }
        }
    }

    /**
     * گرفتن همه‌ی نمادهای همه‌ی ویجت‌ها با کم‌ترین درخواست ممکن:
     * هر منبع فقط یک بار (و دسته‌ای) پرسیده می‌شود؛ نتیجه در کش مشترک ادغام می‌شود.
     *
     * اگر به‌روزرسانی نمادی شکست بخورد، «آخرین مقدار سالم» نگه داشته می‌شود و
     * با علامت stale برمی‌گردد تا ویجت چراغ قرمز نشان دهد — هرگز داده پاک نمی‌شود.
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

        // کش دائمی فقط با داده‌ی سالم تازه می‌شود (+ ثبت نقطه‌ی تاریخچه برای نمودار)
        val good = fetched.filter { it.price != null }
        if (good.isNotEmpty()) {
            mergeCached(context, good)
            appendHistory(context, good)
        }
        val cached = loadCachedMap(context)
        val fresh = fetched.associateBy { key(it.sourceId, it.code) }

        // نقشه‌ی نمایش: تازه اگر آمده؛ وگرنه آخرین مقدار سالم + علامت stale (چراغ قرمز).
        // ts همان «آخرین داده‌ی سالم» می‌ماند (نه زمانِ تلاشِ ناموفق) تا ساعتِ هر ویجت
        // و چراغ‌هایش وضعیت واقعی همان ویجت را نشان دهند.
        val out = mutableMapOf<String, Quote>()
        (cached.keys + fresh.keys).forEach { k ->
            val f = fresh[k]
            val c = cached[k]
            val quote = when {
                f != null && f.price != null -> f
                c != null && c.price != null -> c.copy(stale = true)
                f != null -> f
                else -> c
            }
            if (quote != null) out[k] = quote
        }
        return out
    }
}
