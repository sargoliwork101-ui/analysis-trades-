package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * مدل یک نماد در بورس تهران (TSETMC)
 */
data class TseInstrument(
    val insCode: String,
    val symbol: String,
    val name: String,
    val lastPrice: Double? = null,
    val closePrice: Double? = null,
    val changePct: Double? = null,
    /** حجم معاملات امروز (تعداد سهم) */
    val volume: Double? = null,
    val cIsin: String = ""
)

/**
 * پارسر هوشمند لینک‌ها و شناسه‌های TSETMC
 */
object TseUrlParser {

    private val INS_CODE_PATTERN = Pattern.compile("(?:i=|/instInfo/|/Instrument/|^)(\\d{15,20})")
    private val ISIN_PATTERN = Pattern.compile("(?:/instInfo/|^)(IRO[0-9A-Z]{9})", Pattern.CASE_INSENSITIVE)

    /**
     * استخراج کد شناسه (insCode یا ISIN یا کلمه جستجو) از متن یا لینک ورودی کاربر
     */
    fun parse(input: String): ParsedTseInput {
        val trimmed = input.trim().take(2048)

        // ۱. آیا لینک TSETMC قدیمی است؟ loader.aspx?ParTree=...&i=46348633615832441
        val insMatcher = INS_CODE_PATTERN.matcher(trimmed)
        if (insMatcher.find()) {
            val code = insMatcher.group(1)
            if (code != null && code.length >= 15) {
                return ParsedTseInput(insCode = code, rawQuery = code, isUrl = true)
            }
        }

        // ۲. آیا ISIN است؟ IRO1FOLD0001
        val isinMatcher = ISIN_PATTERN.matcher(trimmed)
        if (isinMatcher.find()) {
            val isin = isinMatcher.group(1)
            if (isin != null) {
                return ParsedTseInput(isin = isin.uppercase(), rawQuery = isin, isUrl = true)
            }
        }

        // ۳. اگر URL دیگری از TSETMC یا سایت‌های مالی است
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val lastSegment = trimmed.substringAfterLast("/").substringBefore("?").trim()
            if (lastSegment.isNotEmpty()) {
                return ParsedTseInput(rawQuery = lastSegment, isUrl = true)
            }
        }

        // ۴. جستجوی متنی معمولی (مثلاً «اهرم»، «فولاد»، «عیار»)
        return ParsedTseInput(rawQuery = trimmed, isUrl = false)
    }
}

data class ParsedTseInput(
    val insCode: String? = null,
    val isin: String? = null,
    val rawQuery: String = "",
    val isUrl: Boolean = false
)

/**
 * موتور خدمات بورس تهران:
 * ۱. کاتالوگ غنی نمادهای برتر و پرطرفدار (آفلاین و تضمینی)
 * ۲. جستجوی آنلاین زنده از TSETMC
 * ۳. واکشی اطلاعات نماد از طریق لینک یا insCode
 */
object TseService {

    /**
     * خواندن یک آدرس TSETMC — از کلاینت مشترک [Http] (استخر اتصال/سقف حجم/فقط https).
     * null یعنی پاسخ نگرفتیم یا نامعتبر بود؛ همه‌ی صداکننده‌ها همین را هندل می‌کنند.
     */
    private fun getBody(url: String): String? = runCatching {
        Http.execute(Request.Builder().url(url).header("User-Agent", Http.UA_DESKTOP).build())
    }.getOrNull()

