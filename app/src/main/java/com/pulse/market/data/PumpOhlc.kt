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

    // ───────────── نمودار خطیِ هم‌بازه (پشتیبان کندل) ─────────────
    // اگر endpointِ کندل برای بازه‌ای داده ندهد (سهمیه/خطای شبکه)، به‌جای نمایشِ
    // اسپارک‌لاینِ همیشه‌۷روزه (که باعث می‌شد نمودار ۳۰ روزه و ۷ روزه یکی به‌نظر برسند)،
    // خطِ قیمتِ *همان بازه* از market_chart گرفته می‌شود تا همه‌ی بازه‌ها هم‌خوان باشند.

    private const val LINE_MAX_POINTS = 180

    private data class LineEntry(val at: Long, val values: List<Double>)

    private val lineCache = HashMap<String, LineEntry>()

    @Synchronized
    private fun cachedLine(coinId: String, range: Range, now: Long = System.currentTimeMillis()): List<Double>? {
        val entry = lineCache[key(coinId, range)] ?: return null
        return if (TimePolicy.isFresh(now, entry.at, TTL_MS)) entry.values else null
    }

    @Synchronized
    private fun putLine(coinId: String, range: Range, values: List<Double>) {
        if (lineCache.size > 40) lineCache.clear()
        lineCache[key(coinId, range)] = LineEntry(System.currentTimeMillis(), values)
    }

    /** خطِ قیمتِ یک بازه از market_chart؛ خطای شبکه = فهرست خالی. */
    suspend fun loadLine(coinId: String, range: Range): List<Double> = withContext(Dispatchers.IO) {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return@withContext emptyList()
        cachedLine(id, range)?.let { return@withContext it }
        val url = "https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=usd&days=${range.days}"
        val values = try {
            parseLine(Http.getText(url))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (values.isNotEmpty()) putLine(id, range, values)
        values
    }

    /** پاسخ market_chart: {"prices":[[ts, price], ...]} */
    internal fun parseLine(body: String): List<Double> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("prices") ?: return emptyList()
        val out = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            if (row.length() < 2) continue
            val price = row.optDouble(1)
            if (price.isFinite() && price > 0.0) out += price
        }
        return downsample(out, LINE_MAX_POINTS)
    }

    /** کاهش تعداد نقطه‌ها به سقفِ معلوم، با فاصله‌ی یکنواخت (حفظِ نقطه‌ی آخر). */
    internal fun downsample(values: List<Double>, maxPoints: Int): List<Double> {
        if (maxPoints < 2 || values.size <= maxPoints) return values
        val step = (values.size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { values[(it * step).toInt().coerceIn(0, values.size - 1)] }
    }
}
