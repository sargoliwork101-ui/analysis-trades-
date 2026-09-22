package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * موتور گرفتن داده از سایت‌ها.
 *
 * هر منبع با کم‌ترین تعداد درخواست ممکن خوانده می‌شود:
 *  - JSON گروهی: همه‌ی نمادها با یک HTTP (مثل CoinGecko و آینه‌ی طلا/ارز)
 *  - JSON تکی: هر نماد یک درخواست (مثل Yahoo)
 *  - HTML: خواندن مقدار از صفحه با سلکتور CSS
 *  - TSE: بورس تهران — جستجوی نماد + قیمت پایانی (دو مرحله‌ای)
 */
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

    /** گرفتن قیمت همه‌ی نمادهای انتخاب‌شده‌ی یک منبع */
    suspend fun fetchAll(source: SourceDef, symbols: List<SymbolDef>): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            when {
                source.kind == FetchKind.TSE_TSETMC ->
                    coroutineScope { symbols.map { s -> async { fetchTse(source, s) } }.awaitAll() }

                source.batchTemplate != null -> fetchBatch(source, symbols)

                else ->
                    coroutineScope { symbols.map { s -> async { fetchOne(source, s) } }.awaitAll() }
            }
        }
    }

    /** گرفتن قیمت یک نماد (برای دکمه‌ی «تست داده» و منابع دلخواه) */
    suspend fun fetch(source: SourceDef, symbol: SymbolDef): Quote =
        withContext(Dispatchers.IO) { fetchOne(source, symbol) }

    // ───────────────────── انواع خواندن ─────────────────────

    private suspend fun fetchOne(source: SourceDef, sym: SymbolDef): Quote = when (source.kind) {
        FetchKind.TSE_TSETMC -> fetchTse(source, sym)
        FetchKind.JSON_REST -> try {
            quoteFromJson(source, sym, parseJson(get(url(source, sym.code), source)))
        } catch (t: Throwable) {
            errorQuote(source, sym, t)
        }

        FetchKind.HTML_CSS -> fetchHtml(source, sym)
    }

    /** یک درخواست برای همه‌ی نمادها؛ اگر شکست خورد به فچ تکی برمی‌گردد */
    private suspend fun fetchBatch(source: SourceDef, symbols: List<SymbolDef>): List<Quote> {
        return try {
            val joined = symbols.joinToString(",") { enc(it.code) }
            val batchUrl = source.batchTemplate!!.replace("{symbols}", joined)
            val json = parseJson(get(batchUrl, source))
            symbols.map { quoteFromJson(source, it, json) }
        } catch (_: Throwable) {
            // اگر درخواست گروهی شکست خورد، تک‌تک امتحان کن
            coroutineScope { symbols.map { s -> async { fetchOne(source, s) } }.awaitAll() }
        }
    }

    private suspend fun fetchTse(source: SourceDef, sym: SymbolDef): Quote {
        val started = System.currentTimeMillis()
        return try {
            val inst = TseService.fetchQuote(sym.code)
            val price = inst.closePrice ?: inst.lastPrice
            Quote(
                code = sym.code,
                label = sym.label.ifBlank { inst.name },
                price = price,
                changePct = inst.changePct,
                unit = source.unit,
                error = if (price == null) "قیمت پیدا نشد" else null,
                ts = started
            )
        } catch (t: Throwable) {
            errorQuote(source, sym, t, started)
        }
    }

    private fun fetchHtml(source: SourceDef, sym: SymbolDef): Quote {
        val started = System.currentTimeMillis()
        return try {
            val body = get(url(source, sym.code), source)
            val doc = Jsoup.parse(body, url(source, sym.code))
            val selector = source.cssSelector
            val el = if (selector.isNullOrBlank()) null
            else runCatching { doc.selectFirst(selector) ?: doc.select(selector).firstOrNull() }.getOrNull()
                ?: error("سلکتور در صفحه پیدا نشد")
            val raw = when {
                source.cssAttr.isNullOrBlank() -> el.text()
                else -> el.attr(source.cssAttr)
            }
            val scaled = Num.parse(raw)?.let { it * source.scale }
            Quote(
                code = sym.code, label = sym.label, price = scaled, unit = source.unit,
                error = if (scaled == null) "«$raw» عدد نبود" else null,
                ts = started
            )
        } catch (t: Throwable) {
            errorQuote(source, sym, t, started)
        }
    }

    // ───────────────────── ساخت Quote از JSON ─────────────────────

    private fun quoteFromJson(source: SourceDef, sym: SymbolDef, json: Any): Quote {
        val started = System.currentTimeMillis()
        val rawPrice = JsonPath.readDouble(json, source.pricePath?.replace("{symbol}", sym.code))
        val change = readChange(json, source, sym, rawPrice)
        val spark = JsonPath.readDoubleList(json, source.sparkPath?.replace("{symbol}", sym.code))
            .map { it * source.scale }
            .let { if (it.size >= 3) it.takeLast(48) else emptyList() }
        val scaled = rawPrice?.let { it * source.scale }
        return Quote(
            code = sym.code,
            label = sym.label,
            price = scaled,
            changePct = change,
            unit = source.unit,
            error = if (scaled == null) "قیمت در پاسخ پیدا نشد" else null,
            ts = started,
            spark = spark
        )
    }

    /** تبدیل عدد «تغییر» سایت به درصد، بر اساس حالت انتخاب‌شده */
    private fun readChange(json: Any, source: SourceDef, sym: SymbolDef, rawPrice: Double?): Double? {
        if (source.changeMode == ChangeMode.NONE) return null
        val raw = JsonPath.readDouble(json, source.changePath?.replace("{symbol}", sym.code)) ?: return null
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

    // ───────────────────── ابزارهای HTTP ─────────────────────

    private fun url(source: SourceDef, code: String): String =
        source.urlTemplate.replace("{symbol}", enc(code))

    private fun get(url: String, source: SourceDef): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "fa,en;q=0.8")
            .header(
                "Accept",
                if (source.kind == FetchKind.HTML_CSS) "text/html,application/xhtml+xml,*/*"
                else "application/json, text/plain, */*"
            )
            .apply { source.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code}" + if (text.isNotBlank()) " — ${text.trim().take(60)}" else "")
            }
            text
        }
    }

    private fun parseJson(body: String): Any =
        runCatching { JSONObject(body) as Any }.getOrElse { JSONArray(body) as Any }

    private fun enc(code: String): String =
        URLEncoder.encode(code, "UTF-8").replace("+", "%20")

    private fun errorQuote(
        source: SourceDef,
        sym: SymbolDef,
        t: Throwable,
        started: Long = System.currentTimeMillis()
    ): Quote = Quote(
        code = sym.code,
        label = sym.label,
        error = t.message?.take(80) ?: "خطای شبکه",
        unit = source.unit,
        ts = started
    )
}
