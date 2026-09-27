package com.pulse.market.data

import kotlin.math.max

/**
 * ایچیموکو کینکو هیو روی سری‌ی کوتاه قیمت (نمودار ۷ روزه‌ی CoinGecko).
 *
 * سری ما ۴۰ نقطه دارد (هر نقطه ≈ ۴ ساعت)، پس دوره‌های کلاسیک ۹/۲۶/۵۲ روی آن
 * جا نمی‌شوند. به‌جای حذف اندیکاتور، دوره‌ها به‌نسبت طول سری مقیاس می‌شوند و
 * همان معنی «کوتاه‌مدت / میان‌مدت / بلندمدتِ همین بازه» را می‌دهند.
 * خروجی عمداً nullable است: تا وقتی داده‌ی کافی نیست هیچ عددی ساخته نمی‌شود.
 */
object Ichimoku {

    data class Series(
        val tenkan: List<Double?>,
        val kijun: List<Double?>,
        val spanA: List<Double?>,
        val spanB: List<Double?>,
        /** جابه‌جایی رو به جلوی ابر (تعداد نقطه) */
        val displacement: Int,
        val tenkanPeriod: Int,
        val kijunPeriod: Int,
        val spanBPeriod: Int
    ) {
        /** آخرین مقدار معتبر هر خط */
        val lastTenkan: Double? get() = tenkan.lastOrNull { it != null }
        val lastKijun: Double? get() = kijun.lastOrNull { it != null }

        /** ابرِ روبه‌روی آخرین کندل (همان چیزی که قیمت امروز با آن سنجیده می‌شود) */
        val currentSpanA: Double? get() = spanA.getOrNull(spanA.size - 1 - displacement) ?: spanA.lastOrNull { it != null }
        val currentSpanB: Double? get() = spanB.getOrNull(spanB.size - 1 - displacement) ?: spanB.lastOrNull { it != null }
    }

    enum class CloudPosition(val label: String) {
        ABOVE("بالای ابر (روند صعودی)"),
        INSIDE("داخل ابر (بی‌تصمیم)"),
        BELOW("زیر ابر (روند نزولی)"),
        UNKNOWN("نامشخص")
    }

    /** ساخت خطوط ایچیموکو؛ برای سری‌های خیلی کوتاه null برمی‌گردد. */
    fun of(values: List<Double>): Series? {
        val clean = values.filter { it.isFinite() && it > 0.0 }
        if (clean.size < 12) return null
        val n = clean.size
        val tenkanPeriod = max(2, n / 6)
        val kijunPeriod = max(tenkanPeriod + 1, n / 3)
        val spanBPeriod = max(kijunPeriod + 1, (n * 2) / 3)
        val displacement = kijunPeriod / 2

        val tenkan = midPoints(clean, tenkanPeriod)
        val kijun = midPoints(clean, kijunPeriod)
        val baseSpanA = tenkan.indices.map { i ->
            val t = tenkan[i]
            val k = kijun[i]
            if (t == null || k == null) null else (t + k) / 2.0
        }
        val baseSpanB = midPoints(clean, spanBPeriod)

        return Series(
            tenkan = tenkan,
            kijun = kijun,
            spanA = shiftForward(baseSpanA, displacement),
            spanB = shiftForward(baseSpanB, displacement),
            displacement = displacement,
            tenkanPeriod = tenkanPeriod,
            kijunPeriod = kijunPeriod,
            spanBPeriod = spanBPeriod
        )
    }

    /** میانگین سقف و کف هر پنجره — همان تعریف تنکان/کیجون/اسپن‌بی */
    internal fun midPoints(values: List<Double>, period: Int): List<Double?> {
        if (period < 1) return values.map { null }
        return values.indices.map { index ->
            if (index + 1 < period) return@map null
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            for (i in (index + 1 - period)..index) {
                val v = values[i]
                if (v > high) high = v
                if (v < low) low = v
            }
            if (high.isFinite() && low.isFinite()) (high + low) / 2.0 else null
        }
    }

    /** جابه‌جایی رو به جلو؛ طول خروجی = طول ورودی + displacement */
    internal fun shiftForward(values: List<Double?>, displacement: Int): List<Double?> {
        if (displacement <= 0) return values
        return List(displacement) { null } + values
    }

    /** جای قیمت نسبت به ابر */
    fun position(price: Double?, spanA: Double?, spanB: Double?): CloudPosition {
        if (price == null || !price.isFinite() || spanA == null || spanB == null) return CloudPosition.UNKNOWN
        val top = max(spanA, spanB)
        val bottom = minOf(spanA, spanB)
        return when {
            price > top -> CloudPosition.ABOVE
            price < bottom -> CloudPosition.BELOW
            else -> CloudPosition.INSIDE
        }
    }

    /** خلاصه‌ی متنی برای نمایش و برای فرستادن به مدل هوش مصنوعی */
    fun summary(price: Double?, series: Series?): String {
        if (series == null) return "داده‌ی کافی برای ایچیموکو نبود"
        val spanA = series.currentSpanA
        val spanB = series.currentSpanB
        val position = position(price, spanA, spanB)
        val cross = when {
            series.lastTenkan == null || series.lastKijun == null -> "نامشخص"
            series.lastTenkan!! > series.lastKijun!! -> "تنکان بالای کیجون (شتاب مثبت)"
            series.lastTenkan!! < series.lastKijun!! -> "تنکان زیر کیجون (شتاب منفی)"
            else -> "تنکان و کیجون روی هم"
        }
        return "${position.label} • $cross"
    }
}
