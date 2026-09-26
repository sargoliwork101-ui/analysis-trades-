package com.pulse.market.data

import kotlinx.serialization.Serializable
import kotlin.math.abs

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
        val text = "$daysText، $window"
        return if (persian) com.pulse.market.ui.Format.toPersianDigits(text) else text
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
                AlertCondition.PCT_UP -> "رشد ≥ ${com.pulse.market.ui.Format.price(abs(threshold), persian)}٪"
                AlertCondition.PCT_DOWN -> "افت ≥ ${com.pulse.market.ui.Format.price(abs(threshold), persian)}٪"
            }
        }
    }
}

/**
 * منطق خالصِ هشدارها؛ جدا از Android تا هم رفتار عبور از حد دقیق باشد و هم بتوان
 * آن را با تست واحد قفل کرد.
 */
object AlertLogic {

    /** مقداری که برای شرط باید با حد مقایسه شود (قیمت یا درصد تغییر). */
    fun metric(rule: AlertRule, price: Double, changePct: Double?): Double? =
        when (rule.condition) {
            AlertCondition.ABOVE, AlertCondition.BELOW -> price
            AlertCondition.PCT_UP, AlertCondition.PCT_DOWN -> changePct
        }?.takeIf { it.isFinite() }

    /** آیا یک مقدارِ هم‌نوعِ شرط، حد را رد کرده است؟ */
    fun isTriggered(rule: AlertRule, metric: Double): Boolean = when (rule.condition) {
        AlertCondition.ABOVE -> metric >= rule.threshold
        AlertCondition.BELOW -> metric <= rule.threshold
        AlertCondition.PCT_UP -> metric >= abs(rule.threshold)
        AlertCondition.PCT_DOWN -> metric <= -abs(rule.threshold)
    }

    /**
     * بازه‌ی زمانی معمولی و شب‌گذر (مثلاً ۲۲:۰۰ تا ۰۶:۰۰).
     * مجموعه‌ی روزِ خالی واقعاً یعنی «هیچ روزی»، مطابق متن رابط کاربری.
     */
    fun isInsideSchedule(rule: AlertRule, dayIndex: Int, minuteOfDay: Int): Boolean {
        if (!rule.scheduleEnabled) return true
        if (dayIndex !in rule.days) return false
        if (rule.noTimeLimit) return true

        val minute = minuteOfDay.coerceIn(0, 1439)
        val from = rule.fromMinute.coerceIn(0, 1439)
        val to = rule.toMinute.coerceIn(0, 1439)
        return if (from <= to) minute in from..to else (minute >= from || minute <= to)
    }
}
