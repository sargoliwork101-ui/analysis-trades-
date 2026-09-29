package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * تشخیصِ نمادِ معتبرِ تریدینگ‌ویو برای یک کوین، پیش از نشان‌دادنِ «نمودار پیشرفته».
 *
 * چرا: قبلاً کورکورانه `{SYMBOL}USDT` به ویجت داده می‌شد؛ برای کوینی که چنین جفتی در
 * تریدینگ‌ویو ندارد، فقط پیامِ «Invalid symbol» دیده می‌شد. حالا از خودِ سرویسِ جست‌وجوی
 * نمادِ تریدینگ‌ویو می‌پرسیم:
 *  • [Outcome.Found]    → نمادِ دقیق (`EXCHANGE:SYMBOL`) پیدا شد؛ همان به ویجت داده می‌شود.
 *  • [Outcome.NotFound] → پاسخ سالم بود ولی هیچ جفتِ USDT وجود ندارد → بازگشتِ خودکار به «نمودار داخلی».
 *  • [Outcome.Unknown]  → خطای شبکه/تجزیه؛ نمی‌دانیم. خوش‌بینانه با حدسِ `{SYMBOL}USDT` ادامه می‌دهیم
 *                          (رفتار مثل قبل)، تا خطای گذرای شبکه، کاربر را از نمودار محروم نکند.
 */
object TradingViewSymbols {

    sealed interface Outcome {
        data class Found(val tvSymbol: String) : Outcome
        data object NotFound : Outcome
        data object Unknown : Outcome
    }

    private const val ENDPOINT = "https://symbol-search.tradingview.com/symbol_search/"

    /** صرافی‌های ترجیحی به ترتیب اولویت (نقدشوندگی/پوششِ بیشتر). */
    private val PREFERRED = listOf("BINANCE", "BYBIT", "OKX", "COINBASE", "KUCOIN")

    /** نمادِ پایه را به حروف بزرگِ فقط حرف/عدد پاک می‌کند. */
    internal fun cleanBase(symbol: String): String =
        symbol.trim().uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(12)

    suspend fun resolve(symbol: String): Outcome = withContext(Dispatchers.IO) {
        val base = cleanBase(symbol)
        if (base.isEmpty()) return@withContext Outcome.Unknown
        val target = "${base}USDT"
        val url = "$ENDPOINT?text=$target&type=crypto&hl=0&lang=en"
        try {
            val body = Http.getText(
                url = url,
                userAgent = Http.UA_DESKTOP,
                accept = "application/json, text/plain, */*",
                headers = mapOf(
                    "Referer" to "https://www.tradingview.com/",
                    "Origin" to "https://www.tradingview.com"
                )
            )
            parse(body, base)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Outcome.Unknown
        }
    }

    /** حذفِ برچسب‌های HTML (سرویسِ جست‌وجو بخشِ منطبق را با <em>…</em> می‌پیچد). */
    internal fun stripTags(s: String): String = s.replace(Regex("<[^>]*>"), "")

    /**
     * تجزیه‌ی پاسخِ جست‌وجو. خروجی:
     *  • Found اگر جفتِ `{base}USDT` در نتایج باشد (صرافیِ ترجیحی اولویت دارد).
     *  • NotFound اگر پاسخ سالم بود ولی چنین جفتی نبود.
     */
    internal fun parse(body: String, base: String): Outcome {
        val target = "${base}USDT"
        val arr: JSONArray = runCatching { JSONArray(body) }.getOrNull()
            ?: runCatching { JSONObject(body).optJSONArray("symbols") }.getOrNull()
            ?: return Outcome.Unknown
        var fallbackExchange: String? = null
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val sym = stripTags(obj.optString("symbol")).uppercase(Locale.ROOT)
                .filter { it.isLetterOrDigit() }
            if (sym != target) continue
            found = true
            val exchange = obj.optString("exchange").trim().uppercase(Locale.ROOT)
            if (exchange.isEmpty()) continue
            if (exchange in PREFERRED) {
                return Outcome.Found("$exchange:$target")
            }
            if (fallbackExchange == null) fallbackExchange = exchange
        }
        return when {
            fallbackExchange != null -> Outcome.Found("$fallbackExchange:$target")
            found -> Outcome.Found(target)   // جفت هست ولی صرافی نامعلوم — ویجت خودش انتخاب می‌کند
            else -> Outcome.NotFound
        }
    }
}
