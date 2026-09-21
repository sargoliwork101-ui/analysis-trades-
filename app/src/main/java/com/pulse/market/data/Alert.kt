package com.pulse.market.data

import kotlinx.serialization.Serializable

/** شرط فعال شدن هشدار */
@Serializable
enum class AlertCondition {
    /** قیمت بالاتر از حد مشخص برود */
    ABOVE,

    /** قیمت پایین‌تر از حد مشخص بیاید */
    BELOW,

    /** درصد رشد روزانه از حد مشخص بیشتر شود */
    PCT_UP,

    /** درصد افت روزانه از حد مشخص بیشتر شود */
    PCT_DOWN
}

/**
 * یک قانون هشدار قیمت.
 * می‌توانی برای هر نماد جداگانه تعیین کنی در چه بازه‌ی زمانی و چه روزهایی فعال باشد.
 */
@Serializable
data class AlertRule(
    val id: String,
    val symbolCode: String,
    val symbolLabel: String,
    val sourceId: String,
    val condition: AlertCondition,
    val threshold: Double,
    /** زمان‌بندی فعال باشد؟ اگر نه، تمام شبانه‌روز بررسی می‌شود */
    val scheduleEnabled: Boolean = true,
    /** دقیقه‌ی شروع بازه از نیمه‌شب، مثلاً 9*60 = 09:00 */
    val fromMinute: Int = 9 * 60,
    /** دقیقه‌ی پایان بازه، مثلاً 17*60 = 17:00 */
    val toMinute: Int = 17 * 60,
    /** روزهای هفته — ۰=شنبه تا ۶=جمعه */
    val days: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    /** حداقل فاصله بین دو نوتیف برای همین قانون (دقیقه) — جلوگیری از اسپم */
    val cooldownMin: Int = 30,
    /** فقط لحظه‌ی رد کردن حد نوتیف بده (نه تا وقتی بالای حد است) */
    val onlyOnCross: Boolean = true,
    val enabled: Boolean = true
) {
    fun scheduleText(persian: Boolean = false): String {
        if (!scheduleEnabled) return "همیشه فعال"
        val daysText = when (days) {
            setOf(0, 1, 2, 3, 4, 5, 6) -> "هر روز"
            setOf(0, 1, 2, 3, 4) -> "شنبه تا چهارشنبه"
            setOf(0, 1, 2, 3, 4, 5) -> "شنبه تا پنجشنبه"
            emptySet<Int>() -> "هیچ روزی"
            else -> days.sorted().joinToString("،") { AlertRule.dayName(it) }
        }
        val window = if (noTimeLimit) "شبانه‌روز" else "${hhmm(fromMinute)} تا ${hhmm(toMinute)}"
        return "$daysText، $window"
    }

    val noTimeLimit: Boolean
        get() = fromMinute == toMinute

    companion object {
        private val fa = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

        fun dayName(index: Int): String = fa.getOrElse(index) { "?" }

        fun hhmm(minuteOfDay: Int): String =
            String.format(java.util.Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

        fun conditionText(condition: AlertCondition, threshold: Double, unit: String, persian: Boolean): String {
            val t = com.pulse.market.ui.Format.price(threshold, persian)
            return when (condition) {
                AlertCondition.ABOVE -> "قیمت ≥ $t $unit"
                AlertCondition.BELOW -> "قیمت ≤ $t $unit"
                AlertCondition.PCT_UP -> "رشد ≥ $t٪"
                AlertCondition.PCT_DOWN -> "افت ≥ $t٪"
            }
        }
    }
}
