package com.pulse.market.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * خواندن مقدار از یک JSON با مسیر کوتاه.
 *
 * مثال‌ها:
 *   "closingPriceInfo.pClosing"
 *   "chart.result[0].meta.regularMarketPrice"
 *   "[0].lastValue"
 *
 * چند مسیر جایگزین را می‌توان با `|` جدا کرد تا اولین مقدار غیرخالی برگردد
 * (برای سایت‌هایی که جواب را گاهی با wrapper و گاهی خام می‌دهند):
 *   "instrumentSearch[0].pClosing | [0].pClosing | pClosing"
 */
object JsonPath {

    private val INDEX_RE = Regex("""\[(\d+)]""")

    fun read(root: Any?, path: String): Any? {
        if (path.isBlank()) return null
        for (alt in path.split('|')) {
            val v = readSingle(root, alt.trim())
            if (v != null && v != JSONObject.NULL) return v
        }
        return null
    }

    private fun readSingle(root: Any?, path: String): Any? {
        if (path.isBlank()) return null
        var cur: Any = root ?: return null
        for (rawSeg in path.split('.')) {
            if (rawSeg.isEmpty()) continue
            // جدا کردن نام و ایندکس‌ها:  result[0][2]
            val indexes = INDEX_RE.findAll(rawSeg).map { it.groupValues[1].toInt() }.toList()
            val name = rawSeg.substringBefore('[')
            if (name.isNotEmpty()) {
                val obj = cur as? JSONObject
                cur = when {
                    obj != null -> obj.opt(name) ?: return null
                    // مسیر با نام نوشته شده ولی جواب آرایه‌ی خام است (مثل instrumentSearch[0] روی [...])
                    cur is JSONArray -> cur
                    else -> return null
                }
            }
            for (i in indexes) {
                cur = (cur as? JSONArray)?.opt(i) ?: return null
            }
        }
        return cur
    }

    fun readDouble(root: Any?, path: String?): Double? {
        if (path.isNullOrBlank()) return null
        return when (val v = read(root, path)) {
            is Number -> v.toDouble()
            is String -> Num.parse(v)
            else -> null
        }
    }

    /** آرایه‌ی عددی برای نمودار مینیاتوری — مقادیر null در پاسخ نادیده گرفته می‌شوند */
    fun readDoubleList(root: Any?, path: String?): List<Double> {
        val arr = read(root, path ?: return emptyList()) as? JSONArray ?: return emptyList()
        val out = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) {
            val d = when (val x = arr.opt(i)) {
                is Number -> x.toDouble()
                is String -> Num.parse(x)
                else -> null
            } ?: continue
            out.add(d)
        }
        return out
    }
}
