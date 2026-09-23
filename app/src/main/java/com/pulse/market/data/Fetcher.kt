package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * موتور گرفتن داده از سایت‌ها.
 *
 * هر منبع با کم‌ترین تعداد درخواست ممکن خوانده می‌شود:
 *  - JSON گروهی: همه‌ی نمادها با یک HTTP (مثل CoinGecko و TGJU)
 *  - JSON تکی: هر نماد یک درخواست (منابع دلخواه بدون آدرس گروهی)
 *  - HTML: خواندن مقدار از صفحه با سلکتور CSS
 *  - TSE: بورس تهران — جستجوی نماد + قیمت پایانی (دو مرحله‌ای)
 *
 * همه‌ی درخواست‌ها از [Http] می‌روند: یک کلاینت مشترک، فقط http/https و سقف
 * حجم پاسخ (جلوگیری از OOM با پاسخ غول‌آسا).
 *
 * واحد و ضریب «هر نماد» بر «منبع» مقدم است ([SymbolDef.unit] / [SymbolDef.scale])؛
 * این‌طور یک منبع می‌تواند هم انس طلای دلاری داشته باشد هم سکه‌ی تومانی.
 */
object Fetcher {

    /** حداکثر درخواست هم‌زمان — جلوی سیل درخواست‌ها با چند ویجت/چند نماد را می‌گیرد */
    private const val MAX_PARALLEL = 4

    private val gate = Semaphore(MAX_PARALLEL)

    /** گرفتن قیمت همه‌ی نمادهای انتخاب‌شده‌ی یک منبع */
    suspend fun fetchAll(source: SourceDef, symbols: List<SymbolDef>): List<Quote> {
        if (symbols.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            when {
                source.kind == FetchKind.TSE_TSETMC -> parallel(symbols) { s -> fetchTse(source, s) }

                source.batchTemplate != null -> fetchBatch(source, symbols)

                else -> parallel(symbols) { s -> fetchOne(source, s) }
            }
        }
    }

    /** گرفتن قیمت یک نماد (برای دکمه‌ی «تست داده» و منابع دلخواه) */
    suspend fun fetch(source: SourceDef, symbol: SymbolDef): Quote =
        withContext(Dispatchers.IO) { fetchOne(source, symbol) }

    /** اجرای موازی با سقف هم‌زمانی ([MAX_PARALLEL]) */
    private suspend fun parallel(
        symbols: List<SymbolDef>,
        block: suspend (SymbolDef) -> Quote
    ): List<Quote> = coroutineScope {
        symbols.map { s -> async { gate.withPermit { block(s) } } }.awaitAll()
    }

    // ───────────────────── انواع خواندن ─────────────────────

    private suspend fun fetchOne(source: SourceDef, sym: SymbolDef): Quote = when (source.kind) {
        FetchKind.TSE_TSETMC -> fetchTse(source, sym)
        FetchKind.JSON_REST -> try {
            quoteFromJson(source, sym, parseJson(getAny(singleUrls(source, sym.code), source)))
        } catch (t: Throwable) {
            errorQuote(source, sym, t)
        }

        FetchKind.HTML_CSS -> fetchHtml(source, sym)
    }

    /** یک درخواست برای همه‌ی نمادها؛ اگر شکست خورد به فچ تکی برمی‌گردد */
    private suspend fun fetchBatch(source: SourceDef, symbols: List<SymbolDef>): List<Quote> {
        return try {
            val joined = symbols.joinToString(",") { enc(it.code) }
            val batchUrls = (listOfNotNull(source.batchTemplate) + source.urlFallbacks)
                .map { it.replace("{symbols}", joined).replace("{symbol}", joined) }
            val json = parseJson(getAny(batchUrls, source))
            symbols.map { quoteFromJson(source, it, json) }
        } catch (_: Throwable) {
            // اگر درخواست گروهی شکست خورد، تک‌تک امتحان کن
            parallel(symbols) { s -> fetchOne(source, s) }
        }
    }

