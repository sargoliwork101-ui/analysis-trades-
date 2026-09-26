package com.pulse.market.data

/** تبدیل عددهای «قشنگ‌شده»‌ی سایت‌ها به Double */
object Num {

    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    fun normalize(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            val fa = FA.indexOf(c)
            val ar = AR.indexOf(c)
            sb.append(
                when {
                    fa >= 0 -> '0' + fa
                    ar >= 0 -> '0' + ar
                    else -> c
                }
            )
        }
        var t = sb.toString()
        // ممیز عربی/فارسی باید پیش از حذف نویسه‌ها به نقطه تبدیل شود؛ وگرنه
        // «۴٬۳۱۰٫۹۴» اشتباهاً ۴۳۱۰۹۴ خوانده می‌شد.
        t = t.replace('٫', '.')
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
        // جداکننده‌ی هزارگان و فاصله‌ها
        t = t.replace(",", "")
            .replace("٬", "")
            .replace("،", "")
            .replace("\u00A0", "")
            .replace("\u202F", "")
            .replace("\u200F", "")
            .replace("\u200E", "")
            .replace(" ", "")
        // حذف واحد پول و هر چیز غیرعددی
        t = t.replace(Regex("[^0-9.+\\-eE]"), "")
        return t
    }

    fun parse(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        val value = normalize(input).toDoubleOrNull() ?: return null
        // Infinity/NaN نمودار و DecimalFormat را خراب می‌کنند و قیمت معتبر نیستند.
        return value.takeIf { it.isFinite() }
    }
}
