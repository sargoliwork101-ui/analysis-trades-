package com.pulse.market.data

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * وقتی درخواست AI «تمام‌وقت» می‌شود، پیام «پاسخی نیامد» هیچ کمکی به کاربر نمی‌کند:
 * معلوم نیست مشکل از اینترنت است، از فیلترینگ، یا از کندیِ خودِ مدل. این کلاس سه
 * مرحله‌ی واقعی اتصال را جدا جدا می‌آزماید و می‌گوید دقیقاً کدام مرحله شکسته است.
 * هیچ کلید و هیچ داده‌ای فرستاده نمی‌شود؛ فقط DNS، TCP و دست‌دادن TLS.
 */
object NetworkProbe {

    enum class Stage { DNS, TCP, TLS, OK }

    data class Result(val stage: Stage, val millis: Long)

    /** مهلت کوتاه عمدی: عیب‌یابی نباید خودش کاربر را منتظر بگذارد. */
    internal const val PROBE_TIMEOUT_MS = 6_000

    /** باید روی نخ پس‌زمینه صدا زده شود. */
    fun probe(host: String, port: Int = 443, timeoutMs: Int = PROBE_TIMEOUT_MS): Result {
        val startedAt = System.nanoTime()
        fun elapsed() = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val address = try {
            InetAddress.getByName(host)
        } catch (_: Exception) {
            return Result(Stage.DNS, elapsed())
        }
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            try {
                val secure = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, host, port, true) as SSLSocket
                secure.soTimeout = timeoutMs
                secure.startHandshake()
                secure.close()
                Result(Stage.OK, elapsed())
            } catch (_: Exception) {
                Result(Stage.TLS, elapsed())
            }
        } catch (_: Exception) {
            Result(Stage.TCP, elapsed())
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** سرویس‌هایی که از داخل ایران بدون فیلترشکن جواب می‌دهند و OpenAI-compatible‌اند. */
    internal const val IRAN_FRIENDLY_HINT =
        "اگر فیلترشکن نداری، سرویس‌های داخلی هم همین‌جا کار می‌کنند: " +
            "AvalAI با آدرس https://api.avalai.ir/v1 یا GapGPT با آدرس https://api.gapgpt.app/v1 " +
            "(هر دو OpenAI-compatible‌اند و همان مدل‌های GPT/Claude/Gemini را می‌دهند)."

    /** ترجمه‌ی نتیجه‌ی آزمایش به یک جمله‌ی قابل‌اقدام برای کاربر. */
    internal fun advice(host: String, result: Result): String {
        val name = host.ifBlank { "سرویس" }
        return when (result.stage) {
            Stage.DNS -> "🔍 عیب‌یابی: نام «$name» اصلاً به IP تبدیل نشد. یعنی یا اینترنت دستگاه " +
                "قطع است، یا آدرس API غلط تایپ شده، یا DNS/فیلترینگ جلوی آن را گرفته. " +
                "آدرس را دوباره نگاه کن و اینترنت را امتحان کن."
            Stage.TCP -> "🔍 عیب‌یابی: «$name» پیدا شد ولی درِ ارتباط (پورت امن) باز نشد. " +
                "این نشانه‌ی روشنِ مسدودبودن مسیر است؛ با فیلترشکن امتحان کن. " + IRAN_FRIENDLY_HINT
            Stage.TLS -> "🔍 عیب‌یابی: اتصال به «$name» برقرار شد ولی ارتباط امن (TLS) وسط کار " +
                "بسته شد — معمولاً یعنی مسیر فیلتر می‌شود یا ساعت/تاریخ دستگاه اشتباه است. " +
                IRAN_FRIENDLY_HINT
            Stage.OK -> "🔍 عیب‌یابی: خودِ «$name» در ${result.millis} میلی‌ثانیه در دسترس بود، " +
                "پس شبکه سالم است و مشکل از خودِ پاسخ‌دادن مدل است: مدل سبک‌تر انتخاب کن " +
                "(مثل gemini-2.0-flash یا gpt-4o-mini)، «درخواست جست‌وجوی وب» را خاموش کن " +
                "و حالت صرفه‌جویی را روشن بگذار."
        }
    }

    /** پورت واقعیِ همان آدرس؛ سرویس‌های خودمیزبان گاهی روی پورت غیر ۴۴۳ هستند. */
    internal fun portOf(uri: java.net.URI): Int = when {
        uri.port > 0 -> uri.port
        uri.scheme.orEmpty().equals("http", ignoreCase = true) -> 80
        else -> 443
    }

    /** عیب‌یابی کاملِ یک آدرس API؛ اگر میزبان خوانده نشود، چیزی اضافه نمی‌کند. */
    fun adviceFor(endpoint: String): String? {
        val uri = runCatching { java.net.URI(endpoint.trim()) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        if (host.isBlank()) return null
        return advice(host, probe(host, portOf(uri)))
    }
}
