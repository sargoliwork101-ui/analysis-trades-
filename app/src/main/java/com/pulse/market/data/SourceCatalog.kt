package com.pulse.market.data

import com.pulse.market.data.FetchKind.HTML_CSS
import com.pulse.market.data.FetchKind.JSON_REST
import com.pulse.market.data.FetchKind.TSE_TSETMC

/**
 * لیست منابع آماده‌ی داخل اپ.
 * کاربر از این لیست انتخاب می‌کند یا خودش یک «منبع دلخواه» با آدرس و مسیر JSON می‌سازد.
 */
object SourceCatalog {

    private const val MIRROR = "https://raw.githubusercontent.com/HosseinOdd/Navasan-API/main/data"

    val builtIn: List<SourceDef> = listOf(

        // ────────────────────────── کریپتو ──────────────────────────
        SourceDef(
            id = "crypto_coingecko",
            title = "کریپتو — CoinGecko",
            subtitle = "قیمت دلاری + تغییر ۲۴ ساعت • به‌روزرسانی لحظه‌ای",
            kind = JSON_REST,
            urlTemplate = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids={symbol}&vs_currencies=usd&include_24hr_change=true",
            // همه‌ی نمادها با یک درخواست (جلوگیری از محدودیت تعداد درخواست CoinGecko)
            batchTemplate = "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids={symbols}&vs_currencies=usd&include_24hr_change=true",
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

        // ─────────────────────── طلا و ارز ───────────────────────
        SourceDef(
            id = "fx_rates",
            title = "ارز — دلار، یورو، …",
            subtitle = "نرخ ارز آزاد (تومان) • داده‌های Navasan",
            kind = JSON_REST,
            urlTemplate = "$MIRROR/fiat.json",
            batchTemplate = "$MIRROR/fiat.json",
            pricePath = "{symbol}.value",
            changePath = "{symbol}.change_pct",
            changeMode = ChangeMode.PERCENT,
            unit = "تومان",
            symbols = listOf(
                SymbolDef("usd", "دلار"),
                SymbolDef("eur", "یورو"),
                SymbolDef("gbp", "پوند"),
                SymbolDef("aed", "درهم امارات"),
                SymbolDef("try", "لیر ترکیه"),
                SymbolDef("jpy", "ین ژاپن"),
                SymbolDef("chf", "فرانک سوئیس"),
                SymbolDef("cny", "یوان چین")
            )
        ),

        SourceDef(
            id = "gold_rates",
            title = "طلا و سکه",
            subtitle = "طلای ۱۸ عیار، مثقال، سکه (تومان) • داده‌های Navasan",
            kind = JSON_REST,
            urlTemplate = "$MIRROR/gold.json",
            batchTemplate = "$MIRROR/gold.json",
            pricePath = "{symbol}.value",
            changePath = "{symbol}.change_pct",
            changeMode = ChangeMode.PERCENT,
            unit = "تومان",
            symbols = listOf(
                SymbolDef("18ayar", "طلای ۱۸ عیار (گرم)"),
                SymbolDef("gerami", "مثقال طلا"),
                SymbolDef("sekkeh", "سکه"),
                SymbolDef("bahar", "سکه بهار آزادی"),
                SymbolDef("nim", "نیم‌سکه"),
                SymbolDef("rob", "ربع‌سکه")
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
            title = "نمونه‌ی اسکرپ وب — TGJU (آزمایشی)",
            subtitle = "خواندن مستقیم از HTML سایت (سلکتور قابل تغییر)",
            kind = HTML_CSS,
            urlTemplate = "https://www.tgju.org/profile/{symbol}",
            cssSelector = "span[data-col='info-last-trade'], .price, .info-price .value",
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
