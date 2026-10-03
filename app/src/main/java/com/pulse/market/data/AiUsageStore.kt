package com.pulse.market.data

import android.content.Context
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * شمارنده‌ی مصرف توکن هوش مصنوعی — فقط روی همین گوشی.
 *
 * چرا: هزینه‌ی هر درخواست AI مستقیم از حساب خودِ کاربر کم می‌شود، ولی تا این‌جا هیچ‌جای
 * برنامه نشان نمی‌داد که یک روز چند توکن رفته است. بدون این عدد، «کم‌مصرف‌کردن» حدس و
 * گمان است. حالا مصرف واقعیِ گزارش‌شده توسط خود سرویس (فیلد usage در پاسخ) روزبه‌روز
 * نگه داشته می‌شود تا هم دیده شود و هم بتوان سقف روزانه گذاشت.
 *
 * هیچ متنی از درخواست یا پاسخ ذخیره نمی‌شود؛ فقط عدد.
 */
object AiUsageStore {

    private const val PREF = "pulse_ai_usage"
    private const val KEY_DAYS = "days"
    private const val MAX_DAYS = 14

    /** مصرف یک شبانه‌روز. [day] به شکل yyyyMMdd و بر اساس ساعت محلی گوشی است. */
    data class Day(
        val day: Int,
        val input: Int = 0,
        val output: Int = 0,
        val requests: Int = 0
    ) {
        val total: Int get() = input + output
    }

    /** کلید روز جاری؛ تغییر روز به‌صورت خودکار شمارنده را صفر می‌کند. */
    fun dayKey(millis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.YEAR) * 10_000 +
                (cal.get(Calendar.MONTH) + 1) * 100 +
                cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    private fun read(context: Context): List<Day> {
        val raw = prefs(context).getString(KEY_DAYS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val day = obj.optInt("day")
                    if (day <= 0) continue
                    add(
                        Day(
                            day = day,
                            input = obj.optInt("input").coerceAtLeast(0),
                            output = obj.optInt("output").coerceAtLeast(0),
                            requests = obj.optInt("requests").coerceAtLeast(0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun write(context: Context, days: List<Day>) {
        val bounded = days.sortedByDescending { it.day }.take(MAX_DAYS)
        val array = JSONArray()
        for (day in bounded) {
            array.put(
                JSONObject()
                    .put("day", day.day)
                    .put("input", day.input)
                    .put("output", day.output)
                    .put("requests", day.requests)
            )
        }
        prefs(context).edit().putString(KEY_DAYS, array.toString()).apply()
    }

    /** ثبت مصرف یک درخواست. مقدار منفی یا صفرِ هر دو فیلد باز هم یک «درخواست» است. */
    @Synchronized
    fun record(context: Context, input: Int, output: Int, at: Long = System.currentTimeMillis()) {
        val key = dayKey(at)
        val days = read(context).toMutableList()
        val index = days.indexOfFirst { it.day == key }
        val current = if (index >= 0) days[index] else Day(day = key)
        val updated = current.copy(
            input = (current.input + input.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE / 2),
            output = (current.output + output.coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE / 2),
            requests = current.requests + 1
        )
        if (index >= 0) days[index] = updated else days.add(updated)
        write(context, days)
    }

    /** مصرف امروز (اگر هنوز درخواستی نرفته باشد، صفر). */
    fun today(context: Context, at: Long = System.currentTimeMillis()): Day {
        val key = dayKey(at)
        return read(context).firstOrNull { it.day == key } ?: Day(day = key)
    }

    /** روزهای اخیر، تازه‌ترین اول. */
    fun recent(context: Context): List<Day> = read(context).sortedByDescending { it.day }

    /**
     * مجموع توکن هفت روز گذشته. عمداً «هفت رکورد آخر» نیست: اگر چند روز هیچ درخواستی
     * نرفته باشد، رکوردهای قدیمی‌تر نباید در آمار «هفت روز اخیر» شمرده شوند.
     */
    fun weekTotal(context: Context, now: Long = System.currentTimeMillis()): Int {
        val cutoff = dayKey(now - 6L * 24 * 60 * 60 * 1000)
        return recent(context).filter { it.day >= cutoff }.sumOf { it.total }
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_DAYS).apply()
    }

    /** متن کوتاه فارسی برای نمایش در تنظیمات. */
    fun summaryText(today: Day, weekTotal: Int, budget: Int): String {
        val base = "مصرف امروز: ${group(today.total)} توکن در ${group(today.requests)} درخواست " +
                "(ورودی ${group(today.input)} • خروجی ${group(today.output)}) — هفت روز اخیر: ${group(weekTotal)} توکن"
        return if (budget > 0) {
            val percent = ((today.total.toLong() * 100) / budget.toLong()).toInt().coerceIn(0, 999)
            "$base\nسقف روزانه: ${group(budget)} توکن — ${group(percent)}٪ مصرف شده"
        } else {
            base
        }
    }

    private fun group(value: Int): String =
        String.format(Locale.US, "%,d", value).replace(',', '٬')
}