    private suspend fun fetchTse(source: SourceDef, sym: SymbolDef): Quote {
        val started = System.currentTimeMillis()
        return try {
            val inst = TseService.fetchQuote(sym.code)
            val price = inst.closePrice ?: inst.lastPrice
            Quote(
                code = sym.code, sourceId = source.id,
                label = sym.label.ifBlank { inst.name },
                price = price,
                changePct = inst.changePct,
                volume = inst.volume,
                unit = unitOf(source, sym),
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
            val urls = singleUrls(source, sym.code)
            val body = getAny(urls, source)
            val doc = Jsoup.parse(body, urls.first())
            val selector = source.cssSelector
            val el = if (selector.isNullOrBlank()) {
                error("سلکتور CSS برای منبع HTML تعریف نشده")
            } else {
                runCatching { doc.selectFirst(selector) ?: doc.select(selector).firstOrNull() }.getOrNull()
                    ?: error("سلکتور در صفحه پیدا نشد")
            }
            val raw = if (source.cssAttr.isNullOrBlank()) el.text() else el.attr(source.cssAttr)
            val scaled = Num.parse(raw)?.let { it * scaleOf(source, sym) }
            Quote(
                code = sym.code, sourceId = source.id, label = sym.label, price = scaled,
                unit = unitOf(source, sym),
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
        val scale = scaleOf(source, sym)
        val rawPrice = JsonPath.readDouble(json, source.pricePath?.replace("{symbol}", sym.code))
        val change = readChange(json, source, sym, rawPrice, scale)
        val spark = JsonPath.readDoubleList(json, source.sparkPath?.replace("{symbol}", sym.code))
            .map { it * scale }
            .let { if (it.size >= 3) it.takeLast(SPARK_HISTORY_MAX) else emptyList() }
        val scaled = rawPrice?.let { it * scale }
        return Quote(
            code = sym.code, sourceId = source.id,
            label = sym.label,
            price = scaled,
            changePct = change,
            volume = readVolume(json, source, sym),
            unit = unitOf(source, sym),
            error = if (scaled == null) "قیمت در پاسخ پیدا نشد" else null,
            ts = started,
            spark = spark
        )
    }

    /** حجم معاملات/حجم ۲۴ ساعت — عدد یا آرایه‌ی میله‌ها (که جمع زده می‌شود) */
    private fun readVolume(json: Any, source: SourceDef, sym: SymbolDef): Double? {
        val path = source.volumePath?.replace("{symbol}", sym.code) ?: return null
        val scale = scaleOf(source, sym)
        JsonPath.readDouble(json, path)?.let { return it * scale }
        val list = JsonPath.readDoubleList(json, path)
        return if (list.isEmpty()) null else list.sum() * scale
    }

    /** تبدیل عدد «تغییر» سایت به درصد، بر اساس حالت انتخاب‌شده */
    private fun readChange(
        json: Any,
        source: SourceDef,
        sym: SymbolDef,
        rawPrice: Double?,
        scale: Double
    ): Double? {
        if (source.changeMode == ChangeMode.NONE) return null
        val raw = JsonPath.readDouble(json, source.changePath?.replace("{symbol}", sym.code)) ?: return null
        return when (source.changeMode) {
            ChangeMode.PERCENT -> raw
            ChangeMode.ABSOLUTE -> {
                val price = rawPrice ?: return null
                val base = (price / scale) - raw
                if (base == 0.0) null else (raw / base) * 100.0
            }

            ChangeMode.PREV_CLOSE -> {
                val price = rawPrice ?: return null
                if (raw == 0.0) null else ((price / scale - raw) / raw) * 100.0
            }

            ChangeMode.NONE -> null
        }
    }

    // ───────────────────── واحد و ضریب هر نماد ─────────────────────

    /** واحد این نماد — اول واحد خودِ نماد، بعد واحد منبع */
    fun unitOf(source: SourceDef, sym: SymbolDef): String =
        sym.unit.trim().ifBlank { source.unit }

    /** ضریب این نماد — اول ضریب خودِ نماد، بعد ضریب منبع */
    fun scaleOf(source: SourceDef, sym: SymbolDef): Double =
        sym.scale ?: source.scale

    // ───────────────────── ابزارهای HTTP ─────────────────────

    /** آدرس‌های «یک نماد»: اصلی + پشتیبان‌ها — جای {symbol} در همه پر می‌شود */
    private fun singleUrls(source: SourceDef, code: String): List<String> =
        (listOf(source.urlTemplate) + source.urlFallbacks)
            .map { it.replace("{symbol}", enc(code)) }

    /**
     * اولین آدرسی که جواب داد برمی‌گردد — آدرس اصلی و بعد پشتیبان‌ها؛
     * هر آدرس مستقل امتحان می‌شود تا محدودیت/فیلترِ یک سرور، داده را قطع نکند
     * (مثلاً TradingView در بعضی شبکه‌ها در دسترس نیست ولی TGJU هست).
     */
    private fun getAny(urls: List<String>, source: SourceDef): String {
        var last: Throwable? = null
        for (u in urls) {
            try {
                return Http.getTextBlocking(
                    url = u,
                    accept = if (source.kind == FetchKind.HTML_CSS)
                        "text/html,application/xhtml+xml,*/*"
                    else "application/json, text/plain, */*",
                    headers = source.headers,
                    maxBytes = if (source.kind == FetchKind.HTML_CSS)
                        Http.MAX_HTML_BYTES else Http.MAX_JSON_BYTES
                )
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: error("آدرسی برای خواندن تعریف نشده بود")
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
        code = sym.code, sourceId = source.id,
        label = sym.label,
        error = t.message?.take(80) ?: "خطای شبکه",
        unit = unitOf(source, sym),
        ts = started
    )
}
