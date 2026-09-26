package com.pulse.market.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object Format {

    // DecimalFormat هم به Locale پیش‌فرض وابسته است و هم thread-safe نیست؛ ویجت،
    // سرویس و اعلان‌ها می‌توانند هم‌زمان از این شیء استفاده کنند. نمادهای US ظاهر
    // فعلی را روی همه‌ی زبان‌های گوشی ثابت نگه می‌دارند و safeFormat جلوی race را می‌گیرد.
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val grouped = DecimalFormat("#,##0", symbols)
    private val two = DecimalFormat("#,##0.00", symbols)
    private val small = DecimalFormat("0.######", symbols)
    private val oneD = DecimalFormat("0.#", symbols)

    private fun DecimalFormat.safeFormat(value: Double): String =
        synchronized(this) { format(value) }

    /** قیمت را خوانا می‌کند: ۱٬۲۵۰٬۰۰۰ / ۹۸٬۴۵۰ / ۰٫۰۰۰۱۲۳ — با compact: 64.2K / ۹۸٫۴ هزار
     *  مقادیر اعشاریِ بالای هزار گرد نمی‌شوند (کریپتو: 64,210.55)؛ اعداد صحیح (بورس/طلا) همیشه بدون اعشار */
    fun price(value: Double?, persian: Boolean = false, compact: Boolean = false): String {
        if (value == null) return "—"
        if (compact && abs(value) >= 1000) return volume(value, persian)
        val a = abs(value)
        val text = when {
            a >= 1000 -> if (value % 1.0 != 0.0) two.safeFormat(value) else grouped.safeFormat(value)
            a >= 1 -> two.safeFormat(value)
            else -> small.safeFormat(value)
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

    /** حجم معاملات/حجم ۲۴ ساعت — فرمت فشرده: ۱۲٫۴ میلیون / 3.2B */
    fun volume(value: Double?, persian: Boolean = false): String {
        if (value == null) return "—"
        val a = abs(value)
        return when {
            a >= 1_000_000_000 -> compactNum(value / 1_000_000_000, if (persian) " میلیارد" else "B", persian)
            a >= 1_000_000 -> compactNum(value / 1_000_000, if (persian) " میلیون" else "M", persian)
            a >= 1_000 -> compactNum(value / 1_000, if (persian) " هزار" else "K", persian)
            else -> grouped.safeFormat(value).let { if (persian) toPersianDigits(it) else it }
        }
    }

    private fun compactNum(v: Double, suffix: String, persian: Boolean): String {
        val n = if (abs(v) >= 100) grouped.safeFormat(v) else oneD.safeFormat(v)
        val out = n + suffix
        return if (persian) toPersianDigits(out).replace(".", "٫") else out
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
