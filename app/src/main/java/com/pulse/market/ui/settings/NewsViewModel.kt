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
import com.pulse.market.data.NewsBriefingStore
import com.pulse.market.data.NewsCacheStore
import com.pulse.market.data.NewsSortOrder
import com.pulse.market.data.sortedFor
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

    /** نظر کلی هوش مصنوعی از مجموع تیترها (ارزان‌ترین مسیر استفاده از AI در تب خبر). */
    var briefing by mutableStateOf<NewsAiSummarizer.Briefing?>(null); private set
    var briefingBusy by mutableStateOf(false); private set
    var briefingError by mutableStateOf<String?>(null); private set

    private var started = false
    private val ctx get() = getApplication<Application>().applicationContext

    /** هر بار تب دیده می‌شود تنظیم AI دوباره خوانده می‌شود؛ خبر فقط در اولین بار/کهنگی تازه می‌شود. */
    fun onVisible() {
        viewModelScope.launch {
            val loadedConfig = withContext(Dispatchers.IO) { PumpAiConfigStore.load(ctx) }
            // فقط عوض‌شدن خودِ سرویس دلیل تحلیل دوباره است؛ تغییر تنظیم‌های مصرف
            // (حالت کم‌مصرف/سقف روزانه) نباید یک تحلیل تازه و پولی راه بیندازد.
            val configChanged = !loadedConfig.sameProviderAs(aiConfig)
            aiConfig = loadedConfig

            if (!started) {
                started = true
                briefing = withContext(Dispatchers.IO) { NewsBriefingStore.load(ctx) }
                val cached = withContext(Dispatchers.IO) { NewsCacheStore.load(ctx) }
                if (cached != null) {
                    items = cached.items
                    fetchedAt = cached.fetchedAt
                }
                val fresh = cached != null &&
                    System.currentTimeMillis() - cached.fetchedAt in 0L..NewsCacheStore.FRESH_MS
                if (!fresh) {
                    refreshInternal(force = false)
                } else {
                    summarizeMissing()
                    ensureBriefing(force = false)
                }
            } else if (configChanged) {
                summarizeMissing()
                ensureBriefing(force = false)
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
        // جمع‌بندی کوتاه همیشه اول می‌آید: ارزان است و حتی وقتی ترجمهٔ تک‌تک خبرها
        // خاموش باشد، کاربر نظر هوش مصنوعی را دارد.
        ensureBriefing(force = false)
        summarizeMissing()
    }

    /** گرفتن نظر تازه با دکمهٔ کاربر؛ کش را دور می‌زند. */
    fun refreshBriefing() {
        viewModelScope.launch { ensureBriefing(force = true) }
    }

    /**
     * فقط وقتی هزینه می‌کند که واقعاً لازم باشد: جمع‌بندیِ ذخیره‌شده تا وقتی فهرست
     * خبرها عوض نشده دوباره خریداری نمی‌شود.
     */
    private suspend fun ensureBriefing(force: Boolean) {
        val config = aiConfig
        if (briefingBusy || !config.enabled || !config.anyReady || !config.newsBriefing) return
        // تازه‌ترین خبرها مبنای جمع‌بندی‌اند تا نتیجه با چیزی که کاربر می‌بیند بخواند.
        val source = items.sortedFor(NewsSortOrder.NEWEST).take(NewsAiSummarizer.BRIEFING_ITEMS)
        if (source.isEmpty()) return
        val signature = NewsAiSummarizer.signature(source)
        val current = briefing
        if (!force && current != null && current.signature == signature) return
        briefingBusy = true
        briefingError = null
        try {
            val outcome = NewsAiSummarizer.briefing(config, source)
            outcome.briefing?.let { fresh ->
                briefing = fresh
                withContext(Dispatchers.IO) { NewsBriefingStore.save(ctx, fresh) }
            }
            outcome.error?.let { briefingError = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            briefingError = "گرفتن نظر کلی هوش مصنوعی کامل نشد"
        } finally {
            briefingBusy = false
        }
    }

    private suspend fun summarizeMissing() {
        val config = aiConfig
        // ترجمه و تحلیل تک‌تک خبرها گران‌ترین مسیر است و فقط با انتخاب خود کاربر اجرا می‌شود.
        if (aiBusy || !config.enabled || !config.anyReady || !config.newsPerItemAi) return
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
