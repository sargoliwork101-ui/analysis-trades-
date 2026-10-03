package com.pulse.market.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.market.data.AiReviewStore
import com.pulse.market.data.NobitexMarkets
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpAlertEngine
import com.pulse.market.data.PumpScanner
import com.pulse.market.data.WidgetConfig
import com.pulse.market.ui.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * نگه‌دارنده‌ی وضعیت و منطقِ بخش «پامپ‌های کریپتو».
 *
 * چرا ViewModel: پیش‌تر همه‌ی state داخل خود composable با `remember` بود، پس با
 * چرخش صفحه یا رفتن به بخش دیگر و برگشتن، نتیجه‌ی اسکن/معامله‌ها از دست می‌رفت و
 * دوباره از شبکه گرفته می‌شد. حالا state در ViewModelِ scope‌شده به Activity می‌ماند،
 * پس بازگشت به این بخش فوری و بدون درخواست دوباره است. منطقِ شبکه/دیسک هم از UI جدا شد.
 *
 * همه‌ی propertyها با Compose State پشتیبانی می‌شوند تا خواندنشان در composition به
 * صورت خودکار recomposition ایجاد کند؛ نوشتن فقط از داخل همین کلاس مجاز است.
 */
class PumpsViewModel(app: Application) : AndroidViewModel(app) {

    var scan by mutableStateOf<PumpScanner.PumpScan?>(null); private set
    var busy by mutableStateOf(false); private set
    var note by mutableStateOf(""); private set

    var aiConfig by mutableStateOf(PumpAiConfig()); private set
    var aiBusyIds by mutableStateOf<Set<String>>(emptySet()); private set
    var aiReviews by mutableStateOf<Map<String, PumpAiReviewer.Review>>(emptyMap()); private set
    var aiErrors by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var aiReviewAt by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    var aiHistory by mutableStateOf<Map<String, List<AiReviewStore.Entry>>>(emptyMap()); private set
    var aiStorageError by mutableStateOf(false); private set
    var aiTestBusy by mutableStateOf(false); private set
    var aiTestResult by mutableStateOf<PumpAiReviewer.TestResult?>(null); private set

    var previousMatches by mutableStateOf<Pair<Long, Set<String>>?>(null); private set
    var trades by mutableStateOf<List<PaperTradeStore.Trade>>(emptyList()); private set
    var tradeNotice by mutableStateOf<String?>(null); private set
    var nobitex by mutableStateOf<Map<String, NobitexMarkets.Result>>(emptyMap()); private set

    private var aiEdited = false
    private var started = false

    private val ctx get() = getApplication<Application>().applicationContext

