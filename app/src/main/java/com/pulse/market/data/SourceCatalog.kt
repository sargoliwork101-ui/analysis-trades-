package com.pulse.market.data

import com.pulse.market.data.FetchKind.JSON_REST
import com.pulse.market.data.FetchKind.TSE_TSETMC

/**
 * لیست منابع آماده‌ی داخل اپ.
 * کاربر از این لیست انتخاب می‌کند یا خودش یک «منبع دلخواه» با آدرس و مسیر JSON می‌سازد.
 *
 * فقط منابعی می‌مانند که واقعاً کار می‌کنند: کریپتو (CoinGecko)، بورس تهران
 * (TSETMC + شاخص کل)، طلا و ارز (TGJU) و بازارهای جهانی (TradingView).
 *
 * واحد و ضریب هر نماد می‌تواند از منبع جدا باشد ([SymbolDef.unit]/[SymbolDef.scale]) —
 * مثلاً در منبع TGJU، «سکه امامی» تومانی است ولی «انس طلا» دلاری.
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
            subtitle = "قیمت پایانی + درصد تغییر روز (ریال) • TSETMC روی VPN/IP خارجی معمولاً پاسخ نمی‌دهد",
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
            urlFallbacks = listOf("http://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/0"),
            pricePath = "[0].lastValue | indexB1LastAll[0].lastValue | lastValue",
            changePath = "[0].indexChange | indexB1LastAll[0].indexChange | indexChange",
            changeMode = ChangeMode.ABSOLUTE,
            unit = "واحد",
            symbols = listOf(SymbolDef("index", "شاخص کل"))
        ),

        // ─────────────────────── طلا و ارز — TGJU ───────────────────────
        // API رسمیِ خودِ tgju.org — همان که سایت برای به‌روزرسانی لحظه‌ای صدا می‌زند؛
        // همه‌ی نمادها با یک درخواست. مقادیر «ریال» است؛ با ضریب ۰٫۱ تومان می‌شود.
        // هر کلیدِ این API می‌تواند نماد باشد (مثل price_gbp یا ons) — کد را در
        // «افزودن نماد» به‌صورت دلخواه بنویس.
        //
        // «انس طلا (جهانی)» و «انس نقره (جهانی)» دلاری‌اند (نه ریالی)، پس ضریب و
        // واحد مخصوص خودشان را دارند — همیشه همین الگو را برای نمادهای دلاری به‌کار ببر.
        SourceDef(
            id = "tgju",
            title = "طلا و ارز — TGJU",
            subtitle = "دلار آزاد، طلا و سکه (تومان) + انس جهانی (دلار) • داده‌های tgju.org",
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
                SymbolDef("price_gbp", "پوند انگلیس"),
                SymbolDef("geram18", "طلای ۱۸ عیار (گرم)"),
                SymbolDef("geram24", "طلای ۲۴ عیار (گرم)"),
                SymbolDef("mesghal", "مثقال طلا"),
                SymbolDef("sekee", "سکه امامی"),
                SymbolDef("sekeb", "سکه بهار آزادی"),
                SymbolDef("nim", "نیم‌سکه"),
                SymbolDef("rob", "ربع‌سکه"),
                // ── دلاری‌ها: ضریب ۱ و واحد $ (نه تومان) ──
                SymbolDef("ons", "انس طلا (جهانی)", unit = "$", scale = 1.0),
                SymbolDef("silver", "انس نقره (جهانی)", unit = "$", scale = 1.0),
                SymbolDef("tether_gold_xaut", "طلای توکنیزه XAU", unit = "$", scale = 1.0)
            )
        ),

        // ─────────────────── بازارهای جهانی — TradingView ───────────────────
        // اسکنر رسمی TradingView: هر نماد یک درخواست سبک JSON و پاسخ فوری.
        // برای «طلا و نقره‌ی جهانی، نفت، شاخص دلار و کریپتو به دلار».
        //
        // اگر شبکه‌ات TradingView را فیلتر کرده باشد (مثل بعضی اینترنت‌های ایران)،
        // این منبع جواب نمی‌دهد؛ به‌جایش از «طلا و ارز — TGJU» و نماد
        // «انس طلا (جهانی)» استفاده کن — همان قیمت انس جهانی، بدون فیلترشکن.
        SourceDef(
            id = "tradingview",
            title = "بازارهای جهانی — TradingView",
            subtitle = "انس طلا، نقره، نفت، شاخص دلار و کریپتو (به دلار)",
            kind = JSON_REST,
            urlTemplate = "https://scanner.tradingview.com/symbol" +
                    "?symbol={symbol}&fields=close,change,change_abs,volume&no_404=true",
            pricePath = "close",
            changePath = "change",
            changeMode = ChangeMode.PERCENT,
            unit = "$",
            symbols = listOf(
                SymbolDef("OANDA:XAUUSD", "طلا — انس جهانی", unit = "$"),
                SymbolDef("TVC:SILVER", "نقره — انس جهانی", unit = "$"),
                SymbolDef("TVC:DXY", "شاخص دلار آمریکا", unit = "امتیاز"),
                SymbolDef("TVC:USOIL", "نفت WTI", unit = "$"),
                SymbolDef("TVC:UKOIL", "نفت برنت", unit = "$"),
                SymbolDef("BITSTAMP:BTCUSD", "بیت‌کوین (TradingView)", unit = "$"),
                SymbolDef("BITSTAMP:ETHUSD", "اتریوم (TradingView)", unit = "$")
            )
        )
    )

    fun byId(id: String): SourceDef? =
        builtIn.firstOrNull { it.id == id }

    /** منبع‌هایی که کاربر خودش اضافه کرده (از تنظیمات) هم به این لیست اضافه می‌شوند */
    fun all(custom: List<SourceDef>): List<SourceDef> = builtIn + custom
}
