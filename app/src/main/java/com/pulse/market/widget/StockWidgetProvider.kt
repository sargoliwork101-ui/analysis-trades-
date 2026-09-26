package com.pulse.market.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.pulse.market.data.AlertEngine
import com.pulse.market.data.ConfigStore
import com.pulse.market.data.InternalGuard
import com.pulse.market.data.PumpAlertEngine
import com.pulse.market.data.Quote
import com.pulse.market.data.QuoteRepo
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.SymbolSort
import com.pulse.market.data.TimePolicy
import com.pulse.market.data.WidgetConfig
import com.pulse.market.service.LiveUpdateService
import com.pulse.market.service.LiveUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
        // اکشن‌های سفارشی فقط با توکن داخلی اجرا می‌شوند. رسیورها در مانیفست
        // non-exported هم هستند؛ این بررسی لایه‌ی دفاعی دوم برای PendingIntentهاست.
        val isInternalAction = intent.action == ACTION_REFRESH || intent.action == ACTION_TOGGLE_LIVE
        if (isInternalAction && !InternalGuard.isTrusted(context, intent)) {
            Log.w(TAG, "اکشن داخلی بدون توکن معتبر رد شد: ${intent.action}")
            return
        }
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
                        // هر ویجت حالت زنده‌ی خودش را کنترل می‌کند؛ PendingIntent کهنه‌ی
                        // ویجت حذف‌شده نباید دوباره برای یک id نامعتبر تنظیمات بسازد.
                        val wId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
                        if (wId !in WidgetRenderer.allWidgetIds(context)) return@launch
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
                // هم سرویس زنده و هم Worker دوره‌ای مطابق ویجت‌های باقی‌مانده همگام شوند —
                // حذفِ آخرین ویجت باید هر دو را خاموش کند (وگرنه Worker هر ۱۵ دقیقه
                // تا همیشه بی‌دلیل بیدار می‌شد و باتری می‌سوزاند)
                syncLiveService(context, startForeground = false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        private const val TAG = "PulseMarketWidget"

        const val ACTION_REFRESH = "com.pulse.market.ACTION_REFRESH"
        const val ACTION_TOGGLE_LIVE = "com.pulse.market.ACTION_TOGGLE_LIVE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshMutex = Mutex()

        /**
         * به‌روزرسانی همه‌ی ویجت‌ها — هر کدام با پیکربندی و نمادهای خودش؛
         * نمادهای مشترک فقط یک بار از شبکه گرفته می‌شوند.
         *
         * respectSchedule: درخواست‌های زمان‌بندی‌شده (سرویس زنده/Worker) به بازه‌ی ساعتی
         * هر ویجت احترام می‌گذارند تا بیرون از آن ساعت‌ها اینترنت مصرف نشود؛
         * رفرش دستیِ کاربر (دکمه‌ی روی ویجت) همیشه انجام می‌شود.
         *
         * نکته‌ی مهم: «نمایش» هرگز متوقف نمی‌شود — ویجتی که این نوبت شبکه نمی‌خواهد
         * (بیرون از بازه‌ی ساعتی، بدون اینترنت، یا حالت دستی) آخرین داده‌ی سالم خودش را
         * نشان می‌دهد و فقط چراغ‌هایش قرمز می‌شوند؛ صفحه هرگز خالی نمی‌شود.
         */
        suspend fun refreshAll(context: Context, force: Boolean = false, respectSchedule: Boolean = false) {
            refreshMutex.lock()
            try {
                refreshAllLocked(context.applicationContext, force, respectSchedule)
            } finally {
                refreshMutex.unlock()
            }
        }

        private suspend fun refreshAllLocked(
            context: Context,
            force: Boolean,
            respectSchedule: Boolean
        ) {
            val ids = WidgetRenderer.allWidgetIds(context)
            if (ids.isEmpty()) return

            val cfgs = ids.map { it to ConfigStore.current(context, it) }

            // نمادهای هر ویجت همیشه برای «نمایش» محاسبه می‌شوند؛
            // فقط «گرفتن از شبکه» به تنظیمات هر ویجت احترام می‌گذارد:
            // بیرون از بازه‌ی ساعتی یا ویجتِ «دستی» (به‌روزرسانی خودکار خاموش) این نوبت شبکه نمی‌خواهد
            val wantedAll = cfgs.map { (_, cfg) -> QuoteRepo.wantedFor(context, cfg) }
            val wantedNet = cfgs.mapIndexed { i, (_, cfg) ->
                val skip = respectSchedule && (!cfg.liveService || !inRefreshWindow(cfg))
                when {
                    skip -> emptyList()
                    else -> {
                        // نمادهای هشدارهای فعال هم از شبکه خوانده می‌شوند — حتی اگر در
                        // ویجت نمایش داده نشده باشند؛ تا هشدار همیشه با داده‌ی تازه
                        // بررسی شود و بعد از مدتی بی‌صدا از کار نیفتد
                        val alertSyms = cfg.alerts.filter { it.enabled }.mapNotNull { rule ->
                            val sid = rule.sourceId.ifBlank { cfg.sourceId }
                            if (sid.isBlank() ||
                                ConfigStore.resolveSource(context, sid) == null
                            ) null
                            else sid to SymbolDef(
                                rule.symbolCode,
                                rule.symbolLabel.ifBlank { rule.symbolCode },
                                sid
                            )
                        }
                        (wantedAll[i] + alertSyms).distinctBy { it.first to it.second.code }
                    }
                }
            }

            val cached = QuoteRepo.loadCachedMap(context)
            // فاصله‌ی تازگی فقط از ویجت‌هایی که واقعاً شبکه می‌خواهند
            val minInterval = (cfgs.filterIndexed { i, _ -> wantedNet[i].isNotEmpty() }
                .minOfOrNull { it.second.intervalSec } ?: cfgs.minOf { it.second.intervalSec })
                .coerceIn(5, 3600)
            val stale = !TimePolicy.isFresh(
                System.currentTimeMillis(),
                QuoteRepo.lastUpdated(context),
                minInterval * 1000L
            )
            val missing = wantedNet.flatten().any { QuoteRepo.key(it.first, it.second.code) !in cached }
            val needNetwork =
                (force || cached.isEmpty() || stale || missing) && wantedNet.flatten().isNotEmpty()

            val quoteMap = if (needNetwork) QuoteRepo.refreshMany(context, wantedNet) else cached

            cfgs.forEachIndexed { i, (id, cfg) ->
                val quotes = wantedAll[i].mapNotNull { quoteMap[QuoteRepo.key(it.first, it.second.code)] }
                renderOne(context, id, cfg, quotes)
                // هشدار فقط بعد از یک نوبت واقعی شبکه و فقط با Quote سالمِ همان
                // نوبت بررسی می‌شود؛ داده‌ی stale/cached نباید نوتیف تازه بسازد.
                if (needNetwork && wantedNet[i].isNotEmpty()) {
                    val alertQuotes = cfg.alerts.filter { it.enabled }.mapNotNull { rule ->
                        val sid = rule.sourceId.ifBlank {
                            cfg.activeSourceIds.firstOrNull().orEmpty()
                        }
                        quoteMap[QuoteRepo.key(sid, rule.symbolCode)]
                            ?.takeIf { !it.stale && it.error == null && it.price != null }
                    }.distinctBy { QuoteRepo.key(it.sourceId, it.code) }
                    try {
                        AlertEngine.evaluate(context, "widget_$id", cfg, alertQuotes)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // خطای یک موتور هشدار نباید رندر ویجت‌های دیگر را متوقف کند.
                    }
                }
            }

            // آلارم پامپ از همان چرخه‌ی موجود استفاده می‌کند، اما موتور خودش فقط هر
            // ۱۵ دقیقه یک اسکن مشترک می‌گیرد و برای هر ویجت cooldown جدا دارد.
            val pumpConfigs = cfgs.filter { (_, cfg) ->
                !respectSchedule || inRefreshWindow(cfg)
            }
            try {
                PumpAlertEngine.evaluateConfigured(context, pumpConfigs)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // خطای CoinGecko/اعلان نباید رندر قیمت‌های اصلی را خراب کند.
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

        private suspend fun renderOne(
            context: Context,
            widgetId: Int,
            cfg: WidgetConfig,
            quotes: List<Quote>
        ) {
            val sourceIds = cfg.activeSourceIds
            val sourceTitle = when {
                sourceIds.size == 1 ->
                    // resolveSource (نه فقط کاتالوگ) — تا ویجتِ تک‌منبعیِ «دلخواه»
                    // نامی که کاربر خودش گذاشته را نشان دهد، نه «منبع دلخواه»
                    ConfigStore.resolveSource(context, sourceIds.first())
                        ?.title?.substringBefore(" —") ?: "منبع دلخواه"
                else -> "نبض بازار"
            }
            // منبعِ انتخاب‌شده ولی ناموجود (مثل منابع حذف‌شده‌ی نسخه‌های قدیم) —
            // حالت خالیِ ویجت باید بگوید مشکل چیست، نه «رفرش بزن»
            val missingSources = sourceIds.filter { ConfigStore.resolveSource(context, it) == null }
            // واحدِ خالی (مثلاً داده‌ی قدیمیِ کش) از تعریف منبع پر می‌شود —
            // تا واحدِ نماد هیچ‌وقت به خاطر داده‌ی کهنه از ویجت نیفتد
            val healed = quotes.map { q ->
                if (q.unit.isBlank() && q.sourceId.isNotBlank()) {
                    val srcUnit = ConfigStore.resolveSource(context, q.sourceId)?.unit.orEmpty()
                    if (srcUnit.isNotEmpty()) q.copy(unit = srcUnit) else q
                } else q
            }
            // زمانِ «همین ویجت»: تازه‌ترین داده‌ی نمادهای خودش — نه سراسری؛
            // تا ویجتِ دستی/متوقف، ساعت و چراغِ سبزِ ویجتِ زنده‌ی کناری را نشان ندهد
            val ownLatest = healed.maxOfOrNull { it.ts } ?: 0L
            val updatedAt = ownLatest.takeIf { it > 0 } ?: QuoteRepo.lastUpdated(context)
            WidgetRenderer.render(
                context = context,
                widgetId = widgetId,
                cfg = cfg,
                quotes = QuoteRepo.withLocalSpark(context, sortQuotes(healed, cfg.sortMode)),
                live = cfg.liveService,
                updatedAt = updatedAt,
                sourceTitle = sourceTitle,
                emptyHint = if (missingSources.isNotEmpty())
                    "منبع این ویجت حذف شده — از تنظیمات، منبع جدید انتخاب کن"
                else ""
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

        /**
         * آیا ویجتِ واقعیِ زنده‌ای روی صفحه هست؟
         * تنظیمِ «مؤثرِ» هر ویجت واقعی حساب می‌شود (ویجتی که تنظیمات ذخیره‌شده ندارد = الگو)؛
         * ولی خودِ الگو به‌تنهایی حساب نمی‌شود — وگرنه با حذف/دستی‌کردن همه‌ی ویجت‌ها
         * هم سرویس زنده و Worker برای همیشه روشن می‌ماندند و باتری می‌سوزاندند.
         */
        suspend fun anyLiveWidget(context: Context): Boolean {
            val ids = WidgetRenderer.allWidgetIds(context)
            return ids.isNotEmpty() && ids.any { ConfigStore.current(context, it).liveService }
        }

        /** آیا Worker دوره‌ای برای قیمت زنده یا آلارم پامپ لازم است؟ */
        suspend fun anyPeriodicWidget(context: Context): Boolean {
            val ids = WidgetRenderer.allWidgetIds(context)
            return ids.isNotEmpty() && ids.any {
                val cfg = ConfigStore.current(context, it)
                cfg.liveService || cfg.pumpAlertEnabled
            }
        }

        /** کمینه‌ی فاصله‌ی تازه‌سازی بین ویجت‌های واقعیِ زنده — الگو به‌تنهایی وارد نمی‌شود */
        suspend fun liveInterval(context: Context): Int {
            val ids = WidgetRenderer.allWidgetIds(context)
            return ids.map { ConfigStore.current(context, it) }
                .filter { it.liveService }
                .minOfOrNull { it.intervalSec }
                ?.coerceIn(5, 3600)
                ?: 15
        }

        /**
         * سرویس چندثانیه‌ای فقط برای ویجت زنده است؛ Worker پانزده‌دقیقه‌ای برای
         * ویجت زنده یا آلارم پامپ نگه داشته می‌شود.
         *
         * [startForeground] باید فقط پس از اقدام مستقیم کاربر true باشد. بوت، Worker و
         * ساخت پس‌زمینه‌ی پروسه حق شروع dataSync ForegroundService را در Android 15+
         * ندارند؛ در آن مسیرها فقط WorkManager زمان‌بندی می‌شود.
         */
        suspend fun syncLiveService(context: Context, startForeground: Boolean = true) {
            val hasLiveWidget = anyLiveWidget(context)
            if (hasLiveWidget) {
                if (startForeground) LiveUpdateService.start(context)
            } else {
                LiveUpdateService.stop(context)
            }

            if (anyPeriodicWidget(context)) {
                LiveUpdateWorker.schedule(context)
            } else {
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