    /**
     * کاتالوگ جامع داخلی نمادهای پرمعامله و بسیار محبوب بورس تهران
     * (صندوق‌های طلا، صندوق‌های اهرمی، غول‌های شاخص‌ساز و بازار)
     */
    val POPULAR_INSTRUMENTS: List<TseInstrument> = listOf(
        // ─── صندوق‌های طلا ───
        TseInstrument("35700347742832443", "طلا", "صندوق پشتوانه طلای لوتوس"),
        TseInstrument("50091851210452391", "عیار", "صندوق طلای عیار مفید"),
        TseInstrument("65883838195688438", "کهربا", "صندوق طلای کهربا"),
        TseInstrument("43825828773950621", "زرفام", "صندوق طلای زرفام آشنا"),
        TseInstrument("28320490710609349", "زر", "صندوق طلای زرین آگاه"),
        TseInstrument("20875829988225508", "گوهر", "صندوق طلای گوهر نفیس"),
        TseInstrument("17939882949168449", "ناب", "صندوق طلای ناب"),

        // ─── صندوق‌های اهرمی و شاخصی ───
        TseInstrument("20986986422891963", "اهرم", "صندوق س. اهرمی کاریزما"),
        TseInstrument("43665798993213017", "شتاب", "صندوق س. اهرمی شتاب"),
        TseInstrument("71900138933098331", "موج", "صندوق س. اهرمی موج فیروزه"),
        TseInstrument("26685472851554522", "جهش", "صندوق س. اهرمی جهش فارابی"),
        TseInstrument("52458444983281249", "توان", "صندوق س. اهرمی توان مفید"),
        TseInstrument("41699948011270271", "پالایش", "صندوق پالایشی یکم"),
        TseInstrument("65089307994467000", "دارا یکم", "صندوق واسطه‌گری مالی یکم"),

        // ─── صندوق‌های درآمد ثابت ───
        TseInstrument("", "کیان", "صندوق درآمد ثابت کیان"),
        TseInstrument("", "آرمان", "صندوق درآمد ثابت آرمان"),
        TseInstrument("", "همای", "صندوق درآمد ثابت همای"),
        TseInstrument("", "ثبات", "صندوق درآمد ثابت ثبات"),
        TseInstrument("", "پارند", "صندوق درآمد ثابت پارند"),
        TseInstrument("", "سپاس", "صندوق درآمد ثابت سپاس"),
        TseInstrument("", "کمند", "صندوق درآمد ثابت کمند"),
        TseInstrument("", "اعتماد", "صندوق درآمد ثابت اعتماد"),

        // ─── سهام شاخص‌ساز و پرمعامله ───
        TseInstrument("46348633615832441", "فولاد", "فولاد مبارکه اصفهان"),
        TseInstrument("35425587644337450", "فملی", "ملی صنایع مس ایران"),
        TseInstrument("65863428195688438", "خودرو", "ایران خودرو"),
        TseInstrument("2400322364771558", "خساپا", "سایپا"),
        TseInstrument("26014918613481444", "خگستر", "گسترش س. ایران خودرو"),
        TseInstrument("44891419799292819", "شپنا", "پالایش نفت اصفهان"),
        TseInstrument("35366681030756042", "شتران", "پالایش نفت تهران"),
        TseInstrument("58448530324888258", "شبندر", "پالایش نفت بندرعباس"),
        TseInstrument("24694908985160541", "شستا", "سرمایه‌گذاری تأمین اجتماعی"),
        TseInstrument("70045951863250232", "وبملت", "بانک ملت"),
        TseInstrument("63916262824858022", "وتجارت", "بانک تجارت"),
        TseInstrument("17411649622941544", "وبصادر", "بانک صادرات ایران"),
        TseInstrument("43810459526827081", "دی", "بانک دی"),
        TseInstrument("42562479630329431", "فارس", "صنایع پتروشیمی خلیج فارس"),
        TseInstrument("32183258189849504", "تاپیکو", "س. نفت و گاز و پتروشیمی تأمین"),
        TseInstrument("35218206405971184", "وغدیر", "سرمایه‌گذاری غدیر"),
        TseInstrument("22566083119194236", "پترول", "گروه پتروشیمی س. ایرانیان"),
        TseInstrument("58823909772591605", "نوری", "پتروشیمی نوری"),
        TseInstrument("31289124407332219", "بوعلی", "پتروشیمی بوعلی سینا"),
        TseInstrument("47898583489823491", "آریا", "پلیمر آریا ساسول"),
        TseInstrument("35002166946654128", "زاگرس", "پتروشیمی زاگرس"),
        TseInstrument("33832168407421833", "شپدیس", "پتروشیمی پردیس"),
        TseInstrument("63851502422031760", "کگل", "معدنی و صنعتی گل‌گهر"),
        TseInstrument("11414457319985991", "کچاد", "معدنی و صنعتی چادرملو"),
        TseInstrument("37803299724212702", "ذوب", "ذوب‌آهن اصفهان"),
        TseInstrument("67484392478392180", "صبا", "گروه مالی صبا تأمین"),
        TseInstrument("39185207431289419", "خبهمن", "گروه بهمن"),
        TseInstrument("69103099839441128", "وپاسار", "بانک پاسارگاد")
    )

