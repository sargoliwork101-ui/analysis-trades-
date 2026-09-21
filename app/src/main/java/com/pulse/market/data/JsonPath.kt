package com.pulse.market.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * خواندن مقدار از یک JSON با مسیر کوتاه.
 * مثال‌ها:
 *   "closingPriceInfo.pDrCotVal"
 *   "chart.result[0].meta.regularMarketPrice"
 *   "stats.btc-rls.latest"
 */
object JsonPath {

    private val INDEX_RE = Regex("""\[(\d+)]""")

    fun read(root: Any?, path: String): Any? {
        if (path.isBlank()) return null
        var cur: Any = root ?: return null
        for (rawSeg in path.split('.')) {
            if (rawSeg.isEmpty()) continue
            // جدا کردن نام و ایندکس‌ها:  result[0][2]
            val indexes = INDEX_RE.findAll(rawSeg).map { it.groupValues[1].toInt() }.toList()
            val name = rawSeg.substringBefore('[')
            if (name.isNotEmpty()) {
                cur = (cur as? JSONObject)?.opt(name) ?: return null
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
            is Boolean -> null
            else -> null
        }
    }
}
