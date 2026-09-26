package com.pulse.market.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun rejectsArbitraryApkAssetEvenInsideOfficialRelease() {
        val assets = JSONArray(
            """[{"name":"untrusted.apk","browser_download_url":"https://github.com/sargoliwork101-ui/analysis-trades-/releases/download/v1.15/untrusted.apk"}]"""
        )
        assertNull(AppUpdater.pickApk(assets))
    }

    @Test
    fun acceptsOnlyStandardApkAndValidSha256() {
        val sha = "ab".repeat(32)
        val assets = JSONArray(
            """[{"name":"PulseMarket-v1.15.apk","browser_download_url":"https://github.com/sargoliwork101-ui/analysis-trades-/releases/download/v1.15/PulseMarket-v1.15.apk","digest":"sha256:$sha"}]"""
        )
        assertEquals(
            "https://github.com/sargoliwork101-ui/analysis-trades-/releases/download/v1.15/PulseMarket-v1.15.apk" to sha,
            AppUpdater.pickApk(assets)
        )
    }
}
