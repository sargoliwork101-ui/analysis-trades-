package com.pulse.market.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONArray

/**
 * ─────────────────────────────────────────────────────────────────────────
 * اسکنر «پامپ‌های کریپتو».
 *
 * پامپ = رشد سریع و شارپ قیمت یک کوین در بازه‌ی کوتاه، معمولاً همراه با جهش حجم
 * معاملات. این ماژول کوین‌های برتر بازار را از CoinGecko می‌خواند و با یک امتیاز
 * ترکیبی، آن‌هایی که واقعاً در حال پامپ‌اند را جدا می‌کند:
 *
 *   امتیاز = تغییر ۲۴ ساعت  +  ۲ × تغییر ۱ ساعت  +  ۵۰ × (حجم ۲۴س / ارزش بازار، سقف ۱)
 *
 * «نسبت حجم به ارزش بازار» (turnover) مهم‌ترین نشانه است: کوینی که در ۲۴ ساعت
 * به‌اندازه‌ی کل ارزش بازارش معامله شده، یعنی پول واقعی واردش شده — نه فقط
 * چند سفارش کوچک که قیمت را جابه‌جا کرده.
 *
 * هشدار مهم (همین در متن راهنمای داخل برنامه هم آمده): بیشتر پامپ‌ها پایان بدی
 * دارند. این بخش «فرصت» نیست، «هشدار» است — نه سیگنال خرید.
 * ─────────────────────────────────────────────────────────────────────────
 */
object PumpScanner {

    /** چند کوین در هر اسکن خوانده شود (سقف رایگان CoinGecko = ۲۵۰) */
    val UNIVERSE_CHOICES = listOf(50, 100, 250)

    /** حداکثر تعداد کوینی که در نتیجه نگه داشته می‌شود */
    private const val MAX_RESULTS = 30

    private const val PREF = "pulse_pumps"
    private const val KEY_LAST = "last_scan"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** یک کوین با نشانه‌های پامپ */
    @Serializable
    data class PumpCoin(
        val id: String,
        val symbol: String,
        val name: String,
        val price: Double? = null,
        val change1h: Double? = null,
        val change24h: Double? = null,
        val change7d: Double? = null,
        val volume: Double? = null,
        val marketCap: Double? = null,
        val rank: Int = 0,
        val score: Double = 0.0
    ) {
        /** «سولانا (SOL)» — نام نمایشی برای ویجت و فهرست‌ها */
        val displayName: String
            get() = if (symbol.isBlank()) name else "$name (${symbol.uppercase()})"

        /** تبدیل به نماد قابل افزودن به ویجت (منبع: کریپتو CoinGecko) */
        fun toSymbolDef(sourceId: String = CRYPTO_SOURCE_ID): SymbolDef =
            SymbolDef(code = id, label = displayName, sourceId = sourceId, unit = "$", scale = 1.0)

        /**
         * ریسکِ کوین بر اساس جایگاه و ارزش بازار — پامپ کوین‌های کوچک‌تر خطرناک‌تر است.
         * صرفاً یک نشانه‌ی راهنما برای کاربر است، نه توصیه‌ی مالی.
         */
        val risk: Risk
            get() = when {
                rank in 1..30 && (marketCap ?: 0.0) >= 5_000_000_000.0 -> Risk.LOW
                rank in 1..120 && (marketCap ?: 0.0) >= 500_000_000.0 -> Risk.MEDIUM
                else -> Risk.HIGH
            }

        /** پیشنهاد احتیاطی و دلیل آن؛ عمداً هیچ حالت «خرید» ندارد. */
        val advice: Advice
            get() = adviceFor(this)
    }

    /** سطح ریسکِ تقریبی — فقط برای نمایش رنگ و برچسب */
    enum class Risk(val label: String) {
        LOW("کم‌ریسک‌تر"),
        MEDIUM("پرنوسان"),
        HIGH("پرخطر")
    }

    enum class Recommendation(val label: String) {
        WATCH("فقط زیر نظر بگیر"),
        WAIT("برای ورود عجله نکن"),
        AVOID("از تعقیب قیمت دوری کن")
    }

