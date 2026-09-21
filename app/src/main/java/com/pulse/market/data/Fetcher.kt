package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/** موتور گرفتن داده از سایت‌ها (API های JSON و صفحه‌های HTML) */
object Fetcher {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetch(source: SourceDef, symbol: SymbolDef): Quote = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val encodedSymbol = java.net.URLEncoder.encode(symbol.code, "UTF-8").replace("+", "%20")
            val url = source.urlTemplate.replace("{symbol}", encodedSymbol)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "fa,en;q=0.8")
                .header(
                    "Accept",
                    if (source.kind == FetchKind.JSON_REST) "application/json, text/plain, */*"
                    else "text/html,application/xhtml+xml,*/*"
                )
                .apply { source.headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val body: String = client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful && text.isBlank()) error("HTTP ${resp.code}")
                text
            }

            val rawPrice: Double?
            var change: Double? = null
            var spark: List<Double> = emptyList()

            when (source.kind) {
                FetchKind.JSON_REST -> {
                    val json: Any = runCatching { JSONObject(body) as Any }
                        .getOrElse { JSONArray(body) as Any }
                    val directPrice = JsonPath.readDouble(json, source.pricePath?.replace("{symbol}", symbol.code))
                    // اگر TSETMC است و قیمت مستقیم نیامد، آخرین معامله یا closingPriceInfo را بررسی کن
                    rawPrice = directPrice ?: if (source.id == "tse_tsetmc" || source.urlTemplate.contains("tsetmc.com")) {
                        JsonPath.readDouble(json, "instrumentSearch[0].pDrCotVal")
                            ?: JsonPath.readDouble(json, "closingPriceInfo.pClosing")
                            ?: JsonPath.readDouble(json, "closingPriceInfo.pDrCotVal")
                    } else null

                    change = readChange(json, source, symbol, rawPrice)
                    spark = readSpark(json, source, symbol)
                }

                FetchKind.HTML_CSS -> {
                    val doc = Jsoup.parse(body, url)
                    val selector = source.cssSelector
                    val el = if (selector.isNullOrBlank()) {
                        null
                    } else {
                        runCatching { doc.selectFirst(selector) }.getOrNull()
                            ?: runCatching { doc.select(selector).firstOrNull() }.getOrNull()
                    }
                    val raw = when {
                        el == null -> error("سلکتور در صفحه پیدا نشد")
                        source.cssAttr.isNullOrBlank() -> el.text()
                        else -> el.attr(source.cssAttr)
                    }
                    rawPrice = Num.parse(raw)
                    change = null
                }
            }

            val scaled = rawPrice?.let { it * source.scale }
            Quote(
                code = symbol.code,
                label = symbol.label,
                price = scaled,
                changePct = change,
                unit = source.unit,
                error = if (scaled == null) "داده پیدا نشد" else null,
                ts = started,
                spark = spark
            )
        } catch (t: Throwable) {
            Quote(
                code = symbol.code,
                label = symbol.label,
                error = t.message?.take(60) ?: "خطای شبکه",
                unit = source.unit,
                ts = started
            )
        }
    }

    /** خواندن سری اعداد برای نمودار مینیاتوری */
    private fun readSpark(json: Any, source: SourceDef, symbol: SymbolDef): List<Double> {
        val path = source.sparkPath?.replace("{symbol}", symbol.code) ?: return emptyList()
        val arr = JsonPath.read(json, path) as? JSONArray ?: return emptyList()
        val out = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            val d = when (v) {
                is Number -> v.toDouble()
                is String -> Num.parse(v)
                else -> null
            }
            if (d != null) { out.add(d * source.scale); if (out.size >= 48) break }
        }
        return if (out.size >= 3) out else emptyList()
    }

    /** تبدیل عدد «تغییر» سایت به درصد، بر اساس حالت انتخاب‌شده */
    private fun readChange(json: Any, source: SourceDef, symbol: SymbolDef, rawPrice: Double?): Double? {
        // برای بورس تهران (TSETMC) اگر دیتای دیروز وجود دارد، درصد تغییر رسمی روز را حساب کن
        if (source.id == "tse_tsetmc" || source.urlTemplate.contains("tsetmc.com")) {
            val pClosing = rawPrice
                ?: JsonPath.readDouble(json, "instrumentSearch[0].pClosing")
                ?: JsonPath.readDouble(json, "closingPriceInfo.pClosing")
            val pYesterday = JsonPath.readDouble(json, "instrumentSearch[0].priceYesterday")
                ?: JsonPath.readDouble(json, "closingPriceInfo.priceYesterday")
            if (pClosing != null && pYesterday != null && pYesterday > 0.0) {
                return ((pClosing - pYesterday) / pYesterday) * 100.0
            }
            val pChange = JsonPath.readDouble(json, "instrumentSearch[0].priceChange")
                ?: JsonPath.readDouble(json, "closingPriceInfo.priceChange")
            if (pChange != null && pYesterday != null && pYesterday > 0.0) {
                return (pChange / pYesterday) * 100.0
            }
        }

        if (source.changeMode == ChangeMode.NONE) return null
        val raw = JsonPath.readDouble(json, source.changePath?.replace("{symbol}", symbol.code)) ?: return null
        return when (source.changeMode) {
            ChangeMode.PERCENT -> raw
            ChangeMode.ABSOLUTE -> {
                val price = rawPrice ?: return null
                val base = (price / source.scale) - raw
                if (base == 0.0) null else (raw / base) * 100.0
            }
            ChangeMode.PREV_CLOSE -> {
                val price = rawPrice ?: return null
                if (raw == 0.0) null else ((price / source.scale - raw) / raw) * 100.0
            }
            ChangeMode.NONE -> null
        }
    }
}