    /**
     * جستجوی هوشمند نماد بر اساس ورودی کاربر (نام، نماد، کد، یا لینک کامل TSETMC)
     */
    suspend fun search(rawInput: String): List<TseInstrument> = withContext(Dispatchers.IO) {
        val parsed = TseUrlParser.parse(rawInput)
        val query = parsed.rawQuery.trim().take(200)
        if (query.isBlank() && parsed.insCode == null) return@withContext emptyList()

        val results = mutableListOf<TseInstrument>()

        // ۱. اگر لینک یا insCode بود، مستقیم مشخصات آن نماد را دریافت کن
        if (parsed.insCode != null) {
            val byIns = fetchByInsCode(parsed.insCode)
            if (byIns != null) {
                results.add(byIns)
                return@withContext results
            }
        }

        // ۲. فیلتر سریع در دیتابیس آماده‌ی پرمعامله‌ها
        val localMatches = POPULAR_INSTRUMENTS.filter {
            it.symbol.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.insCode == query
        }
        results.addAll(localMatches)

        // ۳. جستجوی آنلاین در TSETMC
        runCatching {
            val onlineMatches = searchTseOnline(query)
            for (om in onlineMatches) {
                if (results.none { it.insCode == om.insCode || it.symbol == om.symbol }) {
                    results.add(om)
                }
            }
        }

        results
    }

    /**
     * جستجوی آنلاین از طریق API جستجوی رسمی TSETMC
     */
    private fun searchTseOnline(query: String): List<TseInstrument> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/$encoded"

