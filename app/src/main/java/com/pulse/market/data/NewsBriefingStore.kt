package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * آخرین «نظر کلی هوش مصنوعی دربارهٔ خبرها» روی همین گوشی.
 *
 * چرا ذخیره می‌شود: این جمع‌بندی هزینهٔ توکن دارد. تا وقتی فهرست خبرها عوض نشده،
 * با هر بار باز شدن تب خبر نباید دوباره خریداری شود؛ همین نسخهٔ ذخیره‌شده نمایش
 * داده می‌شود و فقط با تغییر خبرها یا درخواست صریح کاربر، جمع‌بندی تازه گرفته می‌شود.
 */
object NewsBriefingStore {

    private const val PREF = "pulse_news_briefing"
    private const val KEY = "latest"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(context: Context): NewsAiSummarizer.Briefing? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        return runCatching {
            json.decodeFromString(NewsAiSummarizer.Briefing.serializer(), raw)
        }.getOrNull()?.takeIf { it.usable }
    }

    fun save(context: Context, briefing: NewsAiSummarizer.Briefing) {
        val raw = runCatching {
            json.encodeToString(NewsAiSummarizer.Briefing.serializer(), briefing)
        }.getOrNull() ?: return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
