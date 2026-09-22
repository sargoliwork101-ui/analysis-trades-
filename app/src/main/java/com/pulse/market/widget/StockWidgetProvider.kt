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
import com.pulse.market.data.SymbolSort
import com.pulse.market.data.WidgetConfig
import com.pulse.market.service.LiveUpdateService
import com.pulse.market.service.LiveUpdateWorker
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
                renderFromCache(context, appWidgetId)
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
                        // هر ویجت حالت زنده‌ی خودش را کنترل می‌کند
                        val wId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                        val cfg = ConfigStore.current(context, wId)
                        val newState = !cfg.liveService
                        ConfigStore.save(context, cfg.copy(liveService = newState), wId)
                        syncLiveService(context)
                        refreshAll(context, force = true)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /** پاک کردن تنظیمات ویجت‌هایی که از صفحه حذف شده‌اند */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { ConfigStore.deleteWidget(context, it) }
                if (WidgetRenderer.allWidgetIds(context).isEmpty()) {
                    LiveUpdateService.stop(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        const val ACTION_REFRESH = "com.pulse.market.ACTION_REFRESH"
        const val ACTION_TOGGLE_LIVE = "com.pulse.market.ACTION_TOGGLE_LIVE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * به‌روزرسانی همه‌ی ویجت‌ها — هر کدام با پیکربندی و نمادهای خودش؛
         * نمادهای مشترک فقط یک بار از شبکه گرفته می‌شوند.
         *
         * respectSchedule: درخواست‌های زمان‌بندی‌شده (سرویس زنده/Worker) به بازه‌ی ساعتی
         * هر ویجت احترام می‌گذارند تا بیرون از آن ساعت‌ها اینترنت مصرف نشود؛
         * رفرش دستیِ کاربر (دکمه‌ی روی ویجت) همیشه انجام می‌شود.
         */
        suspend fun refreshAll(context: Context, force: Boolean = false, respectSchedule: Boolean = false) {
            val ids = WidgetRenderer.allWidgetIds(context)
            if (ids.isEmpty()) return

            val cfgs = ids.map { it to ConfigStore.current(context, it) }
            // بیرون از بازه‌ی ساعتی کاربر؟ آن ویجت این نوبت چیزی از شبکه نمی‌خواهد
            val wantedList = cfgs.map { (_, cfg) ->
                if (respectSchedule && !inRefreshWindow(cfg)) emptyList()
                else QuoteRepo.wantedFor(context, cfg)
            }

            val cached = QuoteRepo.loadCachedMap(context)
            val minInterval = cfgs.minOf { it.second.intervalSec }.coerceIn(5, 3600)
            val stale =
                System.currentTimeMillis() - QuoteRepo.lastUpdated(context) > minInterval * 1000L
            val missing = wantedList.flatten().any { QuoteRepo.key(it.first, it.second.code) !in cached }
            val needNetwork =
                (force || cached.isEmpty() || stale || missing) && wantedList.flatten().isNotEmpty()

            val quoteMap = if (needNetwork) QuoteRepo.refreshMany(context, wantedList) else cached

            cfgs.forEachIndexed { i, (id, cfg) ->
                val quotes = wantedList[i].mapNotNull { quoteMap[QuoteRepo.key(it.first, it.second.code)] }
                renderOne(context, id, cfg, quotes)
                // هشدارهای هر ویجت روی داده‌ی خودش
                runCatching { AlertEngine.evaluate(context, cfg, quotes) }
            }
        }

        /** رندر یک ویجت از روی کش — برای تغییر اندازه بدون شبکه */
        private suspend fun renderFromCache(context: Context, widgetId: Int) {
            val cfg = ConfigStore.current(context, widgetId)
            val wanted = QuoteRepo.wantedFor(context, cfg)
            val map = QuoteRepo.loadCachedMap(context)
            val quotes = wanted.mapNotNull { map[QuoteRepo.key(it.first, it.second.code)] }
            renderOne(context, widgetId, cfg, quotes)
        }

        private fun renderOne(
            context: Context,
            widgetId: Int,
            cfg: WidgetConfig,
            quotes: List<Quote>
        ) {
            val sourceIds = cfg.activeSourceIds
            val sourceTitle = when {
                sourceIds.size == 1 ->
                    SourceCatalog.byId(sourceIds.first())?.title?.substringBefore(" —") ?: "منبع دلخواه"
                else -> "نبض بازار"
            }
            val updatedAt =
                QuoteRepo.lastUpdated(context).takeIf { it > 0 } ?: System.currentTimeMillis()
            WidgetRenderer.render(
                context = context,
                widgetId = widgetId,
                cfg = cfg,
                quotes = QuoteRepo.withLocalSpark(context, sortQuotes(quotes, cfg.sortMode)),
                live = cfg.liveService,
                updatedAt = updatedAt,
                sourceTitle = sourceTitle
            )
        }

        /** مرتب‌سازی نمایش نمادها — بدون تغییر ترتیب ذخیره‌شده‌ی کاربر */
        private fun sortQuotes(quotes: List<Quote>, mode: SymbolSort): List<Quote> = when (mode) {
            SymbolSort.MANUAL -> quotes
            SymbolSort.BIGGEST_CHANGE ->
                quotes.sortedByDescending { kotlin.math.abs(it.changePct ?: 0.0) }

            SymbolSort.ALPHABET -> quotes.sortedBy { it.label }
        }

        /**
         * آیا «الان» در بازه‌ی ساعتی تازه‌سازی این ویجت است؟
         * بازه‌ی شب‌گذر (مثل ۲۲ تا ۷) هم پشتیبانی می‌شود؛ from=to یعنی شبانه‌روزی.
         */
        private fun inRefreshWindow(cfg: WidgetConfig): Boolean {
            if (!cfg.refreshWindowEnabled) return true
            val from = cfg.refreshFromMinute.coerceIn(0, 1439)
            val to = cfg.refreshToMinute.coerceIn(0, 1439)
            if (from == to) return true
            val cal = java.util.Calendar.getInstance()
            val minute = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            return if (from < to) minute in from..to else (minute >= from || minute <= to)
        }

        /** سرویس زنده فقط وقتی لازم است که هیچ ویجتی liveService داشته باشد */
        suspend fun syncLiveService(context: Context) {
            if (ConfigStore.anyLive(context)) {
                LiveUpdateService.start(context)
                LiveUpdateWorker.schedule(context)
            } else {
                LiveUpdateService.stop(context)
                LiveUpdateWorker.cancel(context)
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
