package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

/**
 * منبعِ پشتیبانِ کریپتو (Coinpaprika).
 *
 * چرا لازم است: کلِ داده‌ی کریپتو (اسکنِ پامپ، قیمتِ کیف، جزئیاتِ کوین) از CoinGecko
 * می‌آمد. API رایگانِ CoinGecko پشتِ Cloudflare است و به‌شدت ریت‌لیمیت (۴۲۹) می‌شود؛
 * در بعضی مناطق (از جمله ایران) و حتی روی خیلی از IPهای خروجیِ VPN، Cloudflare درخواست
 * را بلاک می‌کند — همان «حتی با VPN هم داده نمی‌گیرد».
 *
 * Coinpaprika یک API رایگانِ **بدونِ کلید** است که پشتِ Cloudflare/چالشِ مرورگر نیست،
 * پس معمولاً وقتی CoinGecko در دسترس نیست هم جواب می‌دهد. این ماژول فقط زمانی صدا زده
 * می‌شود که منبعِ اصلی شکست بخورد یا ناقص باشد.
 */
object CryptoFallback {

    // تیکرِ همه‌ی کوین‌ها با قیمتِ دلاری و درصدِ تغییرها؛ بدونِ کلید و بدونِ محدودیتِ منطقه‌ای سخت‌گیر.
    private const val TICKERS_URL = "https://api.coinpaprika.com/v1/tickers?quotes=USD"
    private const val TTL_MS = 60_000L

    // پاسخِ paprika چند مگابایت است؛ فقط رده‌های بالای بازار برای اسکن/قیمت لازم‌اند.
    private const val MAX_PARSE = 3000

    @Volatile
    private var cache: Pair<Long, List<Ticker>>? = null

    /** یک تیکرِ paprika به شکلِ خنثی (مستقل از ارائه‌دهنده). */
    data class Ticker(
        /** شناسه‌ی paprika مثلِ «btc-bitcoin» */
        val id: String,
        /** بخشِ بعد از خط تیره مثلِ «bitcoin» — اغلب برابرِ شناسه‌ی CoinGecko است */
        val slug: String,
        val symbol: String,
        val name: String,
        val price: Double?,
        val change1h: Double?,
        val change24h: Double?,
        val change7d: Double?,
        val change30d: Double?,
        val volume: Double?,
        val marketCap: Double?,
        val rank: Int,
        val ath: Double?,
        val athChangePct: Double?,
        val circulating: Double?,
        val total: Double?
    )

