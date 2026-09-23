package com.pulse.market.data

/**
 * ─────────────────────────────────────────────────────────────────────────
 * ماژول مرجع «بازار» — تنها جایی که مشخص می‌شود هر منبع مال کدام بازار است.
 *
 * قاعده‌ی طلایی: هیچ کلاسی در کل برنامه نباید id منبع را با رشته مقایسه کند
 * (مثل `if (src.id == "tse_tsetmc")`). هرجا لازم است بدانی این منبع چه بازار و
 * چه قابلیت‌هایی دارد، از [marketKindOf] / [SourceDef.marketKind] بپرس.
 * این ماژول دقیقاً برای همین ساخته شد تا «یک قابلیت اینجا هست آنجا نیست»
 * دیگر رخ ندهد.
 * ─────────────────────────────────────────────────────────────────────────
 */
enum class MarketKind(val key: String, val label: String) {
    /** بورس تهران (TSETMC + شاخص کل) */
    TSE("tse", "بورس"),

    /** کریپتو — بازار ۲۴/۷ */
    CRYPTO("crypto", "کریپتو"),

    /** سهام آمریکا (NYSE/NASDAQ) */
    US("us", "آمریکا"),

    /** طلا و ارز — بازار آزاد ایران */
    GOLD_FX("goldfx", "طلا و ارز"),

    /** بازارهای جهانی (انس طلا، نقره، نفت، شاخص دلار) — ۲۴/۵ */
    GLOBAL("global", "جهانی"),

    /** منبع دلخواه کاربر — بازار مشخصی ندارد */
    CUSTOM("custom", "منبع دلخواه")
}

/**
 * نگاشت id منبع‌های آماده → بازار.
 * فقط همین یک تابع در کل برنامه id ها را «می‌شناسد»؛ بقیه از طریق MarketKind کار می‌کنند.
 * منبع دلخواه کاربر null برمی‌گرداند (یعنی CUSTOM).
 */
fun marketKindOf(sourceId: String): MarketKind? = when (sourceId) {
    "tse_tsetmc", "tse_index" -> MarketKind.TSE
    "crypto_coingecko" -> MarketKind.CRYPTO
    "tgju" -> MarketKind.GOLD_FX
    "tradingview" -> MarketKind.GLOBAL
    else -> null
}

/** بازارِ هر تعریف منبع — منابع دلخواه کاربر = CUSTOM */
val SourceDef.marketKind: MarketKind
    get() = marketKindOf(id) ?: MarketKind.CUSTOM
