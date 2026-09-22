package com.pulse.market.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.pulse.market.data.AlertEngine
import com.pulse.market.data.ConfigStore
import com.pulse.market.data.Quote
import com.pulse.market.data.QuoteRepo
import com.pulse.market.data.SourceCatalog
import com.pulse.market.data.WidgetConfig
import com.pulse.market.service.LiveUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class StockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** با تغییر اندازه‌ی ویجت (کشیدن گوشه‌ها)، محتوا دوباره با اندازه‌ی جدید رندر می‌شود */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        scope.launch {
            try {
                renderAll(context, ConfigStore.current(context), QuoteRepo.loadCached(context))
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        refreshAll(context, force = true)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_TOGGLE_LIVE -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        val cfg = ConfigStore.current(context)
                        val newState = !cfg.liveService
                        ConfigStore.save(context, cfg.copy(liveService = newState))
                        if (newState) LiveUpdateService.start(context) else LiveUpdateService.stop(context)
                        renderAll(context, cfg.copy(liveService = newState), QuoteRepo.loadCached(context))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        if (WidgetRenderer.allWidgetIds(context).isEmpty()) {
            LiveUpdateService.stop(context)
        }
    }

    companion object {

        const val ACTION_REFRESH = "com.pulse.market.ACTION_REFRESH"
        const val ACTION_TOGGLE_LIVE = "com.pulse.market.ACTION_TOGGLE_LIVE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** گرفتن داده و رندر همه‌ی ویجت‌های روی صفحه */
        suspend fun refreshAll(context: Context, force: Boolean = false) {
            val ids = WidgetRenderer.allWidgetIds(context)
            if (ids.isEmpty()) return

            val cfg = ConfigStore.current(context)
            val cached = QuoteRepo.loadCached(context)
            val ageMs = System.currentTimeMillis() - QuoteRepo.lastUpdated(context)
            val needNetwork = force || cached.isEmpty() || ageMs > cfg.intervalSec * 1000L

            val quotes = if (needNetwork) QuoteRepo.refresh(context, cfg) else cached
            val finalQuotes = if (quotes.any { it.price != null }) quotes else cached
            renderAll(context, cfg, finalQuotes)

            // بررسی هشدارهای قیمت روی همان دیتای تازه
            runCatching { AlertEngine.evaluate(context, cfg, finalQuotes) }
        }

        fun renderAll(context: Context, cfg: WidgetConfig, quotes: List<Quote>) {
            val ids = WidgetRenderer.allWidgetIds(context)
            if (ids.isEmpty()) return
            val sourceIds = cfg.activeSourceIds
            val sourceTitle = when {
                sourceIds.size == 1 ->
                    SourceCatalog.byId(sourceIds.first())?.title?.substringBefore(" —") ?: "منبع دلخواه"
                else -> "نبض بازار"
            }
            val updatedAt = QuoteRepo.lastUpdated(context).takeIf { it > 0 } ?: System.currentTimeMillis()
            ids.forEach { id ->
                WidgetRenderer.render(
                    context = context,
                    widgetId = id,
                    cfg = cfg,
                    quotes = quotes,
                    live = cfg.liveService,
                    updatedAt = updatedAt,
                    sourceTitle = sourceTitle
                )
            }
        }

        /** وقتی می‌خواهیم از بیرون (اپ/سرویس) رندر تازه بزنیم */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = WidgetRenderer.allWidgetIds(context)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, StockWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}

/** ویجت کوچک (۲×۱) — یک ردیف، مناسب فضای کم */
class SmallWidgetProvider : StockWidgetProvider()

/** ویجت بزرگ (۴×۳) — تا ۴ ردیف با نمودار */
class LargeWidgetProvider : StockWidgetProvider()
