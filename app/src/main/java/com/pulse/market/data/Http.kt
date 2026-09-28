package com.pulse.market.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
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

    /** سقف حجم فایل نصبی (APK) هنگام آپدیت درون‌برنامه‌ای — دست‌ودل‌باز برای رشد آینده. */
    const val MAX_APK_BYTES = 80L * 1024 * 1024

    /**
     * سقف کش دیسکِ HTTP (۵ مگابایت). کش فقط پاسخ‌های GET ای را نگه می‌دارد که خودِ سرور
     * با هدرهای `Cache-Control`/`ETag` قابل‌کش اعلام کرده است؛ پس داده‌ی قیمتِ زنده که
     * سرور آن را no-cache می‌فرستد هرگز کهنه سرو نمی‌شود. سود اصلی: درخواست‌های شرطی
     * (If-None-Match / 304 Not Modified) پهنای‌باند و مصرف داده را کم می‌کنند، و اگر
     * سرور یک TTL کوتاه بدهد، تازه‌سازی‌های پشت‌سرهم دوباره از شبکه نمی‌گیرند.
     */
    private const val MAX_CACHE_BYTES = 5L * 1024 * 1024

    /** مسیر کش؛ یک‌بار در [init] از روی [Context] ست می‌شود (پیش از اولین درخواست). */
    @Volatile
    private var cacheDir: File? = null

    /**
     * یک‌بار در `Application.onCreate` صدا زده می‌شود تا کش دیسک پیش از ساخته‌شدنِ
     * تنبلِ [client] آماده باشد. بی‌ضرر است اگر چند بار صدا زده شود.
     */
    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = runCatching { File(context.applicationContext.cacheDir, "http_cache") }.getOrNull()
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // کش دیسک اختیاری است؛ اگر init صدا نخورده باشد، کلاینت بدون کش کار می‌کند
            // (رفتار قبلی). سرور تصمیم می‌گیرد چه چیزی و چند وقت کش شود.
            .apply { cacheDir?.let { cache(Cache(it, MAX_CACHE_BYTES)) } }
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
    fun execute(
        request: Request,
        maxBytes: Long = MAX_JSON_BYTES,
        callTimeoutSeconds: Int? = null
    ): String = resolveFinal(request, callTimeoutSeconds).use { readCapped(it, maxBytes) }

    /**
     * درخواست را (با دنبال‌کردن امنِ redirect) تا پاسخِ نهاییِ غیرِ redirect پیش می‌برد و
     * آن را باز برمی‌گرداند؛ **مسئولیت بستنِ پاسخ با فراخواننده است** (`.use { … }`).
     */
    private fun resolveFinal(request: Request, callTimeoutSeconds: Int? = null): Response {
        rejectSensitiveCleartext(request)
        var current = request
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val call = client.newCall(current)
            callTimeoutSeconds?.let {
                // سقف ۳۰۰ ثانیه: هم مدل‌های thinking کندند، هم دانلود APK روی موبایل.
                call.timeout().timeout(it.coerceIn(3, 300).toLong(), TimeUnit.SECONDS)
            }
            val response = call.execute()
            if (!response.isRedirect) return response
            // پاسخِ redirect فقط برای هدر Location لازم است؛ بدنه‌اش بسته می‌شود.
            response.use { r ->
                if (redirectCount >= MAX_REDIRECTS) error("تعداد تغییر مسیر پاسخ بیش از حد مجاز است")
                // POST هوش مصنوعی یا هر بدنه‌ی حساس نباید خودکار به مقصد دیگری فرستاده شود.
                if (current.body != null) error("تغییر مسیر برای درخواست دارای بدنه مجاز نیست")
                val location = r.header("Location")
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
                        for (name in current.headers.names().filter(::isSensitiveHeader)) {
                            removeHeader(name)
                        }
                    }
                }.build()
                rejectSensitiveCleartext(current)
            }
        }
        error("تغییر مسیر پاسخ کامل نشد")
    }

    /**
     * دانلودِ فایلِ دودویی (مثل APK آپدیت) روی [dest] با سقف حجم و گزارشِ پیشرفت.
     * فقط https مجاز است. [onProgress] بایت‌های دریافتی و حجم کل (اگر سرور بدهد) را می‌دهد.
     */
    suspend fun download(
        url: String,
        dest: File,
        maxBytes: Long = MAX_APK_BYTES,
        onProgress: ((downloaded: Long, total: Long?) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            error("دانلود فقط از https مجاز است")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Accept", "application/octet-stream, */*")
            .build()
        resolveFinal(request, callTimeoutSeconds = 300).use { resp ->
            if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
            val body = resp.body ?: error("پاسخ خالی بود")
            val total = body.contentLength().takeIf { it > 0 }
            if (total != null && total > maxBytes) error("حجم فایل بیش از حد مجاز است")
            dest.parentFile?.mkdirs()
            var written = 0L
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > maxBytes) {
                            runCatching { dest.delete() }
                            error("حجم فایل بیش از حد مجاز است")
                        }
                        output.write(buf, 0, n)
                        onProgress?.invoke(written, total)
                    }
                    output.flush()
                }
            }
        }
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
            .apply {
                for ((name, value) in safeHeadersForUrl(url, headers)) header(name, value)
            }
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
            // ۶۰ کاراکتر برای دیدن پیام واقعی سرویس (مثل خطاهای Google/OpenAI) کم بود.
            val detail = if (text.isNotBlank()) {
                " — ${SensitiveText.redact(text.trim(), 240)}"
            } else ""
            throw HttpException(resp.code, "HTTP ${resp.code}$detail")
        }
        return text
    }
}
