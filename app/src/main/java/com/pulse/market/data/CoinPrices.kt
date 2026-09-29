package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * قیمت لحظه‌ای چند کوین دلخواه (بر اساس شناسه‌ی CoinGecko).
 *
 * اسکن پامپ فقط کوین‌های برتر بازار را می‌آورد؛ برای «معامله‌های من» باید قیمت
 * دقیقاً همان کوین‌هایی که کاربر خریده گرفته شود، حتی اگر دیگر در فهرست پامپ نباشند.
 */
object CoinPrices {

    private const val ENDPOINT = "https://api.coingecko.com/api/v3/simple/price"
    private const val MAX_IDS = 60

    /** شناسه‌های CoinGecko فقط حروف کوچک، عدد و خط تیره دارند. */
    internal fun sanitizeIds(ids: Collection<String>): List<String> = ids.asSequence()
        .map { it.trim().lowercase(Locale.ROOT).filter { ch -> ch.isLetterOrDigit() || ch == '-' } }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_IDS)
        .toList()

    /** نگاشت شناسه به قیمت دلاری؛ خطای شبکه = نقشه‌ی خالی. */
    suspend fun fetch(ids: Collection<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        val safe = sanitizeIds(ids)
        if (safe.isEmpty()) return@withContext emptyMap()
        val url = "$ENDPOINT?ids=${safe.joinToString(",")}&vs_currencies=usd"
        val primary = try {
            parse(Http.getText(url))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyMap()
        }
        // اگر CoinGecko همه را داد، تمام. وگرنه شناسه‌های جامانده را از منبعِ پشتیبان
        // (Coinpaprika) تکمیل کن تا قیمتِ کیف حتی وقتی CoinGecko بلاک است هم بیاید.
        val missing = safe.filterNot { it in primary }
        if (missing.isEmpty()) return@withContext primary
        val fallback = runCatching { CryptoFallback.pricesFor(missing) }.getOrNull().orEmpty()
        if (fallback.isEmpty()) primary else primary + fallback
    }

    /** پاسخ: {"solana":{"usd":150.2}, ...} */
    internal fun parse(body: String): Map<String, Double> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Double>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val price = root.optJSONObject(id)?.optDouble("usd")
            if (price != null && price.isFinite() && price > 0.0) out[id.lowercase(Locale.ROOT)] = price
        }
        return out
    }
}
