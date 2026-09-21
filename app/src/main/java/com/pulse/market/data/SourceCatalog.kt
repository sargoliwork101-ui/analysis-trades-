package com.pulse.market.data

import com.pulse.market.data.FetchKind.HTML_CSS
import com.pulse.market.data.FetchKind.JSON_REST

/**
 * لیست منابع آماده‌ی داخل اپ.
 * کاربر از این لیست انتخاب می‌کند یا خودش یک «منبع دلخواه» با آدرس و مسیر JSON می‌سازد.
 */
object SourceCatalog {

    val builtIn: List<SourceDef> = listOf(

        // ────────────────────────── کریپتو ──────────────────────────
        SourceDef(
            id = "crypto_coingecko",
            title = "کریپتو — CoinGecko",
            subtitle = "قیمت دلاری + تغییر ۲۴ ساعت (بدون کلید API)",
            kind = JSON_REST,
            urlTemplate = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids={symbol}&vs_currencies=usd&include_24hr_change=true",
            pricePath = "{symbol}.usd",
            changePath = "{symbol}.usd_24h_change",
            changeMode = ChangeMode.PERCENT,
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
            subtitle = "قیمت پایانی نمادهای بورس (ریال)",
            kind = JSON_REST,
            // این آدرس با l18 یا نام نماد کار می‌کند
            urlTemplate = "https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/{symbol}",
            pricePath = "instrumentSearch[0].pClosing",
            changePath = "instrumentSearch[0].pDrCotVal",
            changeMode = ChangeMode.NONE,
            unit = "ریال",
            symbols = listOf(
                SymbolDef("فولاد", "فولاد مبارکه"),
                SymbolDef("خودرو", "ایران‌خودرو"),
                SymbolDef("خساپا", "سایپا"),
                SymbolDef("شپنا", "پالایش نفت اصفهان"),
                SymbolDef("وبملت", "بانک ملت"),
                SymbolDef("فملی", " ملی صنایع مس"),
                SymbolDef("شستا", "سرمایه‌گذاری تأمین اجتماعی"),
                SymbolDef("خگستر", "گسترش سرمایه‌گذاری ایران‌خودرو")
            )
        ),

        SourceDef(
            id = "tse_index",
            title = "شاخص کل بورس",
            subtitle = "شاخص کل از TSETMC",
            kind = JSON_REST,
            urlTemplate = "https://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/0",
            pricePath = "[0].lastValue",
            changeMode = ChangeMode.NONE,
            unit = "واحد",
            symbols = listOf(SymbolDef("index", "شاخص کل"))
        ),

        // ─────────────────────── طلا و ارز ───────────────────────
        SourceDef(
            id = "fx_navasan",
            title = "طلا و ارز — Navasan",
            subtitle = "دلار، یورو، طلای ۱۸ عیار، سکه (تومان)",
            kind = JSON_REST,
            // کلید دمو «free» هست؛ برای پایداری بیشتر از سایت navasan.tech کلید رایگان بگیر
            urlTemplate = "https://api.navasan.tech/latest/?api_key=free&item={symbol}",
            pricePath = "{symbol}.value",
            changePath = "{symbol}.change",
            changeMode = ChangeMode.ABSOLUTE,
            unit = "تومان",
            symbols = listOf(
                SymbolDef("price_dollar_rl", "دلار"),
                SymbolDef("price_eur", "یورو"),
                SymbolDef("price_gbp", "پوند"),
                SymbolDef("price_aed", "درهم امارات"),
                SymbolDef("price_try", "لیر ترکیه"),
                SymbolDef("gold_18k", "طلای ۱۸ عیار"),
                SymbolDef("coin_emami", "سکه امامی"),
                SymbolDef("mesghal", "مثقال طلا")
            )
        ),

        // ─────────────────── سهام آمریکا (با نمودار) ───────────────────
        SourceDef(
            id = "us_yahoo",
            title = "سهام آمریکا — Yahoo Finance",
            subtitle = "قیمت + درصد تغییر + نمودار مینیاتوری",
            kind = JSON_REST,
            urlTemplate = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}" +
                    "?interval=5m&range=1d&includePrePost=false",
            pricePath = "chart.result[0].meta.regularMarketPrice",
            changePath = "chart.result[0].meta.chartPreviousClose",
            changeMode = ChangeMode.PREV_CLOSE,
            sparkPath = "chart.result[0].indicators.quote[0].close",
            unit = "$",
            symbols = listOf(
                SymbolDef("AAPL", "اپل"),
                SymbolDef("TSLA", "تسلا"),
                SymbolDef("MSFT", "مایکروسافت"),
                SymbolDef("NVDA", "انویدیا"),
                SymbolDef("GOOGL", "گوگل"),
                SymbolDef("AMZN", "آمازون"),
                SymbolDef("META", "متا"),
                SymbolDef("BTC-USD", "بیت‌کوین دلاری")
            )
        ),

        // ─────────────────── نمونه‌ی اسکرپ صفحه‌ی وب ───────────────────
        SourceDef(
            id = "web_tgju",
            title = "نمونه‌ی اسکرپ وب — TGJU",
            subtitle = "خواندن مستقیم از HTML سایت (سلکتور قابل تغییر)",
            kind = HTML_CSS,
            urlTemplate = "https://www.tgju.org/profile/{symbol}",
            cssSelector = "#chart-form .price, .info-price .value, span[data-col='info-last-trade']",
            scale = 1.0,
            unit = "تومان",
            symbols = listOf(
                SymbolDef("price_dollar_rl", "دلار آزاد"),
                SymbolDef("geram18", "طلای ۱۸ عیار"),
                SymbolDef("sekeb", "سکه بهار آزادی")
            )
        )
    )

    fun byId(id: String): SourceDef? =
        builtIn.firstOrNull { it.id == id }

    /** منبع‌هایی که کاربر خودش اضافه کرده (از تنظیمات) هم به این لیست اضافه می‌شوند */
    fun all(custom: List<SourceDef>): List<SourceDef> = builtIn + custom
}