    data class Advice(val recommendation: Recommendation, val reason: String)

    /** نتیجه‌ی یک اسکن */
    @Serializable
    data class PumpScan(
        val at: Long = 0L,
        val universe: Int = 100,
        val minChange: Double = 8.0,
        /** همه‌ی کوین‌های اسکن‌شده، مرتب‌شده بر اساس امتیاز (نزولی) */
        val coins: List<PumpCoin> = emptyList(),
        /** متن خطا اگر اسکن ناموفق بود (نتیجه‌ی قبلی در کش می‌ماند) */
        val error: String? = null
    ) {
        /** کوین‌هایی که آستانه‌ی پامپ را رد کرده‌اند */
        val matches: List<PumpCoin>
            get() = coins.filter { (it.change24h ?: 0.0) >= minChange }

        val isEmpty: Boolean get() = coins.isEmpty()
    }

    const val CRYPTO_SOURCE_ID = "crypto_coingecko"

    /**
     * آخرین اسکن ذخیره‌شده (روی همین گوشی) — تا با باز کردن صفحه، فهرست قبلی
     * فوری دیده شود و نیازی به درخواست تازه نباشد.
     */
    fun cached(context: Context): PumpScan? {
        val raw = prefs(context).getString(KEY_LAST, null) ?: return null
        return runCatching { json.decodeFromString(PumpScan.serializer(), raw) }.getOrNull()
            ?.takeIf { it.coins.isNotEmpty() }
    }

    /**
     * اسکن تازه‌ی بازار.
     *
     * @param universe چند کوین برتر بازار خوانده شود (۵۰/۱۰۰/۲۵۰)
     * @param minChange آستانه‌ی رشد ۲۴ ساعته برای «پامپ» حساب‌شدن
     * @param force اگر false و نتیجه‌ی تازه‌ی کش کمتر از ۲ دقیقه پیش باشد، همان برگردانده می‌شود
     */
    suspend fun scan(
        context: Context,
        universe: Int = 100,
        minChange: Double = 8.0,
        force: Boolean = false
    ): PumpScan = withContext(Dispatchers.IO) {
        val size = universe.coerceIn(10, 250)
        val threshold = minChange.coerceIn(0.0, 100.0)

        if (!force) {
            cached(context)?.let { old ->
                val fresh = System.currentTimeMillis() - old.at < 120_000L
                if (fresh && old.universe == size) {
                    return@withContext old.copy(minChange = threshold)
                }
            }
        }

        val url = "https://api.coingecko.com/api/v3/coins/markets" +
                "?vs_currency=usd&order=market_cap_desc&per_page=$size&page=1" +
                "&sparkline=false&price_change_percentage=1h,24h,7d"

        val result = try {
            val body = Http.getText(url)
            Result.success(parse(body, size, threshold))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }

        return@withContext result.fold(
            onSuccess = { scan ->
                prefs(context).edit()
                    .putString(KEY_LAST, json.encodeToString(PumpScan.serializer(), scan))
                    .apply()
                scan
            },
            onFailure = { t ->
                // خطای شبکه: نتیجه‌ی قبلیِ کش را با پیام خطا برمی‌گردانیم (صفحه خالی نمی‌شود)
                val old = cached(context)
                if (old != null) old.copy(
                    minChange = threshold,
                    error = t.message ?: "خطای شبکه"
                )
                else PumpScan(
                    at = System.currentTimeMillis(),
                    universe = size,
                    minChange = threshold,
                    coins = emptyList(),
                    error = t.message ?: "خطای شبکه"
                )
            }
        )
    }