    /** فهرستِ تیکرها (با کشِ کوتاهِ ۶۰ ثانیه)؛ خطای شبکه = فهرستِ خالی. */
    suspend fun tickers(): List<Ticker> = withContext(Dispatchers.IO) {
        cache?.let { (at, list) ->
            if (list.isNotEmpty() && System.currentTimeMillis() - at < TTL_MS) return@withContext list
        }
        val list = try {
            parse(Http.getText(TICKERS_URL))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (list.isNotEmpty()) cache = System.currentTimeMillis() to list
        list
    }

    /** قیمت + تغییرِ ۲۴ ساعت + حجمِ ۲۴ ساعت برای یک شناسه. */
    data class QuotePx(val price: Double?, val change24h: Double?, val volume: Double?)

    /**
     * تطبیقِ شناسه‌های خواسته‌شده به تیکر: اول شناسه‌ی paprika، سپس slug و در نهایت نماد
     * (با اولویتِ رتبه‌ی بهتر) تا شناسه‌ی CoinGecko هم بیشترین پوشش را بگیرد.
     * کلیدِ خروجی همان شناسه‌ی خواسته‌شده است تا فراخواننده مستقیم پیدایش کند.
     */
    private suspend fun resolve(ids: Collection<String>): Map<String, Ticker> =
        resolveFrom(tickers(), ids)

    /** تطبیقِ خالص و بدونِ شبکه (برای تست‌پذیری). */
    internal fun resolveFrom(list: List<Ticker>, ids: Collection<String>): Map<String, Ticker> {
        val wanted = ids.asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (wanted.isEmpty() || list.isEmpty()) return emptyMap()

        val byId = HashMap<String, Ticker>()
        val bySlug = HashMap<String, Ticker>()
        val bySymbol = HashMap<String, Ticker>()
        for (t in list.sortedBy { if (it.rank > 0) it.rank else Int.MAX_VALUE }) {
            byId.putIfAbsent(t.id, t)
            bySlug.putIfAbsent(t.slug, t)
            bySymbol.putIfAbsent(t.symbol.lowercase(Locale.ROOT), t)
        }
        val out = HashMap<String, Ticker>()
        for (id in wanted) {
            (byId[id] ?: bySlug[id] ?: bySymbol[id])?.let { out[id] = it }
        }
        return out
    }

    /** قیمتِ دلاری برای شناسه‌های خواسته‌شده؛ کلیدِ خروجی = همان شناسه‌ی خواسته‌شده. */
    suspend fun pricesFor(ids: Collection<String>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for ((id, t) in resolve(ids)) {
            t.price?.takeIf { it.isFinite() && it > 0.0 }?.let { out[id] = it }
        }
        return out
    }

    /** قیمت/تغییر/حجم برای شناسه‌های خواسته‌شده (برای ویجت و فهرستِ قیمت‌ها). */
    suspend fun quotesFor(ids: Collection<String>): Map<String, QuotePx> {
        val out = HashMap<String, QuotePx>()
        for ((id, t) in resolve(ids)) {
            val p = t.price?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            out[id] = QuotePx(p, t.change24h, t.volume)
        }
        return out
    }

    /** یافتنِ یک تیکر با شناسه‌ی paprika، slug یا نماد. */
    suspend fun find(idOrSymbol: String): Ticker? {
        val key = idOrSymbol.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return null
        val list = tickers()
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == key }
            ?: list.firstOrNull { it.slug == key }
            ?: list.asSequence()
                .filter { it.symbol.lowercase(Locale.ROOT) == key }
                .minByOrNull { if (it.rank > 0) it.rank else Int.MAX_VALUE }
    }

    internal fun parse(body: String): List<Ticker> {
        val arr = JSONArray(body)
        val limit = minOf(arr.length(), MAX_PARSE)
        val out = ArrayList<Ticker>(limit)
        for (i in 0 until limit) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim().lowercase(Locale.ROOT)
            if (id.isEmpty()) continue
            val usd = o.optJSONObject("quotes")?.optJSONObject("USD")
            out.add(
                Ticker(
                    id = id,
                    slug = id.substringAfter('-', id),
                    symbol = o.optString("symbol").trim(),
                    name = o.optString("name").trim().ifBlank { id },
                    price = usd?.optDouble("price")?.takeIf { it.isFinite() },
                    change1h = usd?.optDouble("percent_change_1h")?.takeIf { it.isFinite() },
                    change24h = usd?.optDouble("percent_change_24h")?.takeIf { it.isFinite() },
                    change7d = usd?.optDouble("percent_change_7d")?.takeIf { it.isFinite() },
                    change30d = usd?.optDouble("percent_change_30d")?.takeIf { it.isFinite() },
                    volume = usd?.optDouble("volume_24h")?.takeIf { it.isFinite() },
                    marketCap = usd?.optDouble("market_cap")?.takeIf { it.isFinite() },
                    rank = o.optInt("rank", 0),
                    ath = usd?.optDouble("ath_price")?.takeIf { it.isFinite() },
                    athChangePct = usd?.optDouble("percent_from_price_ath")?.takeIf { it.isFinite() },
                    circulating = o.optDouble("circulating_supply").takeIf { it.isFinite() },
                    total = o.optDouble("total_supply").takeIf { it.isFinite() }
                )
            )
        }
        return out
    }
}
