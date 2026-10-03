package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** شمارنده‌ی مصرف توکن: کلید روز و متن خلاصه (بخش‌های مستقل از دیسک). */
class AiUsageStoreTest {

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun dayKeyIsGregorianAndChangesExactlyOncePerDay() {
        val previousLocale = java.util.Locale.getDefault()
        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val noon = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, 9, 3, 12, 0, 0)
            }.timeInMillis
            assertEquals(20261003, AiUsageStore.dayKey(noon))
            assertEquals(AiUsageStore.dayKey(noon), AiUsageStore.dayKey(noon + 6 * 60 * 60 * 1000L))
            assertNotEquals(AiUsageStore.dayKey(noon), AiUsageStore.dayKey(noon + day))
            // کلید باید با locale دستگاه عوض نشود، وگرنه شمارنده بی‌دلیل صفر می‌شود.
            java.util.Locale.setDefault(java.util.Locale("th", "TH"))
            assertEquals(20261003, AiUsageStore.dayKey(noon))
            // کلیدها مرتب‌اند، پس مقایسه‌ی «شش روز قبل» برای جمع هفتگی درست کار می‌کند.
            assertTrue(AiUsageStore.dayKey(noon - 6 * day) < AiUsageStore.dayKey(noon))
        } finally {
            java.util.Locale.setDefault(previousLocale)
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun summaryTextShowsTotalsAndBudgetShare() {
        val today = AiUsageStore.Day(day = 20261003, input = 12_000, output = 3_000, requests = 7)
        val withoutBudget = AiUsageStore.summaryText(today, weekTotal = 40_000, budget = 0)
        assertTrue(withoutBudget.contains("15"))
        assertTrue(withoutBudget.contains("مصرف امروز"))
        assertTrue(!withoutBudget.contains("سقف روزانه"))

        val withBudget = AiUsageStore.summaryText(today, weekTotal = 40_000, budget = 30_000)
        assertTrue(withBudget.contains("سقف روزانه"))
        // ۱۵۰۰۰ از ۳۰۰۰۰ یعنی ۵۰ درصد.
        assertTrue(withBudget.contains("50"))
    }

    @Test
    fun totalIsInputPlusOutput() {
        assertEquals(1_500, AiUsageStore.Day(day = 1, input = 1_000, output = 500).total)
        assertEquals(0, AiUsageStore.Day(day = 1).total)
    }
}
