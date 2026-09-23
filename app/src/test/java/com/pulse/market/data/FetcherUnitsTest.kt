package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * تست واحد و ضریب «هر نماد».
 * ریشه‌ی باگ قدیمی: منبع TGJU برای همه‌ی نمادها واحد «تومان» و ضریب ۰٫۱ داشت،
 * پس «انس طلای جهانی» (که دلاری است) ۰٫۱ برابر و با واحد تومان نشان داده می‌شد.
 */
class FetcherUnitsTest {

    private val tgju = SourceDef(
        id = "tgju",
        title = "طلا و ارز",
        urlTemplate = "https://call1.tgju.org/ajax.json",
        scale = 0.1,
        unit = "تومان"
    )

    private val ons = SymbolDef("ons", "انس طلا (جهانی)", unit = "$", scale = 1.0)
    private val sekke = SymbolDef("sekke", "سکه امامی")

    @Test
    fun symbolUnitWinsOverSourceUnit() {
        assertEquals("$", Fetcher.unitOf(tgju, ons))
        assertEquals(1.0, Fetcher.scaleOf(tgju, ons), 1e-9)
    }

    @Test
    fun sourceUnitUsedWhenSymbolHasNone() {
        assertEquals("تومان", Fetcher.unitOf(tgju, sekke))
        assertEquals(0.1, Fetcher.scaleOf(tgju, sekke), 1e-9)
    }

    @Test
    fun dollarGoldOunceIsNotScaledLikeRial() {
        // قیمت خام TGJU برای انس: 4310.94 → باید همان 4310.94 دلار بماند
        val raw = 4310.94
        assertEquals(4310.94, raw * Fetcher.scaleOf(tgju, ons), 1e-6)
        // و سکه امامی با ضریب ۰٫۱ تومانی می‌شود
        assertEquals(235510.0, 2355100000.0 * Fetcher.scaleOf(tgju, sekke), 1e-3)
    }
}
