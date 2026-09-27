package com.pulse.market.data

import java.net.URI

/** اعتبارسنجی خالص URL منابع دلخواه، با حفظ placeholderهای مجاز نماد. */
object SourceUrlPolicy {
    private const val MAX_URL_LENGTH = 2_000
    private val placeholders = Regex("\\{symbols?}")

    fun isValid(value: String): Boolean {
        val raw = value.trim()
        if (raw.isEmpty() || raw.length > MAX_URL_LENGTH) return false
        // URI آکولاد خام را نمی‌پذیرد؛ فقط دو placeholder مستند را برای parse جایگزین می‌کنیم.
        val withoutKnownPlaceholders = placeholders.replace(raw, "symbol")
        if ('{' in withoutKnownPlaceholders || '}' in withoutKnownPlaceholders) return false
        return runCatching {
            val uri = URI(withoutKnownPlaceholders)
            (uri.scheme.equals("https", ignoreCase = true) ||
                    uri.scheme.equals("http", ignoreCase = true)) &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.rawFragment == null &&
                    (uri.port == -1 || uri.port in 1..65_535)
        }.getOrDefault(false)
    }
}
