package com.pulse.market.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** بیشترین تعداد نماد هر ویجت */
const val MAX_SYMBOLS = 6

/** نوع خواندن داده از منبع */
@Serializable
enum class FetchKind {
    /** یک آدرس API که JSON برمی‌گرداند */
    JSON_REST,

    /** یک صفحه‌ی HTML که با سلکتور CSS قیمت از آن بیرون کشیده می‌شود */
    HTML_CSS,

    /** بورس تهران (TSETMC) — دو مرحله‌ای: پیدا کردن نماد + خواندن قیمت */
    TSE_TSETMC
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
    val label: String,
    /** منبعی که این نماد از آن خوانده می‌شود — برای نمادهای داخل SourceDef خالی می‌ماند */
    val sourceId: String = ""
)

/** تعریف یک «منبع داده» — چه سایت آماده چه منبع دلخواه کاربر */
@Serializable
data class SourceDef(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: FetchKind = FetchKind.JSON_REST,
    val urlTemplate: String,
    /** الگوی درخواست گروهی همه‌ی نمادها با یک HTTP (برای جلوگیری از rate-limit) — جای {symbols} */
    val batchTemplate: String? = null,
    val pricePath: String? = null,
    val changePath: String? = null,
    val changeMode: ChangeMode = ChangeMode.PERCENT,
    /** مسیر آرایه‌ی اعداد برای نمودار مینیاتوری (اختیاری) */
    val sparkPath: String? = null,
    /** مسیر عدد «حجم معاملات / حجم ۲۴ ساعت» (اختیاری) — اگر آرایه باشد جمع زده می‌شود */
    val volumePath: String? = null,
    val cssSelector: String? = null,
    val cssAttr: String? = null,
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
    /** حجم معامله (بورس) یا حجم ۲۴ ساعت (کریپتو) */
    val volume: Double? = null,
    val unit: String = "",
    val error: String? = null,
    val ts: Long = 0L,
    /** آخرین مقدار سالم است ولی به‌روزرسانی بعدی ناموفق بود (چراغ قرمز) */
    val stale: Boolean = false,
    /** سری قیمت برای نمودار مینیاتوری */
    val spark: List<Double> = emptyList(),
    /** منبعی که قیمت از آن خوانده شده */
    val sourceId: String = ""
)

/** تم ویجت — تیره/روشن + سه تم ترند: شیشه‌ای، شفق قطبی، نئون */
@Serializable(with = WidgetThemeSerializer::class)
enum class WidgetTheme {
    /** تیره‌ی کلاسیک */
    DARK,

    /** روشن */
    LIGHT,

    /** شیشه‌ای (Glassmorphism) — نیمه‌شفاف با حاشیه‌ی روشن */
    GLASS,

    /** شفق قطبی (Aurora) — گرادیان بنفش/نیلی/فیروزه‌ای */
    AURORA,

    /** نئون (Cyber) — مشکی بنفش با تأکید نئونی */
    NEON
}

/** سازگاری با نسخه‌های قدیمی: AMOLED حذف شد و به «شیشه‌ای» مهاجرت می‌کند */
object WidgetThemeSerializer : KSerializer<WidgetTheme> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WidgetTheme", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WidgetTheme) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): WidgetTheme {
        val name = decoder.decodeString()
        return WidgetTheme.entries.firstOrNull { it.name == name }
            ?: when (name) {
                "AMOLED" -> WidgetTheme.GLASS
                else -> WidgetTheme.DARK
            }
    }
}

/** تنظیمات کاربر برای ویجت — هر ویجت مقادیر و ظاهر مستقل خودش را دارد */
@Serializable
data class WidgetConfig(
    /** @deprecated فقط برای سازگاری با تنظیمات قدیمی — از sourceIds استفاده کن */
    val sourceId: String = "crypto_coingecko",
    /** منابع انتخاب‌شده — چند منبع می‌تواند هم‌زمان فعال باشد */
    val sourceIds: List<String> = emptyList(),
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
    val alerts: List<AlertRule> = emptyList(),

    // ── نمایش: هر ویجت مقادیر دلخواه خودش را نشان می‌دهد ──

    /** عنوان دلخواه ویجت — خالی = عنوان منبع */
    val title: String = "",
    /** ضریب اندازه‌ی فونت (۰٫۷۵ تا ۱٫۵) */
    val fontScale: Float = 1.0f,
    /** نمایش حجم معاملات / حجم ۲۴ ساعت */
    val showVolume: Boolean = true,
    /** نمایش درصد تغییر */
    val showChange: Boolean = true,
    /** نمایش کد/واحد زیر نام نماد */
    val showCode: Boolean = true,
    /** نمایش نوار وضعیت پایین */
    val showStatus: Boolean = true,
    /** نمایش ساعت هدر */
    val showTime: Boolean = true,
    /** جداکننده‌ی رنگی ردیف‌ها (پس‌زمینه‌ی متناوب برای هر نماد) */
    val rowSeparation: Boolean = true,
    /** اعداد فشرده (۶۴٫۲ هزار / 12.4K) */
    val compactNumbers: Boolean = false,
    /** مرتب‌سازی نمادها — دستی یا خودکار */
    val sortMode: SymbolSort = SymbolSort.MANUAL,
    /** وضعیت باز/بسته بودن بورس تهران کنار ساعت ویجت */
    val showMarketStatus: Boolean = true
) {
    /** منابع فعال (با پشتیبانی از فرمت قدیمی تک‌منبعی) */
    val activeSourceIds: List<String>
        get() = sourceIds.ifEmpty { listOf(sourceId) }

    /** نمادهای متعلق به یک منبع مشخص */
    fun symbolsOf(sourceId: String): List<SymbolDef> = symbols.filter { it.sourceId == sourceId }
}

/** روش مرتب‌سازی نمادها در ویجت */
@Serializable
enum class SymbolSort {
    /** ترتیب دستی کاربر (با فلش‌های بالا/پایین) */
    MANUAL,

    /** خودکار: بیشترین تغییر امروز بالاتر می‌نشیند */
    BIGGEST_CHANGE,

    /** بر اساس حروف نام نماد */
    ALPHABET
}
