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
        // جداکننده‌ی هزارگان و فاصله‌ها
        t = t.replace(",", "")
            .replace("٬", "")
            .replace("،", "")
            .replace("\u00A0", "")
            .replace("\u200F", "")
            .replace("\u200E", "")
            .replace(" ", "")
        // حذف واحد پول و هر چیز غیرعددی
        t = t.replace(Regex("[^0-9.+\\-eE]"), "")
        return t
    }

    fun parse(input: String?): Double? {
        if (input.isNullOrBlank()) return null
        return normalize(input).toDoubleOrNull()
    }
}
