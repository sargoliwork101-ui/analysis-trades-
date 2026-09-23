package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ─────────────────────────────────────────────────────────────────────────
 * ماژول مرجع «شبکه» — تنها OkHttpClient برنامه و تنها جای باز کردن HTTP.
 *
 * چرا: قبلاً چهار کلاینت جدا (Fetcher، TseService، WebSymbolSearch، AppUpdater)
 * هر کدام استخر اتصال و تردهای خودشان را می‌ساختند؛ هم حافظه/سوکت بیشتری
 * می‌گرفت، هم تنظیماتشان با هم فرق داشت. حالا همه از همین یک کلاینت مشترک
 * می‌خوانند.
 *
 * قواعد امنیتی این ماژول (برای همه‌ی خواننده‌ها):
 *  ۱) فقط http/https — آدرس‌های دیگر (file، content، intent…) رد می‌شوند.
 *  ۲) سقف حجم پاسخ — پاسخ چندصد مگابایتی، حافظه‌ی برنامه را نمی‌خورد.
 *  ۳) هدر User-Agent/Accept همیشه ست می‌شود (بعضی سایت‌ها بدون آن ۴۰۳ می‌دهند).
 * ─────────────────────────────────────────────────────────────────────────
 */
object Http {

    /** مرورگر موبایل — برای API هایی که به UA حساس‌اند (TSETMC، TGJU) */
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** مرورگر دسکتاپ — بعضی API ها فقط با UA دسکتاپ جواب می‌دهند */
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * سقف حجم پاسخ JSON.
     * عمداً دست‌ودل‌باز است (۸ مگابایت): پاسخ TGJU یک فایل بزرگ چند صد کیلوبایتی
     * است که همه‌ی نمادهای طلا/ارز را با هم می‌دهد، نباید قربانی سقف شود.
     * ولی پاسخ غول‌آسا (مثلاً چند صد مگابایت از یک سرور خراب/مخرب) خوانده نمی‌شود.
     */
    const val MAX_JSON_BYTES = 8L * 1024 * 1024

    /** سقف پیش‌فرض صفحه‌ی HTML — صفحات سنگین اسکرپ هم جا شوند */
    const val MAX_HTML_BYTES = 8L * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** خطای HTTP با کد وضعیت — تا خواننده‌ها بتوانند ۴۰۴ را از بقیه جدا کنند */
    class HttpException(val code: Int, message: String) : IllegalStateException(message)

    /**
     * اجرای یک Request دلخواه با سقف حجم.
     * برای کدهایی که Request خودشان را می‌سازند (مثل TSETMC).
     */
    fun execute(request: Request, maxBytes: Long = MAX_JSON_BYTES): String =
        client.newCall(request).execute().use { resp -> readCapped(resp, maxBytes) }

    /**
     * GET متنی با سقف حجم و بررسی کد پاسخ.
     *
     * @param userAgent پیش‌فرض UA موبایل
     * @param accept    مقدار هدر Accept (برای HTML/JSON فرق می‌کند)
     * @param headers   هدرهای دلخواه منبع (از تنظیمات کاربر)
     * @param maxBytes  سقف حجم پاسخ؛ بیشتر از آن = خطا (نه OOM)
     */
    suspend fun getText(
        url: String,
        userAgent: String = UA_MOBILE,
        accept: String = "application/json, text/plain, */*",
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = MAX_JSON_BYTES
    ): String = withContext(Dispatchers.IO) { getTextBlocking(url, userAgent, accept, headers, maxBytes) }

    /** همان [getText] بدون سوئیچ دیسپچر — برای کدهایی که خودشان روی IO هستند */
    fun getTextBlocking(
        url: String,
        userAgent: String = UA_MOBILE,
        accept: String = "application/json, text/plain, */*",
        headers: Map<String, String> = emptyMap(),
        maxBytes: Long = MAX_JSON_BYTES
    ): String {
        // فقط http/https — جلوی آدرس‌های عجیب (file://، intent://) را می‌گیرد
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            error("آدرس نامعتبر — فقط http و https")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "fa,en;q=0.8")
            .header("Accept", accept)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        return client.newCall(request).execute().use { resp -> readCapped(resp, maxBytes) }
    }

    /** خواندن بدنه با سقف حجم + پیام خطای فارسیِ خوانا */
    private fun readCapped(resp: Response, maxBytes: Long): String {
        val body = resp.body
        val declared = body?.contentLength() ?: -1L
        if (declared > maxBytes) error("حجم پاسخ سایت بیش از حد مجاز است")
        val source = body?.source()
        val text = if (source == null) {
            ""
        } else {
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) error("حجم پاسخ سایت بیش از حد مجاز است")
            try {
                source.readUtf8()
            } catch (io: IOException) {
                error("خواندن پاسخ ناتمام ماند")
            }
        }
        if (!resp.isSuccessful) {
            val detail = if (text.isNotBlank()) " — ${text.trim().take(60)}" else ""
            throw HttpException(resp.code, "HTTP ${resp.code}$detail")
        }
        return text
    }
}
