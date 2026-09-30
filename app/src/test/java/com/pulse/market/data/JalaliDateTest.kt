package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** تستِ تبدیلِ تاریخِ میلادی ↔ شمسی با تاریخ‌های شناخته‌شده. */
class JalaliDateTest {

    @Test
    fun gregorianToJalaliKnownDates() {
        // نوروزِ ۱۴۰۳ = ۲۰ مارس ۲۰۲۴
        assertEquals(JalaliDate.Ymd(1403, 1, 1), JalaliDate.gregorianToJalali(2024, 3, 20))
        // نوروزِ ۱۴۰۰ = ۲۱ مارس ۲۰۲۱
        assertEquals(JalaliDate.Ymd(1400, 1, 1), JalaliDate.gregorianToJalali(2021, 3, 21))
        // اولِ ژانویه‌ی ۲۰۰۰
        assertEquals(JalaliDate.Ymd(1378, 10, 11), JalaliDate.gregorianToJalali(2000, 1, 1))
        // انقلابِ ۵۷ = ۱۱ فوریه‌ی ۱۹۷۹ = ۲۲ بهمن ۱۳۵۷
        assertEquals(JalaliDate.Ymd(1357, 11, 22), JalaliDate.gregorianToJalali(1979, 2, 11))
    }

    @Test
    fun jalaliToGregorianKnownDates() {
        assertEquals(JalaliDate.Ymd(2024, 3, 20), JalaliDate.jalaliToGregorian(1403, 1, 1))
        assertEquals(JalaliDate.Ymd(2021, 3, 21), JalaliDate.jalaliToGregorian(1400, 1, 1))
        assertEquals(JalaliDate.Ymd(1979, 2, 11), JalaliDate.jalaliToGregorian(1357, 11, 22))
    }

    @Test
    fun roundTripIsStableAcrossManyDates() {
        // هر روز از چند سالِ متوالی باید بعد از رفت‌وبرگشت همان بماند
        for (jy in 1395..1410) {
            for (jm in 1..12) {
                val dim = JalaliDate.daysInMonth(jy, jm)
                for (jd in intArrayOf(1, 15, dim)) {
                    val g = JalaliDate.jalaliToGregorian(jy, jm, jd)
                    val back = JalaliDate.gregorianToJalali(g.year, g.month, g.day)
                    assertEquals("failed for $jy/$jm/$jd", JalaliDate.Ymd(jy, jm, jd), back)
                }
            }
        }
    }

    @Test
    fun leapYearsAndMonthLengths() {
        // ماه‌های ۱..۶ سی‌ویک روز، ۷..۱۱ سی روز
        assertEquals(31, JalaliDate.daysInMonth(1403, 1))
        assertEquals(31, JalaliDate.daysInMonth(1403, 6))
        assertEquals(30, JalaliDate.daysInMonth(1403, 7))
        assertEquals(30, JalaliDate.daysInMonth(1403, 11))
        // ۱۴۰۳ کبیسه است → اسفند ۳۰ روز
        assertTrue(JalaliDate.isLeap(1403))
        assertEquals(30, JalaliDate.daysInMonth(1403, 12))
        // ۱۴۰۴ کبیسه نیست → اسفند ۲۹ روز
        assertFalse(JalaliDate.isLeap(1404))
        assertEquals(29, JalaliDate.daysInMonth(1404, 12))
    }

    @Test
    fun millisRoundTripToStartOfDay() {
        val ms = JalaliDate.toMillis(1403, 7, 8)
        val ymd = JalaliDate.fromMillis(ms)
        assertEquals(JalaliDate.Ymd(1403, 7, 8), ymd)
    }
}
