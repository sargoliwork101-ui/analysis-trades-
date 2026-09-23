package com.pulse.market.data

import java.util.Calendar
import java.util.TimeZone

/**
 * وضعیت باز/بسته بودن بازارها.
 *
 * تا نسخه‌ی ۱٫۴ سرصفحه‌ی ویجت همیشه وضعیت «بورس تهران» را نشان می‌داد؛
 * از این پس وضعیت هر بازاری که در همین ویجت فعال است نمایش داده می‌شود:
 * بورس تهران، کریپتو، سهام آمریکا، طلا و ارز و بازارهای جهانی — هر کدام باز یا بسته.
 *
 * ساعت‌ها تقریبی و بدون احتساب تعطیلات رسمی‌اند؛ برای کریپتو بازار
 * همیشه باز است.
 */
object MarketStatus {

    /** وضعیت یک بازار */
    data class State(
        /** کلید یکتا — بازارهای تکراری (مثل بورس + شاخص کل) فقط یک بار نمایش داده می‌شوند */
        val key: String,
        /** نام فارسی کوتاه بازار */
        val label: String,
        val open: Boolean,
        /** متن حالت باز — برای کریپتو «باز ۲۴/۷» */
        val openText: String = "باز"
    )

    /** بورس تهران: شنبه تا چهارشنبه، ۹:۰۰ تا ۱۲:۳۰ به وقت ایران */
    private fun tseOpen(): Boolean = openIn(
        "Asia/Tehran",
        workDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY),
        fromMinute = 9 * 60, toMinute = 12 * 60 + 30
    )

    /** سهام آمریکا: دوشنبه تا جمعه، ۹:۳۰ تا ۱۶:۰۰ به وقت نیویورک */
    private fun usOpen(): Boolean = openIn(
        "America/New_York",
        workDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
        fromMinute = 9 * 60 + 30, toMinute = 16 * 60
    )

    /** طلا و ارز (بازار آزاد): شنبه تا پنجشنبه، حدود ۹ تا ۲۱ — ساعات رسمی ندارد، تقریبی است */
    private fun goldFxOpen(): Boolean = openIn(
        "Asia/Tehran",
        workDays = setOf(
            Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY,
            Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY
        ),
        fromMinute = 9 * 60, toMinute = 21 * 60
    )

    /**
     * بازارهای جهانی (انس طلا، نقره، نفت، فارکس): یکشنبه ۱۸:۰۰ نیویورک باز می‌شود و
     * جمعه ۱۷:۰۰ نیویورک بسته می‌شود — وسط هفته ۲۴ ساعته باز است.
     * (تعطیلات رسمی آمریکا حساب نشده؛ تقریبی است.)
     */
    private fun globalOpen(): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> false
            Calendar.SUNDAY -> minute >= 18 * 60
            Calendar.FRIDAY -> minute < 17 * 60
            else -> true
        }
    }

    /** آیا «الان» در روز کاری و بازه‌ی ساعتیِ این بازار هستیم؟ */
    private fun openIn(zone: String, workDays: Set<Int>, fromMinute: Int, toMinute: Int): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(zone))
        if (cal.get(Calendar.DAY_OF_WEEK) !in workDays) return false
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return minute in fromMinute..toMinute
    }

    /** بازارِ هر منبع — از ماژول مرجع MarketKind؛ منبع دلخواه بازار ندارد */
    fun forSource(sourceId: String): State? = when (marketKindOf(sourceId)) {
        MarketKind.TSE -> State(MarketKind.TSE.key, MarketKind.TSE.label, tseOpen())
        MarketKind.CRYPTO -> State(
            MarketKind.CRYPTO.key, MarketKind.CRYPTO.label, open = true, openText = "باز ۲۴/۷"
        )
        MarketKind.US -> State(MarketKind.US.key, MarketKind.US.label, usOpen())
        MarketKind.GOLD_FX -> State(MarketKind.GOLD_FX.key, MarketKind.GOLD_FX.label, goldFxOpen())
        MarketKind.GLOBAL -> State(MarketKind.GLOBAL.key, MarketKind.GLOBAL.label, globalOpen())
        else -> null
    }

    /**
     * متن وضعیت بازارهای فعالِ همین ویجت — برای سرصفحه‌ی ویجت:
     *  کامل:  «بورس: بسته 🔴 • کریپتو: باز ۲۴/۷ 🟢 • آمریکا: باز 🟢»
     *  کوتاه: «بورس 🔴 • کریپتو 🟢 • آمریکا 🟢»
     *
     * @param short برای ویجت‌های باریک‌تر — فقط نام بازار و چراغ، بدون کلمه‌ی باز/بسته
     */
    fun headerFor(sourceIds: List<String>, short: Boolean): String {
        val states = sourceIds.mapNotNull { forSource(it) }.distinctBy { it.key }
        if (states.isEmpty()) return ""
        return states.joinToString(" • ") { st ->
            val emoji = if (st.open) "🟢" else "🔴"
            if (short) "${st.label} $emoji"
            else "${st.label}: ${if (st.open) st.openText else "بسته"} $emoji"
        }
    }
}
