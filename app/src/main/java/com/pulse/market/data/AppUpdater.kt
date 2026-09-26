package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * بررسی آپدیت برنامه از «آخرین Release» گیت‌هاب.
 *
 * جریان نصب آپدیت: نسخه‌ی جدید روی همین نصب نصب می‌شود (Windows-style update) —
 * نیازی به پاک کردن برنامه نیست، تنظیمات هم سر جایش می‌ماند.
 *
 * ── سخت‌گیری‌های امنیتی (نسخه‌ی ۱٫۱۴) ──
 * ۱) فایل نصبی فقط از **ریلیزهای همین مخزن** قبول می‌شود: آدرس باید با
 *    `https://github.com/<repo>/releases/download/` شروع شود. قبلاً «اولین فایل
 *    با پسوند apk» برداشته می‌شد؛ اگر روزی asset دیگری اضافه شود یا پاسخ API
 *    دست‌کاری شود، ممکن بود آدرس بی‌ربط به کاربر داده شود.
 * ۲) نام فایل باید الگوی خودمان را داشته باشد (`PulseMarket-v*.apk`) — همان
 *    چیزی که CI می‌سازد و ورک‌فلوی بیلد هم امضایش را بررسی می‌کند.
 * ۳) اگر GitHub برای فایل «digest» (sha256) بدهد، به کاربر نشان داده می‌شود تا
 *    خودش هم بتواند فایل دانلودشده را تطبیق دهد.
 * ۴) درخواست از کلاینت مشترک [Http] با سقف حجم می‌رود.
 */
object AppUpdater {

    private const val REPO = "sargoliwork101-ui/analysis-trades-"

    /** همه‌ی آدرس‌های قابل قبول برای فایل نصبی، ریلیزهای همین مخزن‌اند */
    private const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    private val APK_NAME = Regex("""^PulseMarket-v\d+(?:\.\d+)*\.apk$""")
    private val SHA256 = Regex("""^[0-9a-fA-F]{64}$""")

    /** اطلاعات آخرین نسخه‌ی منتشرشده */
    data class LatestRelease(
        val tag: String,
        val version: List<Int>,
        val apkUrl: String?,
        val notes: String,
        val publishedAt: String,
        /** اثر انگشت SHA-256 فایل نصبی (اگر GitHub داده باشد) */
        val apkSha256: String? = null
    )

    /**
     * خواندن آخرین ریلیز:
     * - tag خالی = هنوز هیچ ریلیز رسمی منتشر نشده
     * - null = خطای شبکه/API
     */
    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        try {
            val body = try {
                Http.getText(
                    url = "https://api.github.com/repos/$REPO/releases/latest",
                    userAgent = Http.UA_DESKTOP,
                    accept = "application/vnd.github+json"
                )
            } catch (e: Http.HttpException) {
                // ۴۰۴ یعنی هنوز ریلیزی منتشر نشده — خطای شبکه نیست
                if (e.code == 404) return@withContext LatestRelease("", emptyList(), null, "", "")
                throw e
            }

            val obj = JSONObject(body)
            val tag = obj.optString("tag_name")
            val apk = pickApk(obj.optJSONArray("assets"))
            LatestRelease(
                tag = tag,
                version = parseVersion(tag),
                apkUrl = apk?.first,
                notes = obj.optString("body").take(600),
                publishedAt = obj.optString("published_at"),
                apkSha256 = apk?.second
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /**
     * انتخاب فایل نصبی بین asset های ریلیز — با سخت‌گیری کامل.
     * خروجی: (آدرس، sha256) یا null اگر asset معتبری نبود.
     */
    internal fun pickApk(assets: org.json.JSONArray?): Pair<String, String?>? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            // فقط همان نامی که CI خودمان می‌سازد. fallback قدیمیِ «هر فایل apk»
            // سخت‌گیری توضیح‌داده‌شده در بالای کلاس را عملاً دور می‌زد.
            if (!APK_NAME.matches(name)) continue
            if (!url.startsWith(RELEASE_DOWNLOAD_PREFIX) || url.substringAfterLast('/') != name) continue

            val sha = asset.optString("digest")
                .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.takeIf { SHA256.matches(it) }
                ?.lowercase()
            return url to sha
        }
        return null
    }

    /** آیا نسخه‌ی سمت چپ جدیدتر از نسخه‌ی فعلی است؟ */
    fun isNewer(latest: List<Int>, current: List<Int>): Boolean {
        val n = maxOf(latest.size, current.size)
        for (i in 0 until n) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** "v1.2" یا "1.2.1" → [1,2] یا [1,2,1] */
    fun parseVersion(text: String): List<Int> =
        text.removePrefix("v").split('.', '-')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
}
