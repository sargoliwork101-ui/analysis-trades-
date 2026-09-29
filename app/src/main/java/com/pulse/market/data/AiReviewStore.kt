package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * تاریخچه‌ی تحلیل‌های هوش مصنوعی برای هر کوین — روی همین گوشی ذخیره می‌شود.
 *
 * چرا: تا این‌جا نظر AI فقط در حافظه‌ی صفحه بود و با بستن برنامه از بین می‌رفت. حالا
 * چند تحلیل اخیرِ هر کوین با تاریخ نگه داشته می‌شود تا وقتی دوباره صفحه‌ی جزئیات را باز
 * کنی، «آخرین تحلیل‌ها» را ببینی و بتوانی روند نظر AI را در طول زمان دنبال کنی.
 *
 * همه‌چیز فقط روی همین گوشی است و به هیچ سروری نمی‌رود.
 */
object AiReviewStore {

    private const val PREF = "pulse_ai_reviews"
    private const val KEY = "reviews_by_coin"
    private const val MAX_COINS = 60
    private const val MAX_PER_COIN = 8

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** یک تحلیل ذخیره‌شده با زمانِ گرفتنش. */
    @Serializable
    data class Entry(
        val at: Long,
        val review: PumpAiReviewer.Review
    )

    @Serializable
    private data class CoinLog(
        val coinId: String,
        val symbol: String = "",
        val name: String = "",
        val entries: List<Entry> = emptyList()
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    private fun readAll(context: Context): List<CoinLog> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CoinLog.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun writeAll(context: Context, logs: List<CoinLog>) {
        val bounded = logs
            .filter { it.coinId.isNotBlank() && it.entries.isNotEmpty() }
            .sortedByDescending { log -> log.entries.maxOfOrNull { it.at } ?: 0L }
            .take(MAX_COINS)
        prefs(context).edit()
            .putString(KEY, json.encodeToString(ListSerializer(CoinLog.serializer()), bounded))
            .apply()
    }

    /** تاریخچه‌ی یک کوین (تازه‌ترین اول). */
    fun history(context: Context, coinId: String): List<Entry> {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return emptyList()
        return readAll(context).firstOrNull { it.coinId == id }
            ?.entries?.sortedByDescending { it.at } ?: emptyList()
    }

    /** آخرین تحلیلِ هر کوین (برای پرکردن سریعِ نمای فهرست/جزئیات). */
    fun latestByCoin(context: Context): Map<String, Entry> =
        readAll(context).mapNotNull { log ->
            log.entries.maxByOrNull { it.at }?.let { log.coinId to it }
        }.toMap()

    /** افزودن یک تحلیل تازه به تاریخچه‌ی کوین. */
    @Synchronized
    fun add(
        context: Context,
        coinId: String,
        symbol: String,
        name: String,
        review: PumpAiReviewer.Review,
        at: Long = System.currentTimeMillis()
    ) {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return
        val entry = Entry(at = at, review = review)
        val logs = readAll(context).toMutableList()
        val idx = logs.indexOfFirst { it.coinId == id }
        if (idx >= 0) {
            val old = logs[idx]
            val entries = (listOf(entry) + old.entries)
                .sortedByDescending { it.at }
                .take(MAX_PER_COIN)
            logs[idx] = old.copy(
                symbol = symbol.ifBlank { old.symbol },
                name = name.ifBlank { old.name },
                entries = entries
            )
        } else {
            logs.add(CoinLog(coinId = id, symbol = symbol, name = name, entries = listOf(entry)))
        }
        writeAll(context, logs)
    }

    /** پاک‌کردن تاریخچه‌ی یک کوین. */
    fun clear(context: Context, coinId: String) {
        val id = coinId.trim().lowercase()
        if (id.isEmpty()) return
        writeAll(context, readAll(context).filterNot { it.coinId == id })
    }
}
