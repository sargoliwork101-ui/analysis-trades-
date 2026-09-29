package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * کندل‌های قیمت برای نمودار کوین‌های پامپ.
 *
 * منبع: CoinGecko `/coins/{id}/ohlc`. بازه را کاربر انتخاب می‌کند:
 *  • ۱ روز  → کندل نیم‌ساعته (نمای ساعتی)
 *  • ۷ روز  → کندل ۴ ساعته
 *  • ۳۰ روز → کندل ۴ ساعته
 *  • ۹۰ روز → کندل روزانه
 *
 * نتیجه در حافظه‌ی همین اجرا کش می‌شود تا با هر بار باز و بسته کردن صفحه
 * درخواست تازه به CoinGecko نرود (سهمیه‌ی رایگان محدود است).
 */
object PumpOhlc {

    /** بازه‌های قابل انتخاب در UI */
    enum class Range(val days: Int, val label: String, val candleHint: String) {
        DAY(1, "۱ روز", "کندل نیم‌ساعته"),
        WEEK(7, "۷ روز", "کندل ۴ ساعته"),
        MONTH(30, "۳۰ روز", "کندل ۴ ساعته"),
        QUARTER(90, "۳ ماه", "کندل روزانه")
    }

    data class Candle(
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double
    ) {
        val bullish: Boolean get() = close >= open
    }

    private const val MAX_CANDLES = 400
    private const val TTL_MS = 5 * 60_000L

    private data class Entry(val at: Long, val candles: List<Candle>)

    private val cache = HashMap<String, Entry>()

    private fun key(coinId: String, range: Range) = "${coinId.lowercase()}_${range.days}"

    /** نتیجه‌ی کش‌شده‌ی معتبر، بدون شبکه */
    @Synchronized
    fun cached(coinId: String, range: Range, now: Long = System.currentTimeMillis()): List<Candle>? {
        val entry = cache[key(coinId, range)] ?: return null
        return if (TimePolicy.isFresh(now, entry.at, TTL_MS)) entry.candles else null
    }

    @Synchronized
    private fun put(coinId: String, range: Range, candles: List<Candle>) {
        if (cache.size > 40) cache.clear()
        cache[key(coinId, range)] = Entry(System.currentTimeMillis(), candles)
    }

    /** گرفتن کندل‌ها؛ خطای شبکه = فهرست خالی (UI به نمودار خطی ساده برمی‌گردد). */
    suspend fun load(coinId: String, range: Range): List<Candle> = withContext(Dispatchers.IO) {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return@withContext emptyList()
        cached(id, range)?.let { return@withContext it }
        val url = "https://api.coingecko.com/api/v3/coins/$id/ohlc?vs_currency=usd&days=${range.days}"
        val candles = try {
            parse(Http.getText(url))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (candles.isNotEmpty()) put(id, range, candles)
        candles
    }

    /** پاسخ CoinGecko آرایه‌ای از [زمان, open, high, low, close] است. */
    internal fun parse(body: String): List<Candle> {
        val root = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Candle>(root.length())
        for (i in 0 until root.length()) {
            val row = root.optJSONArray(i) ?: continue
            if (row.length() < 5) continue
            val time = row.optLong(0, 0L)
            val open = row.optDouble(1)
            val high = row.optDouble(2)
            val low = row.optDouble(3)
            val close = row.optDouble(4)
            if (time <= 0L) continue
            if (!open.isFinite() || !high.isFinite() || !low.isFinite() || !close.isFinite()) continue
            if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) continue
            out += Candle(
                time = time,
                open = open,
                high = maxOf(high, open, close),
                low = minOf(low, open, close),
                close = close
            )
        }
        return out.takeLast(MAX_CANDLES)
    }

    /** قیمت‌های پایانی — ورودی ایچیموکو */
    fun closes(candles: List<Candle>): List<Double> = candles.map { it.close }

