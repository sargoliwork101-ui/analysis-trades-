package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * جستجوی آنلاین نماد برای منبعی که API جستجو دارد: کریپتو (CoinGecko).
 *
 * بقیه‌ی منابع (طلا و ارز TGJU، بازارهای جهانی TradingView، منابع دلخواه و…)
 * فهرست محلی دارند و در همان جستجو می‌شوند؛ این شیء برایشان null برمی‌گرداند تا
 * UI به فهرست محلی برگردد.
 * هر خطا هم null می‌شود — جستجوی محلی هیچ‌وقت به خاطر شبکه از کار نمی‌افتد.
 *
 * همه‌ی درخواست‌ها از [Http] می‌روند (کلاینت مشترک + سقف حجم پاسخ).
 */
object WebSymbolSearch {

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
                    else -> null
                }
            }.getOrNull()
        }

    /** CoinGecko: api.coingecko.com/api/v3/search?query=… → coins[] {id, name, symbol} */
    private suspend fun coingecko(sourceId: String, q: String): List<SymbolDef> {
        val body = Http.getText(
            url = "https://api.coingecko.com/api/v3/search?query=" + enc(q),
            userAgent = Http.UA_DESKTOP
        )
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

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
