package com.pulse.market.data

/** پاک‌سازی پیام خطا پیش از ذخیره یا نمایش؛ URL، token و credential هرگز نباید نشت کنند. */
object SensitiveText {
    private val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val assignedSecret = Regex(
        """(?i)(["']?(?:authorization|proxy-authorization|api[_-]?key|x-api-key|access[_-]?token|auth[_-]?token|token|password|client[_-]?secret|secret)["']?\s*[:=]\s*["']?)(?:(?:bearer|basic)\s+)?([^"',;\s}\\]+)"""
    )
    private val authorizationValue = Regex(
        """(?i)\b(?:bearer|basic)\s+[A-Za-z0-9._~+/=-]{6,}"""
    )

    fun redact(message: String, maxLength: Int = 160): String {
        if (message.isBlank()) return ""
        return message
            .replace(url, "[آدرس حذف شد]")
            .replace(assignedSecret) { match -> "${match.groupValues[1]}[حذف شد]" }
            .replace(authorizationValue, "[اعتبارنامه حذف شد]")
            .take(maxLength.coerceIn(0, 4000))
    }
}
