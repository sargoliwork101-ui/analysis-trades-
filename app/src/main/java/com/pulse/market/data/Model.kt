package com.pulse.market.data

import kotlinx.serialization.Serializable

/** نوع خواندن داده از منبع */
@Serializable
enum class FetchKind {
    /** یک آدرس API که JSON برمی‌گرداند */
    JSON_REST,

    /** یک صفحه‌ی HTML که با سلکتور CSS قیمت از آن بیرون کشیده می‌شود */
    HTML_CSS
}

/** معنی عدد «تغییر» که از سایت می‌آید */
@Serializable
enum class ChangeMode {
    /** خود سایت درصد داده (مثل ۲.۳-) */
    PERCENT,

    /** سایت مقدار تغییر مطلق داده (مثل ۱۲۰۰ ریال) */
    ABSOLUTE,

    /** سایتی که فقط قیمت دیروز را می‌دهد و درصد را خودمان حساب می‌کنیم */
    PREV_CLOSE,

    /** درصد نمی‌خواهیم */
    NONE
}

/** یک نماد قابل انتخاب (کد فنی + نام نمایشی) */
@Serializable
data class SymbolDef(
    val code: String,
    val label: String
)

/** تعریف یک «منبع داده» — چه سایت آماده چه منبع دلخواه کاربر */
@Serializable
data class SourceDef(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: FetchKind = FetchKind.JSON_REST,
    val urlTemplate: String,
    val pricePath: String? = null,
    val changePath: String? = null,
    val changeMode: ChangeMode = ChangeMode.PERCENT,
    val cssSelector: String? = null,
    val cssAttr: String? = null,
    /** مسیر آرایه‌ی اعداد برای نمودار مینیاتوری (اختیاری) */
    val sparkPath: String? = null,
    val scale: Double = 1.0,
    val unit: String = "",
    val symbols: List<SymbolDef> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val builtIn: Boolean = true
)

/** یک قیمت خوانده‌شده */
@Serializable
data class Quote(
    val code: String,
    val label: String,
    val price: Double? = null,
    val changePct: Double? = null,
    val unit: String = "",
    val error: String? = null,
    val ts: Long = 0L,
    /** سری قیمت برای نمودار مینیاتوری */
    val spark: List<Double> = emptyList()
)

/** تم ویجت */
@Serializable
enum class WidgetTheme { DARK, LIGHT, AMOLED }

/** تنظیمات کاربر برای ویجت */
@Serializable
data class WidgetConfig(
    val sourceId: String = "crypto_coingecko",
    val symbols: List<SymbolDef> = emptyList(),
    /** فاصله‌ی به‌روزرسانی حالت زنده (ثانیه) */
    val intervalSec: Int = 15,
    /** به‌روزرسانی خودکار پس‌زمینه فعال باشد؟ */
    val liveService: Boolean = true,
    val theme: WidgetTheme = WidgetTheme.DARK,
    val showSparkline: Boolean = true,
    val rows: Int = 3,
    val persianDigits: Boolean = false,
    val showNotification: Boolean = true,
    /** قانون‌های هشدار قیمت */
    val alerts: List<AlertRule> = emptyList()
)
