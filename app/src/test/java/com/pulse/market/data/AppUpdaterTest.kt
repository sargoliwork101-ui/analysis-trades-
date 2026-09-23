package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های مقایسه‌ی نسخه در آپدیت درون‌برنامه‌ای */
class AppUpdaterTest {

    @Test
    fun parsesTagAndPlainVersions() {
        assertEquals(listOf(1, 13), AppUpdater.parseVersion("v1.13"))
        assertEquals(listOf(1, 13), AppUpdater.parseVersion("1.13"))
        assertEquals(listOf(1, 2, 1), AppUpdater.parseVersion("1.2.1"))
        assertEquals(listOf(1, 14), AppUpdater.parseVersion("v1.14"))
        assertEquals(emptyList<Int>(), AppUpdater.parseVersion("release-latest"))
    }

    @Test
    fun comparesVersionsProperly() {
        assertTrue(AppUpdater.isNewer(listOf(1, 14), listOf(1, 13)))
        assertTrue(AppUpdater.isNewer(listOf(2), listOf(1, 99)))
        assertFalse(AppUpdater.isNewer(listOf(1, 13), listOf(1, 13)))
        // نسخه‌ی نصب‌شده‌ی سه‌بخشی از نسخه‌ی ریلیز دوبخشی جدیدتر است
        assertFalse(AppUpdater.isNewer(listOf(1, 13), listOf(1, 13, 1)))
        assertTrue(AppUpdater.isNewer(listOf(1, 13, 1), listOf(1, 13)))
    }
}