    /** خواندن پاسخ CoinGecko و ساختن امتیاز پامپ */
    private fun parse(body: String, universe: Int, minChange: Double): PumpScan {
        val arr = JSONArray(body)
        val out = ArrayList<PumpCoin>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            val price: Double? = o.optDouble("current_price").takeIf { it.isFinite() }
            val cap: Double? = o.optDouble("market_cap").takeIf { it.isFinite() }
            val volume: Double? = o.optDouble("total_volume").takeIf { it.isFinite() }
            val change24: Double? = o.optDouble("price_change_percentage_24h").takeIf { it.isFinite() }
            val change1: Double? = o.optDouble("price_change_percentage_1h_in_currency").takeIf { it.isFinite() }
            val change7: Double? = o.optDouble("price_change_percentage_7d_in_currency").takeIf { it.isFinite() }
            out += PumpCoin(
                id = id,
                symbol = o.optString("symbol"),
                name = o.optString("name").ifBlank { id },
                price = price,
                change1h = change1,
                change24h = change24,
                change7d = change7,
                volume = volume,
                marketCap = cap,
                rank = o.optInt("market_cap_rank", 0),
                score = score(change1, change24, volume, cap)
            )
        }
        val sorted = out.sortedByDescending { it.score }.take(MAX_RESULTS)
        return PumpScan(
            at = System.currentTimeMillis(),
            universe = universe,
            minChange = minChange,
            coins = sorted,
            error = null
        )
    }

    /**
     * پیشنهاد احتیاطی بر اساس ریسک، شتاب یک‌ساعته و گردش حجم. این خروجی توصیه‌ی
     * سرمایه‌گذاری نیست و عمداً هیچ‌وقت «بخر» نمی‌گوید.
     */
    fun adviceFor(coin: PumpCoin): Advice {
        val ch1 = coin.change1h?.takeIf { it.isFinite() } ?: 0.0
        val ch24 = coin.change24h?.takeIf { it.isFinite() } ?: 0.0
        val turnover = turnover(coin.volume, coin.marketCap)
        return when {
            coin.risk == Risk.HIGH -> Advice(
                Recommendation.AVOID,
                "رتبه/ارزش بازار پایین است و برگشت قیمت در کوین‌های کوچک می‌تواند بسیار سریع باشد."
            )
            ch24 >= 25.0 -> Advice(
                Recommendation.AVOID,
                "رشد ۲۴ ساعته از ۲۵٪ گذشته و احتمال خرید در سقف و اصلاح تند بیشتر شده است."
            )
            turnover >= 0.50 -> Advice(
                Recommendation.AVOID,
                "حجم ۲۴ ساعته بیش از نصف ارزش بازار است؛ گردش غیرعادی می‌تواند نشانه‌ی هیجان یا تخلیه باشد."
            )
            ch1 <= 0.0 -> Advice(
                Recommendation.WAIT,
                "با وجود رشد ۲۴ ساعته، شتاب یک‌ساعته متوقف یا منفی شده و ادامه‌ی حرکت تأیید نشده است."
            )
            coin.risk == Risk.MEDIUM -> Advice(
                Recommendation.WAIT,
                "حرکت هنوز مثبت است اما نوسان و ریسک برگشت بالاست؛ تثبیت قیمت و حجم را صبر کن."
            )
            else -> Advice(
                Recommendation.WATCH,
                "ارزش بازار بزرگ‌تر ریسک دست‌کاری را کمتر می‌کند، اما رشد سریع هنوز می‌تواند اصلاح شود."
            )
        }
    }

    fun turnover(volume: Double?, marketCap: Double?): Double {
        val safeVolume = volume?.takeIf { it.isFinite() && it >= 0.0 }
        val safeCap = marketCap?.takeIf { it.isFinite() && it > 0.0 }
        return if (safeVolume != null && safeCap != null) {
            (safeVolume / safeCap).coerceIn(0.0, 1.0)
        } else 0.0
    }

    /**
     * امتیاز پامپ — ترکیب «شدت رشد» و «ورود پول واقعی».
     * turnover = حجم ۲۴ ساعته ÷ ارزش بازار (سقف ۱ تا یک کوین کل بازار را قبضه نکند).
     */
    fun score(change1h: Double?, change24h: Double?, volume: Double?, marketCap: Double?): Double {
        val ch1 = change1h?.takeIf { it.isFinite() } ?: 0.0
        val ch24 = change24h?.takeIf { it.isFinite() } ?: 0.0
        val turnover = turnover(volume, marketCap)
        return (ch24 + 2.0 * ch1 + 50.0 * turnover).takeIf { it.isFinite() } ?: 0.0
    }
}
