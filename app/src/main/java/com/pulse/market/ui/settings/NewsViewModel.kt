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
import com.pulse.market.data.NewsNotifier
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import com.pulse.market.data.PumpAiReviewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** وضعیت تب خبرها؛ کش، شبکه و AI با ترک تب یا چرخش صفحه از بین نمی‌روند. */
class NewsViewModel(app: Application) : AndroidViewModel(app) {
    var items by mutableStateOf<List<MarketNewsItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var aiBusy by mutableStateOf(false); private set
    var aiProgress by mutableStateOf(""); private set
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
                // تحلیل کامل AI خبرهای بدون تغییر را نگه دار تا هر refresh هزینه و زمان دوباره نداشته باشد.
                val previous = items.associateBy { it.id }
                items = feed.items.map { item ->
                    val old = previous[item.id]
                    if (old?.hasCompleteAiAnalysis == true) {
                        item.copy(
                            aiTitle = old.aiTitle,
                            aiOutlook = old.aiOutlook,
                            marketImpact = old.marketImpact,
                            historicalContext = old.historicalContext,
                            aiSummary = old.aiSummary,
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
        if (aiBusy || !config.enabled || !config.anyReady) return
        val pending = items.filterNot { it.hasCompleteAiAnalysis }
            .sortedWith(compareByDescending<MarketNewsItem> { it.importance }
                .thenByDescending { it.publishedAt })
            .take(15)
        if (pending.isEmpty()) return
        aiBusy = true
        aiError = null
        val target = PumpAiReviewer.connectionTarget(config)
        val destination = target?.display ?: config.endpoint
        aiProgress = "در حال اتصال مستقیم به $destination…"
        try {
            val outcome = NewsAiSummarizer.summarize(config, pending) { progress ->
                aiProgress = if (progress.running) {
                    "اتصال مستقیم به $destination — تحلیل دستهٔ ${progress.batchNumber} از " +
                        "${progress.totalBatches} (${progress.completedItems} خبر آماده)"
                } else {
                    "دستهٔ ${progress.batchNumber} از ${progress.totalBatches} تمام شد — " +
                        "${progress.completedItems} خبر آماده"
                }
                if (progress.newItems.isNotEmpty()) {
                    applyEnrichments(progress.newItems)
                    // نتیجه‌ی هر دسته همان لحظه نمایش و ذخیره می‌شود؛ دسته‌های بعدی دیگر
                    // صفحه را با spinner خالی نگه نمی‌دارند.
                    val snapshot = items
                    val savedAt = fetchedAt
                    withContext(Dispatchers.IO) { NewsCacheStore.save(ctx, savedAt, snapshot) }
                }
            }
            outcome.error?.let { aiError = "$it — مقصد مستقیم: $destination" }
            if (outcome.items.isNotEmpty()) {
                val completedIds = pending.asSequence().map { it.id }
                    .filter { it in outcome.items }.toSet()
                applyEnrichments(outcome.items)
                val snapshot = items
                val savedAt = fetchedAt
                withContext(Dispatchers.IO) { NewsCacheStore.save(ctx, savedAt, snapshot) }
                val newestCompleted = items.filter { it.id in completedIds }
                    .maxByOrNull { it.publishedAt }
                NewsNotifier.notifyReady(
                    ctx,
                    completedIds.size,
                    newestCompleted?.displayTitle.orEmpty()
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            aiError = "ترجمه و تحلیل AI کامل نشد؛ متن انگلیسی یا کارت ناقص نمایش داده نمی‌شود — " +
                "مقصد مستقیم: $destination"
        } finally {
            aiBusy = false
            aiProgress = ""
        }
    }

    private fun applyEnrichments(enrichments: Map<String, NewsAiSummarizer.Enrichment>) {
        items = items.map { item ->
            val enriched = enrichments[item.id] ?: return@map item
            item.copy(
                aiTitle = enriched.persianTitle,
                aiOutlook = enriched.outlook,
                marketImpact = enriched.marketImpact,
                historicalContext = enriched.historicalContext,
                aiSummary = enriched.summary,
                importance = enriched.importance ?: item.importance
            )
        }.sortedWith(compareByDescending<MarketNewsItem> { it.importance }
            .thenByDescending { it.publishedAt })
    }
}
