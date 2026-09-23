package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * جستجوی آنلاین نماد برای منبعی که API جستجو دارد:
 * کریپتو (CoinGecko) و سهام آمریکا (Yahoo).
 *
 * بقیه‌ی منابع (طلا و ارز، منابع دلخواه و…) فهرست محلی دارند و در همان
 * جستجو می‌شوند؛ این شیء برایشان null برمی‌گرداند تا UI به فهرست محلی برگردد.
 * هر خطا هم null می‌شود — جستجوی محلی هیچ‌وقت به خاطر شبکه از کار نمی‌افتد.
 */
object WebSymbolSearch {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * جستجو در منبع — null یعنی این منبع جستجوی آنلاین ندارد
     * (یا شبکه شکست خورد) و باید از فهرست محلی استفاده شود.
     * بازارِ منبع از ماژول مرجع MarketKind پرسیده می‌شود — نه با id رشته‌ای.
     */
    suspend fun search(sourceId: String, query: String): List<SymbolDef>? =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext null
            runCatching {
                when (marketKindOf(sourceId)) {
                    MarketKind.CRYPTO -> coingecko(sourceId, q)
                    MarketKind.US -> yahoo(sourceId, q)
                    else -> null
                }
            }.getOrNull()
        }

    /** CoinGecko: api.coingecko.com/api/v3/search?query=… → coins[] {id, name, symbol} */
    private fun coingecko(sourceId: String, q: String): List<SymbolDef> {
        val body = get("https://api.coingecko.com/api/v3/search?query=" + enc(q))
        val arr = JSONObject(body).optJSONArray("coins") ?: return emptyList()
        val out = mutableListOf<SymbolDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            out += SymbolDef(id, o.optString("name").ifBlank { id }, sourceId)
        }
        return out.take(15)
    }

    /** Yahoo: query1.finance.yahoo.com/v1/finance/search?q=… → quotes[] {symbol, shortname, quoteType} */
    private fun yahoo(sourceId: String, q: String): List<SymbolDef> {
        val body = get(
            "https://query1.finance.yahoo.com/v1/finance/search?q=" + enc(q) +
                    "&quotesCount=15&newsCount=0"
        )
        val arr = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
        // فقط ابزارهای واقعی — گزینه‌های اختیار و پیچیده را نمایش نده
        val allowed = setOf("EQUITY", "ETF", "CRYPTOCURRENCY", "INDEX", "CURRENCY")
        val out = mutableListOf<SymbolDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val symbol = o.optString("symbol").trim()
            if (symbol.isEmpty()) continue
            if (o.optString("quoteType") !in allowed) continue
            out += SymbolDef(symbol, o.optString("shortname").ifBlank { symbol }, sourceId)
        }
        return out.take(15)
    }

    private fun get(url: String): String =
        client.newCall(
            Request.Builder().url(url).header("User-Agent", UA).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
