package com.pulse.market.ui

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.abs

object Format {

    private val grouped = DecimalFormat("#,##0")
    private val two = DecimalFormat("#,##0.00")
    private val small = DecimalFormat("0.######")

    /** قیمت را خوانا می‌کند: ۱٬۲۵۰٬۰۰۰ / ۹۸٬۴۵۰ / ۰٫۰۰۰۱۲۳ */
    fun price(value: Double?, persian: Boolean = false): String {
        if (value == null) return "—"
        val a = abs(value)
        val text = when {
            a >= 1000 -> grouped.format(value)
            a >= 1 -> two.format(value)
            else -> small.format(value)
        }
        return if (persian) toPersianDigits(text) else text
    }

    fun pct(value: Double?, persian: Boolean = false): String {
        if (value == null) return ""
        val arrow = when {
            value > 0.0001 -> "▲"
            value < -0.0001 -> "▼"
            else -> "•"
        }
        val text = String.format(Locale.US, "%.2f%%", abs(value))
        return "$arrow ${if (persian) toPersianDigits(text) else text}"
    }

    fun time(ts: Long): String {
        if (ts <= 0L) return "--:--"
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        return String.format(
            Locale.US, "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)
        )
    }

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder()
        for (c in input) sb.append(if (c in '0'..'9') '۰' + (c - '0') else c)
        return sb.toString()
    }
}
