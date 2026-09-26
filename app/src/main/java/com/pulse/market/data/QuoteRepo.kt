package com.pulse.market.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
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
 *
 * ── بهینه‌سازی کارایی (نسخه‌ی ۱٫۱۴) ──
 * قبلاً برای هر رفرش، JSON کامل کش و JSON کامل تاریخچه چند بار از SharedPreferences
 * خوانده و دوباره نوشته می‌شد (Open/Parse/Serialize روی رشته‌ی چند صد کیلوبایتی،
 * در هر ۱۵ ثانیه). حالا:
 *  • کش و تاریخچه یک‌بار در حافظه می‌مانند ([memQuotes]/[memHistory]) و فقط اگر
 *    نبود از دیسک خوانده می‌شوند؛
 *  • نوشتن روی دیسک فقط وقتی انجام می‌شود که داده‌ی تازه‌ای آمده باشد و هر دو کلید
 *    در **یک** تراکنش نوشته می‌شوند (قبلاً دو نوشتن جدا بود).
 */
object QuoteRepo {

    private const val PREF = "pulse_cache"
    private const val KEY_QUOTES = "quotes"
    private const val KEY_HISTORY = "price_history"
    private const val KEY_TS = "ts"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val historySerializer =
        MapSerializer(String.serializer(), ListSerializer(Double.serializer()))

    /** کش در حافظه — منبع اصلی خواندن در طول عمر پروسه */
    @Volatile
    private var memQuotes: Map<String, Quote>? = null

    /** یک‌پارچه نگه داشتن fetch/merge/history در برابر رفرش‌های هم‌زمان. */
    private val refreshMutex = Mutex()

    /** تاریخچه‌ی قیمت در حافظه — نگاشت «منبع|نماد» به سری اعداد */
    @Volatile
    private var memHistory: Map<String, List<Double>>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** کلید یکتای هر نماد: منبع + کد */
    fun key(sourceId: String, code: String) = "$sourceId|$code"

    // ───────────── کش ─────────────

    @Synchronized
    fun loadCachedMap(context: Context): Map<String, Quote> {
        memQuotes?.let { return it }
        val raw = prefs(context).getString(KEY_QUOTES, null)
        val list = if (raw == null) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(Quote.serializer()), raw)
        }.getOrDefault(emptyList())
        val map = list.associateBy { key(it.sourceId, it.code) }
        memQuotes = map
        return map
    }

    /**
     * نوشتن کش و تاریخچه در یک تراکنش.
     * فقط داده‌ی سالم (با قیمت) وارد کش می‌شود تا با خطا، مقدار قبلی از بین نرود.
     */
    @Synchronized
    private fun persist(context: Context, healthy: List<Quote>, history: Map<String, List<Double>>) {
        val merged = loadCachedMap(context).toMutableMap()
        healthy.forEach { merged[key(it.sourceId, it.code)] = it }
        memQuotes = merged
        memHistory = history
        prefs(context).edit()
            .putString(KEY_QUOTES, json.encodeToString(ListSerializer(Quote.serializer()), merged.values.toList()))
            .putString(KEY_HISTORY, json.encodeToString(historySerializer, history))
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    fun lastUpdated(context: Context): Long = prefs(context).getLong(KEY_TS, 0L)

    // ───────────── تاریخچه‌ی قیمت برای نمودار مینیاتوری ─────────────

    @Synchronized
    private fun loadHistory(context: Context): Map<String, List<Double>> {
        memHistory?.let { return it }
        val raw = prefs(context).getString(KEY_HISTORY, null)
        val map = if (raw == null) emptyMap() else runCatching {
            json.decodeFromString(historySerializer, raw)
        }.getOrDefault(emptyMap())
        memHistory = map
        return map
    }

    /**
     * با هر موفقیتِ خواندن، یک نقطه به سری نماد اضافه می‌شود — حتی اگر قیمت تغییر
     * نکرده باشد؛ این‌طور نمودار برای بورس تهران و روزهای بسته‌ی بازار هم به‌درستی
     * و بدون وابستگی به «تغییر قیمت» به‌تدریج شکل می‌گیرد.
     */
    private fun appendHistory(context: Context, fresh: List<Quote>): Map<String, List<Double>> {
        val good = fresh.filter { it.price != null }
        if (good.isEmpty()) return loadHistory(context)
        val hist = loadHistory(context).toMutableMap()
        good.forEach { q ->
            val k = key(q.sourceId, q.code)
            val series = (hist[k] ?: emptyList()).toMutableList()
            series += q.price!!
            hist[k] = series.takeLast(SPARK_HISTORY_MAX)
        }
        return hist
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
        val hasExplicitSelection = cfg.symbols.isNotEmpty()
        return sourceIds.flatMap { sid ->
            val selected = cfg.symbolsOf(sid)
            val syms = if (selected.isNotEmpty() || hasExplicitSelection) {
                selected
            } else {
                // فقط پیکربندی کاملاً خالی (نصب/ویجت تازه) پیش‌فرض نشان می‌دهد؛
                // منبعی که کاربر عمداً بدون نماد گذاشته نباید نماد مخفی نمایش دهد.
                ConfigStore.resolveSource(context, sid)
                    ?.symbols?.take(maxOf(1, MAX_SYMBOLS / sourceIds.size))
                    ?: emptyList()
            }
            syms.map { sid to it }
        }.distinctBy { it.first to it.second.code }
            .take(MAX_SYMBOLS)
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
        refreshMutex.lock()
        return try {
            refreshManyLocked(context.applicationContext, wantedPerWidget)
        } finally {
            refreshMutex.unlock()
        }
    }

    private suspend fun refreshManyLocked(
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
            persist(context, good, appendHistory(context, good))
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
        // وضعیت stale در حافظه بماند؛ وگرنه رندر بعدی که شبکه نمی‌رفت، همان
        // مقدار شکست‌خورده را دوباره سبز نشان می‌داد. Quote خطادارِ بدون قیمت
        // همچنان وارد کش دائمی نمی‌شود.
        synchronized(this) {
            val current = (memQuotes ?: cached).toMutableMap()
            out.forEach { (k, quote) ->
                if (quote.price != null) current[k] = quote
            }
            memQuotes = current
        }
        return out
    }
}
