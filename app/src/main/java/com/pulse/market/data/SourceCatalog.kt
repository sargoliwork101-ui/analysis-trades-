package com.pulse.market.data

import com.pulse.market.data.FetchKind.JSON_REST
import com.pulse.market.data.FetchKind.TSE_TSETMC

/**
 * لیست منابع آماده‌ی داخل اپ.
 * کاربر از این لیست انتخاب می‌کند یا خودش یک «منبع دلخواه» با آدرس و مسیر JSON می‌سازد.
 *
 * فقط منابعی می‌مانند که واقعاً کار می‌کنند (v1.12): کریپتو (CoinGecko)، بورس تهران
 * (TSETMC + شاخص کل) و طلا و ارز (TGJU). Yahoo و آینه‌ی Navasan حذف شدند.
 */
object SourceCatalog {

    val builtIn: List<SourceDef> = listOf(

        // ────────────────────────── کریپتو ──────────────────────────
        SourceDef(
            id = "crypto_coingecko",
            title = "کریپتو — CoinGecko",
            subtitle = "قیمت دلاری + تغییر ۲۴ ساعت • به‌روزرسانی لحظه‌ای",
            kind = JSON_REST,
            urlTemplate = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids={symbol}&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true",
            // همه‌ی نمادها با یک درخواست (جلوگیری از محدودیت تعداد درخواست CoinGecko)
            batchTemplate = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids={symbols}&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true",
            pricePath = "{symbol}.usd",
            changePath = "{symbol}.usd_24h_change",
            changeMode = ChangeMode.PERCENT,
            volumePath = "{symbol}.usd_24h_vol",
            unit = "$",
            symbols = listOf(
                SymbolDef("bitcoin", "بیت‌کوین"),
                SymbolDef("ethereum", "اتریوم"),
                SymbolDef("tether", "تتر"),
                SymbolDef("binancecoin", "بایننس‌کوین"),
                SymbolDef("solana", "سولانا"),
                SymbolDef("ripple", "ریپل"),
                SymbolDef("dogecoin", "دوج‌کوین"),
                SymbolDef("toncoin", "تون‌کوین")
            )
        ),

        // ─────────────────────── بورس تهران ───────────────────────
        SourceDef(
            id = "tse_tsetmc",
            title = "بورس تهران — TSETMC",
            subtitle = "قیمت پایانی + درصد تغییر روز (ریال)",
            kind = TSE_TSETMC,
            urlTemplate = "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/{symbol}",
            unit = "ریال",
            symbols = listOf(
                SymbolDef("فولاد", "فولاد مبارکه"),
                SymbolDef("خودرو", "ایران‌خودرو"),
                SymbolDef("خساپا", "سایپا"),
                SymbolDef("شپنا", "پالایش نفت اصفهان"),
                SymbolDef("وبملت", "بانک ملت"),
                SymbolDef("فملی", "ملی صنایع مس"),
                SymbolDef("شستا", "سرمایه‌گذاری تأمین اجتماعی"),
                SymbolDef("خگستر", "گسترش سرمایه‌گذاری ایران‌خودرو")
            )
        ),

        SourceDef(
            id = "tse_index",
            title = "شاخص کل بورس",
            subtitle = "شاخص کل بازار از TSETMC",
            kind = JSON_REST,
            urlTemplate = "https://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/0",
            pricePath = "[0].lastValue | indexB1LastAll[0].lastValue | lastValue",
            changePath = "[0].indexChange | indexB1LastAll[0].indexChange | indexChange",
            changeMode = ChangeMode.ABSOLUTE,
            unit = "واحد",
            symbols = listOf(SymbolDef("index", "شاخص کل"))
        ),

        // ─────────────────────── طلا و ارز — TGJU ───────────────────────
        // API رسمیِ خودِ tgju.org — همان که سایت برای به‌روزرسانی لحظه‌ای صدا می‌زند؛
        // همه‌ی نمادها با یک درخواست. مقادیر «ریال» است؛ با ضریب ۰٫۱ تومان می‌شود.
        // هر کلیدِ این API می‌تواند نماد باشد (مثل price_gbp یا silver) — کد را در
        // «افزودن نماد» به‌صورت دلخواه بنویس.
        SourceDef(
            id = "tgju",
            title = "طلا و ارز — TGJU",
            subtitle = "دلار آزاد، طلا و سکه (تومان) • داده‌های tgju.org",
            kind = JSON_REST,
            urlTemplate = "https://call1.tgju.org/ajax.json",
            // همه‌ی نمادها با یک درخواست — مسیرِ هر نماد با {symbol} جدا می‌شود
            batchTemplate = "https://call1.tgju.org/ajax.json",
            urlFallbacks = listOf("https://call.tgju.org/ajax.json"),
            pricePath = "current.{symbol}.p",
            changePath = "current.{symbol}.dp",
            changeMode = ChangeMode.PERCENT,
            scale = 0.1,
            unit = "تومان",
            symbols = listOf(
                SymbolDef("price_dollar_rl", "دلار آمریکا (آزاد)"),
                SymbolDef("price_eur", "یورو"),
                SymbolDef("geram18", "طلای ۱۸ عیار (گرم)"),
                SymbolDef("geram24", "طلای ۲۴ عیار (گرم)"),
                SymbolDef("mesghal", "مثقال طلا"),
                SymbolDef("sekee", "سکه امامی"),
                SymbolDef("sekeb", "سکه بهار آزادی"),
                SymbolDef("nim", "نیم‌سکه"),
                SymbolDef("rob", "ربع‌سکه")
            )
        )
    )

    fun byId(id: String): SourceDef? =
        builtIn.firstOrNull { it.id == id }

    /** منبع‌هایی که کاربر خودش اضافه کرده (از تنظیمات) هم به این لیست اضافه می‌شوند */
    fun all(custom: List<SourceDef>): List<SourceDef> = builtIn + custom
}
