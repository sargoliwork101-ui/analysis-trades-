package com.pulse.market.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تست‌های واحدِ فرمت‌کننده‌ی اعداد/زمان.
 *
 * چرا مهم است: خروجی این توابع مستقیم در ویجت، اعلان‌ها و صفحه‌ی تنظیمات دیده
 * می‌شود و باید مستقل از زبان گوشی ثابت بماند (نمادهای Locale.US). این تست‌ها فقط
 * منطق خالص را می‌سنجند و به Android وابسته نیستند.
 */
class FormatTest {

    @Test
    fun persianDigitsMapOnlyDigits() {
        assertEquals("۱۲:۳۰", Format.toPersianDigits("12:30"))
        // کاراکترهای غیرعددی دست‌نخورده می‌مانند
        assertEquals("۵٪ و بیشتر", Format.toPersianDigits("5٪ و بیشتر"))
        assertEquals("", Format.toPersianDigits(""))
    }

    @Test
    fun priceHandlesMissingAndRanges() {
        assertEquals("—", Format.price(null))
        assertEquals("—", Format.price(Double.NaN))
        // عدد صحیحِ بزرگ بدون اعشار، با جداکننده‌ی هزارگان
        assertEquals("1,250,000", Format.price(1_250_000.0))
        // کریپتوی بالای هزار با اعشار حفظ می‌شود
        assertEquals("64,210.55", Format.price(64_210.55))
        // بین ۱ و ۱۰۰۰ همیشه دو رقم اعشار
        assertEquals("5.00", Format.price(5.0))
        // زیر یک، اعشار ریز
        assertEquals("0.000123", Format.price(0.000123))
    }

    @Test
    fun priceCompactUsesVolumeStyleAboveThousand() {
        assertEquals("64.2K", Format.price(64_210.55, compact = true))
    }

    @Test
    fun priceInPersianConvertsDigitsButKeepsGrouping() {
        assertEquals("۱,۰۰۰", Format.price(1000.0, persian = true))
    }

    @Test
    fun pctShowsDirectionArrowAndAbsoluteValue() {
        assertEquals("▲ 5.00%", Format.pct(5.0))
        assertEquals("▼ 2.50%", Format.pct(-2.5))
        assertEquals("• 0.00%", Format.pct(0.0))
        assertEquals("", Format.pct(null))
        assertEquals("", Format.pct(Double.POSITIVE_INFINITY))
    }

    @Test
    fun volumeIsCompactWithSuffixes() {
        assertEquals("—", Format.volume(null))
        assertEquals("950", Format.volume(950.0))
        assertEquals("1.5K", Format.volume(1_500.0))
        assertEquals("1.5M", Format.volume(1_500_000.0))
        assertEquals("2B", Format.volume(2_000_000_000.0))
    }

    @Test
    fun volumeInPersianUsesPersianSuffixAndDecimalMark() {
        assertEquals("۱٫۵ میلیون", Format.volume(1_500_000.0, persian = true))
    }

    @Test
    fun timeHandlesEmptyTimestamp() {
        assertEquals("--:--", Format.time(0L))
        assertEquals("--:--", Format.time(-1L))
    }

    @Test
    fun dateTimeHandlesEmptyTimestamp() {
        assertEquals("—", Format.dateTime(0L))
    }
}
