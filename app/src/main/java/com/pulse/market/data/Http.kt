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
 *  ۴) redirect محدود است؛ downgrade رد و credential هنگام تغییر origin حذف می‌شود.
 *  ۵) هدر حساس هیچ‌گاه روی HTTP cleartext ارسال نمی‌شود.
 * ─────────────────────────────────────────────────────────────────────────
 */
object Http {

    private const val MAX_REDIRECTS = 3

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
            // redirect خودکار می‌تواند هدر سفارشی مثل X-API-Key را به میزبان دیگری
            // ببرد. redirectهای GET را پایین‌تر خودمان، محدود و بدون credential دنبال می‌کنیم.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /** خطای HTTP با کد وضعیت — تا خواننده‌ها بتوانند ۴۰۴ را از بقیه جدا کنند */
    class HttpException(val code: Int, message: String) : IllegalStateException(message)

    /**
     * اجرای یک Request دلخواه با سقف حجم.
     * برای کدهایی که Request خودشان را می‌سازند (مثل TSETMC).
     */
    fun execute(request: Request, maxBytes: Long = MAX_JSON_BYTES): String {
        rejectSensitiveCleartext(request)
        var current = request
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            client.newCall(current).execute().use { response ->
                if (!response.isRedirect) return readCapped(response, maxBytes)
                if (redirectCount >= MAX_REDIRECTS) error("تعداد تغییر مسیر پاسخ بیش از حد مجاز است")
                // POST هوش مصنوعی یا هر بدنه‌ی حساس نباید خودکار به مقصد دیگری فرستاده شود.
                if (current.body != null) error("تغییر مسیر برای درخواست دارای بدنه مجاز نیست")
                val location = response.header("Location")
                    ?: error("پاسخ تغییر مسیر، مقصد معتبر ندارد")
                val next = current.url.resolve(location)
                    ?: error("مقصد تغییر مسیر نامعتبر است")
                if (current.url.isHttps && !next.isHttps) {
                    error("تغییر مسیر ناامن از HTTPS به HTTP رد شد")
                }
                val sameOrigin = current.url.scheme == next.scheme &&
                        current.url.host == next.host && current.url.port == next.port
                current = current.newBuilder().url(next).apply {
                    if (!sameOrigin) {
                        current.headers.names().filter(::isSensitiveHeader)
                            .forEach { name -> removeHeader(name) }
                    }
                }.build()
                rejectSensitiveCleartext(current)
            }
        }
        error("تغییر مسیر پاسخ کامل نشد")
    }

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
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            error("آدرس نامعتبر — فقط http و https")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "fa,en;q=0.8")
            .header("Accept", accept)
            .apply { safeHeadersForUrl(url, headers).forEach { (k, v) -> header(k, v) } }
            .build()

        return execute(request, maxBytes)
    }

    /** روی HTTP هیچ credentialای ارسال نمی‌شود؛ داده‌ی قیمت عمومی همچنان قابل خواندن است. */
    fun safeHeadersForUrl(url: String, headers: Map<String, String>): Map<String, String> {
        if (!url.trim().startsWith("http://", ignoreCase = true)) return headers
        return headers.filterKeys { !isSensitiveHeader(it) }
    }

    fun isSensitiveHeader(name: String): Boolean {
        val key = name.trim().lowercase()
        return key == "authorization" || key == "proxy-authorization" ||
                key == "cookie" || key == "x-api-key" || key == "api-key" ||
                key.endsWith("api-key") || key.endsWith("api_key") || key.endsWith("apikey") ||
                key.endsWith("subscription-key") || key == "x-auth-token" ||
                key == "access-token" || key == "token" ||
                key.endsWith("-token") || key.endsWith("_token") ||
                key.contains("password") || key.contains("secret")
    }

    private fun rejectSensitiveCleartext(request: Request) {
        if (request.url.isHttps) return
        val credentialsInUrl = request.url.username.isNotEmpty() || request.url.password.isNotEmpty() ||
                request.url.queryParameterNames.any(::isSensitiveQueryName)
        if (credentialsInUrl || request.headers.names().any(::isSensitiveHeader)) {
            error("ارسال کلید یا اعتبارنامه روی HTTP ناامن مجاز نیست")
        }
    }

    internal fun isSensitiveQueryName(name: String): Boolean {
        val key = name.trim().lowercase().replace('-', '_')
        return key == "key" || key == "authorization" || key == "auth" ||
                key.contains("api_key") || key.contains("apikey") ||
                key.contains("token") || key.contains("password") || key.contains("secret")
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
            val detail = if (text.isNotBlank()) {
                " — ${SensitiveText.redact(text.trim(), 60)}"
            } else ""
            throw HttpException(resp.code, "HTTP ${resp.code}$detail")
        }
        return text
    }
}
