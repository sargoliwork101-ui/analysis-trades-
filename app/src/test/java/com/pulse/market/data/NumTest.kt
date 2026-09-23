package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** تست‌های پاک‌سازی عدد — سایت‌ها هر کدام یک شکل عدد می‌دهند */
class NumTest {

    @Test
    fun acceptsEnglishGroupedNumbers() {
        assertEquals(1250000.0, Num.parse("1,250,000")!!, 1e-9)
    }

    @Test
    fun acceptsPersianDigitsAndSeparators() {
        assertEquals(4310.94, Num.parse("۴٬۳۱۰.۹۴")!!, 1e-6)
        assertEquals(238995000.0, Num.parse("۲۳۸،۹۹۵،۰۰۰")!!, 1e-6)
    }

    @Test
    fun stripsCurrencyAndPercentSigns() {
        assertEquals(2.31, Num.parse("۲.۳۱%")!!, 1e-9)
        assertEquals(64.99, Num.parse("\$64.99")!!, 1e-9)
    }

    @Test
    fun keepsSigns() {
        assertEquals(-1.5, Num.parse("-1.5")!!, 1e-9)
        assertEquals(120.0, Num.parse("+120")!!, 1e-9)
    }

    @Test
    fun nonNumericIsNull() {
        assertNull(Num.parse("قیمت نامشخص"))
        assertNull(Num.parse(""))
        assertNull(Num.parse(null))
    }
}
