package com.pulse.market.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * تبدیلِ تاریخِ میلادی ↔ شمسی (جلالی) — بدونِ هیچ کتابخانه‌ی بیرونی.
 *
 * الگوریتمِ استانده‌ی «بروجردی» که در بسیاری از کتابخانه‌های PHP/JS استفاده می‌شود؛
 * برای بازه‌ی تاریخ‌های رایج (۱۳۰۰ تا ۱۵۰۰ شمسی) دقیق است.
 */
object JalaliDate {

    /** نام‌های ماه‌های شمسی */
    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** سه‌تایی سال/ماه/روزِ شمسی */
    data class Ymd(val year: Int, val month: Int, val day: Int)

    private val G_DAYS_IN_MONTH = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    /** میلادی → شمسی */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Ymd {
        var jy: Int
        var gy2v = gy
        if (gy > 1600) {
            jy = 979
            gy2v -= 1600
        } else {
            jy = 0
            gy2v -= 621
        }
        val gy2 = if (gm > 2) gy2v + 1 else gy2v
        var days = 365 * gy2v + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 -
            80 + gd + G_DAYS_IN_MONTH[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Ymd(jy, jm, jd)
    }

    /** شمسی → میلادی */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Ymd {
        var gy: Int
        var jyv = jy
        if (jy > 979) {
            gy = 1600
            jyv -= 979
        } else {
            gy = 621
        }
        var days = 365 * jyv + (jyv / 33) * 8 + (jyv % 33 + 3) / 4 + 78 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        gy += 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = gy % 4 == 0 && gy % 100 != 0 || gy % 400 == 0
        val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > monthDays[gm]) {
            gd -= monthDays[gm]
            gm++
        }
        return Ymd(gy, gm, gd)
    }

    /** آیا سالِ شمسی کبیسه است؟ (بر اساسِ خودِ تبدیل تا با پیکر ناسازگار نشود) */
    fun isLeap(jy: Int): Boolean {
        val g = jalaliToGregorian(jy, 12, 30)
        val back = gregorianToJalali(g.year, g.month, g.day)
        return back.year == jy && back.month == 12 && back.day == 30
    }

    /** تعدادِ روزهای یک ماهِ شمسی */
    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    /** میلی‌ثانیه‌ی زمان (منطقه‌ی محلی) → سال/ماه/روزِ شمسی */
    fun fromMillis(ts: Long): Ymd {
        // عمداً GregorianCalendar صریح: روی دستگاهی که locale پیش‌فرضش تقویم دیگری
        // دارد (مثلاً th_TH با تقویم بودایی)، Calendar.getInstance() سالِ غیرمیلادی
        // برمی‌گرداند و همه‌ی تاریخ‌های شمسی برنامه غلط می‌شد.
        val cal = GregorianCalendar(TimeZone.getDefault()).apply { timeInMillis = ts }
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** سال/ماه/روزِ شمسی → میلی‌ثانیه‌ی ابتدای همان روز (منطقه‌ی محلی) */
    fun toMillis(jy: Int, jm: Int, jd: Int): Long {
        val g = jalaliToGregorian(jy, jm, jd)
        val cal = GregorianCalendar(TimeZone.getDefault()).apply {
            clear()
            set(g.year, g.month - 1, g.day, 0, 0, 0)
        }
        return cal.timeInMillis
    }
}
