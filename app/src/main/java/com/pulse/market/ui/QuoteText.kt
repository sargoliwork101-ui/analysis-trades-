package com.pulse.market.ui

import com.pulse.market.data.Quote

/**
 * ─────────────────────────────────────────────────────────────────────────
 * ماژول مرجع «متن قیمتی» — تنها جایی که متنِ قیمت/واحد/تغییر/حجم ساخته می‌شود.
 *
 * قاعده‌ی طلایی: هیچ صفحه و ویجتی خودش قیمت و واحد را کنار هم نچیند
 * (`"${price} ${unit}"`) — همیشه از همین‌جا بگیر. قبل از این ماژول، همین
 * کار در ۶ جای مختلف انجام می‌شد و واحدِ نماد در بعضی جاها جا می‌افتاد.
 * ─────────────────────────────────────────────────────────────────────────
 */
object QuoteText {

    /** واحد نماد — همیشه trim شده؛ خالی = منبع واحد تعریف نکرده */
    fun unit(quote: Quote): String = quote.unit.trim()

    /** فقط عدد قیمت (بدون واحد) */
    fun price(quote: Quote, persian: Boolean = false, compact: Boolean = false): String =
        Format.price(quote.price, persian, compact)

    /** «۶۴٬۲۱۰ تومان» — قیمت با واحد کنارش؛ قیمت یا واحد نبود = فقط خودش */
    fun priceWithUnit(quote: Quote, persian: Boolean = false, compact: Boolean = false): String =
        priceWithUnit(quote.price, unit(quote), persian, compact)

    /** همان بالا برای مقدار خام (مثل نتایج جستجوی بورس) */
    fun priceWithUnit(
        price: Double?,
        unit: String,
        persian: Boolean = false,
        compact: Boolean = false
    ): String {
        val p = Format.price(price, persian, compact)
        val u = unit.trim()
        return if (price != null && u.isNotEmpty()) "$p $u" else p
    }

    /** «▲ ۲٫۳۱٪» */
    fun change(quote: Quote, persian: Boolean = false): String =
        Format.pct(quote.changePct, persian)

    /** «۱۲٫۴ میلیون» */
    fun volume(quote: Quote, persian: Boolean = false): String =
        Format.volume(quote.volume, persian)
}
