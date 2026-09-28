package com.pulse.market.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * دانلود و نصبِ «درون‌برنامه‌ای» آپدیت — تا کاربر مجبور نباشد خودش فایل را از مرورگر
 * بگیرد و دستی نصب کند. مکملِ [AppUpdater] است (آن نسخه‌ی جدید را پیدا می‌کند، این
 * دانلود و نصب می‌کند).
 *
 * ── سخت‌گیری‌های امنیتی ──
 *  ۱) فقط از آدرسِ ریلیزِ رسمیِ همین مخزن دانلود می‌شود و نامِ فایل باید الگوی
 *     خودمان (`PulseMarket-v*.apk`) را داشته باشد — همان بررسی‌هایی که [AppUpdater] هم دارد.
 *  ۲) اگر GitHub برای فایل `sha256` بدهد، پس از دانلود خودکار تطبیق داده می‌شود؛
 *     نخواندنِ اثر انگشت = فایل دور انداخته می‌شود و نصب انجام نمی‌گیرد.
 *  ۳) نصب از راه نصب‌کننده‌ی رسمیِ سیستم انجام می‌شود؛ اندروید هم امضای APK را با
 *     نسخه‌ی نصب‌شده تطبیق می‌دهد (کلید ثابتِ CI)، پس آپدیت روی همین نصب می‌نشیند.
 */
object AppInstaller {

    private const val REPO = "sargoliwork101-ui/analysis-trades-"
    private const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private val APK_NAME = Regex("""^PulseMarket-v\d+(?:\.\d+)*\.apk$""")

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /**
     * دانلودِ APK به پوشه‌ی کشِ برنامه با گزارشِ پیشرفت (۰ تا ۱، یا null اگر حجم کل نامعلوم بود).
     * خروجی: فایلِ آماده‌ی نصب. در صورت هر خطا Exception پرتاب می‌شود.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        expectedSha256: String?,
        onProgress: (Float?) -> Unit
    ): File {
        val name = url.substringAfterLast('/')
        require(url.startsWith(RELEASE_DOWNLOAD_PREFIX) && APK_NAME.matches(name)) {
            "آدرس نصبی نامعتبر است"
        }
        val dir = File(context.cacheDir, "updates")
        // دانلودهای قبلی پاک می‌شوند تا کش پر نشود.
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        dir.mkdirs()
        val dest = File(dir, name)
        Http.download(url, dest) { written, total ->
            onProgress(total?.let { (written.toFloat() / it).coerceIn(0f, 1f) })
        }
        if (expectedSha256 != null) {
            val actual = sha256(dest)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                runCatching { dest.delete() }
                error("اثر انگشت فایل با ریلیز رسمی نخواند")
            }
        }
        return dest
    }

    /** آیا برنامه اجازه‌ی «نصب برنامه‌های ناشناس» را دارد؟ (پیش از اندروید ۸ نیازی نیست) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** کاربر را به صفحه‌ی سیستمیِ «اجازه‌ی نصب از این برنامه» می‌برد (اندروید ۸ به بعد). */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** نصب‌کننده‌ی رسمیِ سیستم را روی فایلِ دانلودشده باز می‌کند. */
    fun launchInstaller(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
