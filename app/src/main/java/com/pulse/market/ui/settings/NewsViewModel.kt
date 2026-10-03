package com.pulse.market.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.market.data.MarketNews
import com.pulse.market.data.MarketNewsItem
import com.pulse.market.data.NewsAiSummarizer
import com.pulse.market.data.NewsCacheStore
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** وضعیت تب خبرها؛ کش، شبکه و AI با ترک تب یا چرخش صفحه از بین نمی‌روند. */
class NewsViewModel(app: Application) : AndroidViewModel(app) {
    var items by mutableStateOf<List<MarketNewsItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var aiBusy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var aiError by mutableStateOf<String?>(null); private set
    var failedSources by mutableStateOf<List<String>>(emptyList()); private set
    var fetchedAt by mutableStateOf(0L); private set
    var aiConfig by mutableStateOf(PumpAiConfig()); private set

    private var started = false
    private val ctx get() = getApplication<Application>().applicationContext

    /** هر بار تب دیده می‌شود تنظیم AI دوباره خوانده می‌شود؛ خبر فقط در اولین بار/کهنگی تازه می‌شود. */
    fun onVisible() {
        viewModelScope.launch {
            val loadedConfig = withContext(Dispatchers.IO) { PumpAiConfigStore.load(ctx) }
            val configChanged = loadedConfig != aiConfig
            aiConfig = loadedConfig

            if (!started) {
                started = true
                val cached = withContext(Dispatchers.IO) { NewsCacheStore.load(ctx) }
                if (cached != null) {
                    items = cached.items
                    fetchedAt = cached.fetchedAt
                }
                val fresh = cached != null &&
                    System.currentTimeMillis() - cached.fetchedAt in 0L..NewsCacheStore.FRESH_MS
                if (!fresh) refreshInternal(force = false) else summarizeMissing()
            } else if (configChanged) {
                summarizeMissing()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            aiConfig = withContext(Dispatchers.IO) { PumpAiConfigStore.load(ctx) }
            refreshInternal(force = true)
        }
    }

    private suspend fun refreshInternal(force: Boolean) {
        if (loading) return
        if (!force && fetchedAt > 0L &&
            System.currentTimeMillis() - fetchedAt in 0L..NewsCacheStore.FRESH_MS
        ) {
            summarizeMissing()
            return
        }
        loading = true
        message = ""
        aiError = null
        try {
            val feed = MarketNews.fetch()
            failedSources = feed.failedSources
            if (feed.items.isEmpty()) {
                message = if (items.isEmpty()) {
                    "خبر تازه دریافت نشد؛ اتصال یا فیلترشکن را بررسی کن"
                } else {
                    "تازه‌سازی کامل نشد؛ خبرهای ذخیره‌شده نمایش داده می‌شوند"
                }
            } else {
                // خلاصه‌ی AI خبرهای بدون تغییر را نگه دار تا هر refresh هزینه و زمان دوباره نداشته باشد.
                val previous = items.associateBy { it.id }
                items = feed.items.map { item ->
                    val old = previous[item.id]
                    if (old?.hasAiSummary == true) {
                        item.copy(
                            aiSummary = old.aiSummary,
                            marketImpact = old.marketImpact,
                            importance = maxOf(item.importance, old.importance)
                        )
                    } else item
                }
                fetchedAt = feed.fetchedAt
                withContext(Dispatchers.IO) { NewsCacheStore.save(ctx, fetchedAt, items) }
                if (failedSources.isNotEmpty()) {
                    message = "بعضی منابع پاسخ ندادند؛ نتیجه از منابع سالم تکمیل شد"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = if (items.isEmpty()) {
                "دریافت خبر کامل نشد؛ اتصال را بررسی کن"
            } else {
                "تازه‌سازی کامل نشد؛ خبرهای ذخیره‌شده نمایش داده می‌شوند"
            }
        } finally {
            loading = false
        }
        summarizeMissing()
    }

    private suspend fun summarizeMissing() {
        val config = aiConfig
        if (aiBusy || !config.enabled || !config.isReady) return
        val pending = items.filterNot { it.hasAiSummary }
            .sortedWith(compareByDescending<MarketNewsItem> { it.importance }
                .thenByDescending { it.publishedAt })
            .take(18)
        if (pending.isEmpty()) return
        aiBusy = true
        aiError = null
        try {
            val outcome = NewsAiSummarizer.summarize(config, pending)
            outcome.error?.let { aiError = it }
            if (outcome.items.isNotEmpty()) {
                items = items.map { item ->
                    val enriched = outcome.items[item.id] ?: return@map item
                    item.copy(
                        aiSummary = enriched.summary,
                        marketImpact = enriched.marketImpact,
                        importance = enriched.importance ?: item.importance
                    )
                }.sortedWith(compareByDescending<MarketNewsItem> { it.importance }
                    .thenByDescending { it.publishedAt })
                withContext(Dispatchers.IO) { NewsCacheStore.save(ctx, fetchedAt, items) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            aiError = "خلاصه‌سازی AI کامل نشد؛ چکیده‌ی خود منابع نمایش داده می‌شود"
        } finally {
            aiBusy = false
        }
    }
}