    // ───────────── کندلِ ساختگی از market_chart (پشتیبان endpointِ ohlc) ─────────────
    // چون همه‌ی نمودارها باید شمعی باشند، اگر endpointِ ohlc برای بازه‌ای داده ندهد
    // (سهمیه‌ی رایگان/خطای شبکه)، سریِ قیمتِ *همان بازه* از market_chart گرفته و به کندل
    // تبدیل می‌شود (هر سطل: open=اولین، high=بیشینه، low=کمینه، close=آخرین). این‌طوری هیچ
    // نمودار خطی‌ای نمی‌ماند و همه‌ی بازه‌ها داده‌ی خودشان را شمعی نشان می‌دهند.

    /** تعداد کندلِ هدف هنگام ساختن کندل از سریِ قیمت */
    private const val SYNTH_CANDLES = 90

    private val synthCache = HashMap<String, Entry>()

    @Synchronized
    private fun cachedSynth(coinId: String, range: Range, now: Long = System.currentTimeMillis()): List<Candle>? {
        val entry = synthCache[key(coinId, range)] ?: return null
        return if (TimePolicy.isFresh(now, entry.at, TTL_MS)) entry.candles else null
    }

    @Synchronized
    private fun putSynth(coinId: String, range: Range, candles: List<Candle>) {
        if (synthCache.size > 40) synthCache.clear()
        synthCache[key(coinId, range)] = Entry(System.currentTimeMillis(), candles)
    }

    /** کندلِ ساختگیِ یک بازه از market_chart؛ خطای شبکه = فهرست خالی. */
    suspend fun loadSynthetic(coinId: String, range: Range): List<Candle> = withContext(Dispatchers.IO) {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return@withContext emptyList()
        cachedSynth(id, range)?.let { return@withContext it }
        val url = "https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=usd&days=${range.days}"
        val candles = try {
            synthesizeCandles(parsePricePoints(Http.getText(url)), SYNTH_CANDLES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (candles.isNotEmpty()) putSynth(id, range, candles)
        candles
    }

    /** پاسخ market_chart: {"prices":[[ts, price], ...]} → فهرست (زمان، قیمت). */
    internal fun parsePricePoints(body: String): List<Pair<Long, Double>> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("prices") ?: return emptyList()
        val out = ArrayList<Pair<Long, Double>>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            if (row.length() < 2) continue
            val ts = row.optLong(0, 0L)
            val price = row.optDouble(1)
            if (price.isFinite() && price > 0.0) out += ts to price
        }
        return out
    }

    /**
     * تبدیل سریِ (زمان، قیمت) به کندل با سطل‌بندیِ یکنواخت:
     * open=اولین قیمتِ سطل، close=آخرین، high=بیشینه، low=کمینه.
     */
    internal fun synthesizeCandles(points: List<Pair<Long, Double>>, targetCount: Int): List<Candle> {
        if (points.size < 2) return emptyList()
        val target = targetCount.coerceIn(2, MAX_CANDLES)
        val bucketSize = kotlin.math.ceil(points.size.toDouble() / target).toInt().coerceAtLeast(1)
        val out = ArrayList<Candle>(target + 1)
        var i = 0
        while (i < points.size) {
            val end = minOf(i + bucketSize, points.size)
            val slice = points.subList(i, end)
            val open = slice.first().second
            val close = slice.last().second
            val high = slice.maxOf { it.second }
            val low = slice.minOf { it.second }
            out += Candle(
                time = slice.first().first,
                open = open,
                high = maxOf(high, open, close),
                low = minOf(low, open, close),
                close = close
            )
            i = end
        }
        return out
    }

    /**
     * تبدیل یک سریِ سادهٔ قیمت (بدون زمان — مثل اسپارک‌لاینِ کش‌شده) به کندل، برای وقتی که
     * شبکه در دسترس نیست و فقط همین سری موجود است. زمان = اندیسِ سطل.
     */
    fun candlesFromValues(values: List<Double>): List<Candle> {
        val clean = values.filter { it.isFinite() && it > 0.0 }
        if (clean.size < 2) return emptyList()
        val points = clean.mapIndexed { i, v -> i.toLong() to v }
        return synthesizeCandles(points, SYNTH_CANDLES)
    }
}