    /** یک‌بار در باز شدن صفحه صدا زده می‌شود؛ اجرای دوباره بی‌اثر است. */
    fun start(universe: Int, minChange: Double) {
        if (started) return
        started = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { PumpAiConfigStore.load(ctx) }
            if (!aiEdited) aiConfig = loaded
        }
        // آخرین تحلیل‌های ذخیره‌شده‌ی هر کوین بازیابی می‌شوند تا بعد از بستن برنامه هم بمانند.
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { AiReviewStore.latestByCoin(ctx) }
            if (latest.isNotEmpty()) {
                aiReviews = aiReviews + latest.mapValues { it.value.review }
                aiReviewAt = aiReviewAt + latest.mapValues { it.value.at }
            }
        }
        viewModelScope.launch {
            previousMatches = withContext(Dispatchers.IO) { PumpScanner.previousMatches(ctx) }
            val cached = withContext(Dispatchers.IO) { PumpScanner.cached(ctx) }
            // با باز شدن صفحه هم حد سود/ضرر با آخرین قیمت کش‌شده بررسی می‌شود.
            trades = withContext(Dispatchers.IO) {
                cached?.coins?.mapNotNull { c -> c.price?.let { c.id to it } }?.toMap()
                    ?.let { PaperTradeStore.settle(ctx, it) }
                PaperTradeStore.all(ctx)
            }
            scan = cached
            if (cached == null) {
                busy = true
                try {
                    scan = PumpScanner.scan(ctx, universe, minChange)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    note = "⚠️ اسکن پامپ کامل نشد؛ اتصال را بررسی کن"
                } finally {
                    busy = false
                }
            }
        }
    }

    fun saveAiConfig(new: PumpAiConfig) {
        if (new != aiConfig) {
            aiReviews = emptyMap()
            aiErrors = emptyMap()
            aiTestResult = null
        }
        aiEdited = true
        aiConfig = new
        PumpAiConfigStore.saveDebounced(ctx, new) { saved -> aiStorageError = !saved }
    }

    fun testAiConnection() {
        if (aiTestBusy) return
        val config = aiConfig
        aiTestBusy = true
        aiTestResult = null
        viewModelScope.launch {
            try {
                aiTestResult = PumpAiReviewer.testConnection(config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val destination = PumpAiReviewer.connectionTarget(config)?.display ?: config.endpoint
                aiTestResult = PumpAiReviewer.TestResult(
                    false,
                    "❌ تست اتصال کامل نشد — مقصد مستقیم: $destination"
                )
            } finally {
                aiTestBusy = false
            }
        }
    }

    fun runAiReview(coin: PumpScanner.PumpCoin) {
        val config = aiConfig
        if (!config.isReady || coin.id in aiBusyIds) return
        aiBusyIds = aiBusyIds + coin.id
        aiErrors = aiErrors - coin.id
        viewModelScope.launch {
            try {
                val outcome = PumpAiReviewer.review(config, coin)
                outcome.review?.let {
                    val at = System.currentTimeMillis()
                    aiReviews = aiReviews + (coin.id to it)
                    aiReviewAt = aiReviewAt + (coin.id to at)
                    withContext(Dispatchers.IO) {
                        AiReviewStore.add(ctx, coin.id, coin.symbol, coin.name, it, at)
                    }
                    aiHistory = aiHistory +
                            (coin.id to withContext(Dispatchers.IO) { AiReviewStore.history(ctx, coin.id) })
                }
                outcome.error?.let { aiErrors = aiErrors + (coin.id to it) }
            } finally {
                aiBusyIds = aiBusyIds - coin.id
            }
        }
    }

    /** تاریخچه‌ی تحلیل‌های یک کوین را برای صفحه‌ی جزئیات بارگذاری می‌کند. */
    fun loadAiHistory(coinId: String) {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { AiReviewStore.history(ctx, coinId) }
            aiHistory = aiHistory + (coinId to history)
        }
    }

    fun runScan(cfg: WidgetConfig, alertOwnerKey: String, force: Boolean) {
        busy = true
        note = ""
        viewModelScope.launch {
            try {
                val res = PumpScanner.scan(ctx, cfg.pumpUniverse, cfg.pumpMinChange, force = force)
                scan = res
                previousMatches = withContext(Dispatchers.IO) { PumpScanner.previousMatches(ctx) }
                // حد سود/حد ضرر معامله‌های آزمایشی با قیمت‌های تازه بررسی می‌شود.
                val closed = withContext(Dispatchers.IO) {
                    val prices = res.coins.mapNotNull { c -> c.price?.let { c.id to it } }.toMap()
                    PaperTradeStore.settle(ctx, prices)
                }
                trades = withContext(Dispatchers.IO) { PaperTradeStore.all(ctx) }
                if (closed.isNotEmpty()) {
                    tradeNotice = closed.joinToString(" • ") { trade ->
                        "${trade.name}: ${trade.closeReason?.label ?: "بسته شد"} — " +
                                PaperTradeStore.resultText(trade, trade.closePrice, cfg.persianDigits)
                    }
                }
                if (res.error == null) PumpAlertEngine.evaluateScan(ctx, alertOwnerKey, cfg, res)
                note = res.error?.let {
                    "⚠️ اسکن تازه نگرفت — $it (فهرست قبلی نمایش داده می‌شود)"
                } ?: ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                note = "⚠️ اسکن پامپ کامل نشد؛ اتصال را بررسی کن"
            } finally {
                busy = false
            }
        }
    }

    private suspend fun reloadTrades() {
        trades = withContext(Dispatchers.IO) { PaperTradeStore.all(ctx) }
    }

    fun buy(
        coin: PumpScanner.PumpCoin,
        amountUsd: Double,
        takeProfitPct: Double?,
        stopLossPct: Double?,
        feePct: Double,
        buyStepPrices: List<Double>?,
        sellStepPrices: List<Double>?,
        persian: Boolean
    ) {
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) {
                PaperTradeStore.buy(
                    ctx, coin, amountUsd, takeProfitPct, stopLossPct, feePct,
                    buyStepPrices = buyStepPrices, sellStepPrices = sellStepPrices
                )
            }
            reloadTrades()
            val ladderNote = when {
                opened == null -> ""
                opened.isLadder && opened.isSellLadder ->
                    " (${Format.toPersianDigits("${opened.steps.size}")} پله خرید، " +
                            "${Format.toPersianDigits("${opened.sellSteps.size}")} پله فروش)"
                opened.isLadder -> " (${Format.toPersianDigits("${opened.steps.size}")} پله خرید)"
                opened.isSellLadder -> " (${Format.toPersianDigits("${opened.sellSteps.size}")} پله فروش)"
                else -> ""
            }
            tradeNotice = when {
                opened == null -> "ثبت خرید آزمایشی ممکن نشد (قیمت یا مبلغ نامعتبر)"
                else -> "خرید آزمایشی ${opened.name} ثبت شد$ladderNote."
            }
        }
    }

    /** فروش یک معامله‌ی مشخص (از کارت کیف). */
    fun sellTrade(trade: PaperTradeStore.Trade, price: Double?, persian: Boolean) {
        viewModelScope.launch {
            val closed = withContext(Dispatchers.IO) { PaperTradeStore.sell(ctx, trade.id, price) }
            reloadTrades()
            tradeNotice = if (closed == null) "برای فروش، اول یک اسکن تازه بزن تا قیمت به‌روز شود"
            else "فروش آزمایشی ${closed.name}: " +
                    PaperTradeStore.resultText(closed, closed.closePrice, persian)
        }
    }

    /** فروش معامله‌ی بازِ یک کوین (از برگه‌ی جزئیات). */
    fun sellOpenForCoin(coinId: String, price: Double?, persian: Boolean) {
        viewModelScope.launch {
            val open = trades.firstOrNull { it.coinId == coinId && it.isOpen }
            val closed = if (open == null) null else withContext(Dispatchers.IO) {
                PaperTradeStore.sell(ctx, open.id, price)
            }
            reloadTrades()
            tradeNotice = if (closed == null) "فروش آزمایشی ممکن نشد"
            else "فروش آزمایشی ${closed.name}: " +
                    PaperTradeStore.resultText(closed, closed.closePrice, persian)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PaperTradeStore.clearClosed(ctx) }
            reloadTrades()
            tradeNotice = null
        }
    }

    fun checkNobitex(coin: PumpScanner.PumpCoin) {
        if (nobitex.containsKey(coin.id)) return
        viewModelScope.launch {
            val result = NobitexMarkets.check(ctx, coin.symbol)
            nobitex = nobitex + (coin.id to result)
        }
    }
}
