package com.pulse.market.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * بررسی آپدیت برنامه از «آخرین Release» گیت‌هاب.
 *
 * جریان نصب آپدیت: نسخه‌ی جدید روی همین نصب نصب می‌شود (Windows-style update) —
 * نیازی به پاک کردن برنامه نیست، تنظیمات هم سر جایش می‌ماند.
 */
object AppUpdater {

    private const val REPO = "sargoliwork101-ui/analysis-trades-"

    /** اطلاعات آخرین نسخه‌ی منتشرشده */
    data class LatestRelease(
        val tag: String,
        val version: List<Int>,
        val apkUrl: String?,
        val notes: String,
        val publishedAt: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * خواندن آخرین ریلیز:
     * - tag خالی = هنوز هیچ ریلیز رسمی منتشر نشده
     * - null = خطای شبکه/API
     */
    suspend fun fetchLatest(): LatestRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    return@use LatestRelease("", emptyList(), null, "", "")
                }
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val obj = JSONObject(body)
                val tag = obj.optString("tag_name")
                val assets = obj.optJSONArray("assets")
                var apk: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val url = assets.getJSONObject(i).optString("browser_download_url")
                        if (url.endsWith(".apk")) {
                            apk = url
                            break
                        }
                    }
                }
                LatestRelease(
                    tag = tag,
                    version = parseVersion(tag),
                    apkUrl = apk,
                    notes = obj.optString("body").take(600),
                    publishedAt = obj.optString("published_at")
                )
            }
        }.getOrNull()
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