        val body = getBody(url) ?: return emptyList()
        if (body.isBlank()) return emptyList()
        return parseSearchResponse(body)
    }

    /**
     * واکشی اطلاعات یک نماد با insCode
     */
    fun fetchByInsCode(insCode: String): TseInstrument? {
        // ابتدا اطلاعات بسته/آخرین قیمت را می‌خوانیم
        val priceUrl = "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/$insCode"

        var closePrice: Double? = null
        var lastPrice: Double? = null
        var changePct: Double? = null
        var volume: Double? = null

        runCatching {
            val body = getBody(priceUrl) ?: return@runCatching
            val json = JSONObject(body)
            val info = json.optJSONObject("closingPriceInfo")
            if (info != null) {
                closePrice = info.optDouble("pClosing", 0.0).takeIf { it > 0 }
                lastPrice = info.optDouble("pDrCotVal", 0.0).takeIf { it > 0 }
                volume = info.optDouble("qTotTran5J", 0.0).takeIf { it > 0 }
                val yesterday = info.optDouble("priceYesterday", 0.0)
                if (yesterday > 0 && closePrice != null) {
                    changePct = ((closePrice!! - yesterday) / yesterday) * 100.0
                }
            }
        }

        // نام و نماد
        val local = POPULAR_INSTRUMENTS.firstOrNull { it.insCode == insCode }
        if (local != null) {
            return local.copy(
                closePrice = closePrice ?: local.closePrice,
                lastPrice = lastPrice ?: local.lastPrice,
                changePct = changePct ?: local.changePct,
                volume = volume ?: local.volume
            )
        }

        // دریافت نام از InstrumentInfo
        val infoUrl = "https://cdn.tsetmc.com/api/Instrument/GetInstrumentInfo/$insCode"
        var symbol = insCode
        var name = "نماد بورس ($insCode)"

        runCatching {
            val body = getBody(infoUrl) ?: return@runCatching
            val json = JSONObject(body)
            val info = json.optJSONObject("instrumentInfo")
            if (info != null) {
                symbol = info.optString("lVal18AFC").ifBlank { insCode }
                name = info.optString("lVal30").ifBlank { symbol }
            }
        }

        return TseInstrument(
            insCode = insCode,
            symbol = symbol,
            name = name,
            lastPrice = lastPrice,
            closePrice = closePrice,
            changePct = changePct,
            volume = volume
        )
    }

    /**
     * گرفتن قیمت یک نماد بورس تهران — با چند لایه‌ی پشتیبان:
     * ۱) insCode عددی  ۲) کاتالوگ داخلی  ۳) جستجوی آنلاین (نمادهای جدید و صندوق‌ها)
     * اگر با کدی قیمت نیامد، از جستجوی آنلاین کمک گرفته می‌شود تا نماد «نیاید» نشود.
     */
    suspend fun fetchQuote(code: String): TseInstrument {
        // ۱) insCode عددی مستقیم
        if (code.matches(Regex("\\d{8,20}"))) {
            val byIns = fetchByInsCode(code)
            if (byIns != null && (byIns.closePrice != null || byIns.lastPrice != null)) return byIns
        }

        // ۲) کاتالوگ داخلی (سریع/آفلاین) — فقط اگر insCode داشته باشد و قیمت بدهد
        POPULAR_INSTRUMENTS.firstOrNull {
            (it.symbol == code || it.name == code) && it.insCode.isNotBlank()
        }?.let { local ->
            val closing = fetchClosing(local.insCode)
            if (closing.close != null || closing.last != null) {
                return local.copy(
                    lastPrice = closing.last ?: local.lastPrice,
                    closePrice = closing.close ?: local.closePrice,
                    changePct = closing.changePct ?: local.changePct,
                    volume = closing.volume ?: local.volume
                )
            }
        }

        // ۳) جستجوی آنلاین — منبع حقیقت برای نمادهای جدید (صندوق‌های درآمد ثابت و ...)
        val online = runCatching { searchTseOnline(code) }.getOrDefault(emptyList())
        val best = online.firstOrNull { it.symbol.equals(code, ignoreCase = true) }
            ?: online.firstOrNull()
        if (best != null && best.insCode.isNotBlank()) {
            val closing = fetchClosing(best.insCode)
            return best.copy(
                lastPrice = closing.last ?: best.lastPrice,
                closePrice = closing.close ?: best.closePrice,
                changePct = closing.changePct ?: best.changePct,
                volume = closing.volume ?: best.volume
            )
        }

        // ۴) چیزی پیدا نشد — حداقل اسم نماد برگردد
        return POPULAR_INSTRUMENTS.firstOrNull { it.symbol == code || it.name == code }
            ?: TseInstrument(code, code, code)
    }

    private data class Closing(
        val close: Double?,
        val last: Double?,
        val changePct: Double?,
        val volume: Double?
    )

    private fun fetchClosing(insCode: String): Closing {
        return try {
            val body = getBody("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/$insCode")
                ?: return Closing(null, null, null, null)
            val root = runCatching { JSONObject(body) }.getOrNull()
                ?: return Closing(null, null, null, null)
            val info = root.optJSONObject("closingPriceInfo") ?: root
            val close = info.optDouble("pClosing", 0.0).takeIf { it > 0 }
            val last = info.optDouble("pDrCotVal", 0.0).takeIf { it > 0 }
            val volume = info.optDouble("qTotTran5J", 0.0).takeIf { it > 0 }
            val yesterday = info.optDouble("priceYesterday", 0.0)
            val change = info.optDouble("priceChange", 0.0)
            val price = close ?: last
            val pct = when {
                price != null && yesterday > 0 -> ((price - yesterday) / yesterday) * 100.0
                change != 0.0 && yesterday > 0 -> (change / yesterday) * 100.0
                else -> null
            }
            Closing(close, last, pct, volume)
        } catch (_: Exception) {
            // Errorهای جدی JVM (OOM و مانند آن) نباید به‌عنوان «بدون قیمت» بلعیده شوند.
            Closing(null, null, null, null)
        }
    }

    private fun parseSearchResponse(body: String): List<TseInstrument> {
        val list = mutableListOf<TseInstrument>()
        val arr = parseArray(body, "instrumentSearch") ?: return list

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val insCode = item.optString("insCode").trim()
            val symbol = item.optString("lVal18AFC").trim().ifBlank { item.optString("l18") }
            val name = item.optString("lVal30").trim().ifBlank { symbol }
            val pClosing = item.optDouble("pClosing", 0.0).takeIf { it > 0 }
            val pLast = item.optDouble("pDrCotVal", 0.0).takeIf { it > 0 }
            val volume = item.optDouble("qTotTran5J", 0.0).takeIf { it > 0 }
            val yesterday = item.optDouble("priceYesterday", 0.0)
            val changePct = if (yesterday > 0 && pClosing != null) {
                ((pClosing - yesterday) / yesterday) * 100.0
            } else null

            if (symbol.isNotBlank()) {
                list.add(
                    TseInstrument(
                        insCode = insCode,
                        symbol = symbol,
                        name = name,
                        lastPrice = pLast,
                        closePrice = pClosing,
                        changePct = changePct,
                        volume = volume,
                        cIsin = item.optString("cIsin")
                    )
                )
            }
        }
        return list
    }

    /** آرایه را از پاسخ خام `[...]` یا پیچیده `{"instrumentSearch":[...]}` بیرون می‌کشد */
    private fun parseArray(body: String, vararg keys: String): JSONArray? {
        runCatching { JSONArray(body) }.getOrNull()?.let { return it }
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        for (k in keys) root.optJSONArray(k)?.let { return it }
        val names = root.keys()
        while (names.hasNext()) {
            root.optJSONArray(names.next())?.let { return it }
        }
        return null
    }
}
