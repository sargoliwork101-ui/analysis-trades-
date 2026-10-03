package com.pulse.market.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AiReviewStore
import com.pulse.market.data.CoinPrices
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpScanner
import com.pulse.market.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تب «معامله‌های من» — هر کوینی که خرید آزمایشی برایش ثبت کرده‌ای اینجا می‌ماند.
 *
 * قیمت‌ها مستقل از اسکن پامپ و مستقیم برای همین کوین‌ها گرفته می‌شوند، پس حتی اگر
 * کوین از فهرست پامپ خارج شده باشد، نتیجه‌ی خرید تو قابل پیگیری است.
 *
 * حتی آفلاین هم کار می‌کند: آخرین قیمت‌ها روی همین گوشی کش می‌شوند، پس اگر اینترنت
 * نبود کیف پول با «آخرین مقدار موجود» نشان داده می‌شود و فقط برچسب «آپدیت نشده» می‌خورد.
 * با زدن روی هر معامله، صفحه‌ی جزئیات (نمودار + داده‌ها + تحلیل AI) باز می‌شود.
 */
@Composable
fun PortfolioCategory(persian: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trades by remember { mutableStateOf<List<PaperTradeStore.Trade>>(emptyList()) }
    var account by remember { mutableStateOf(PaperTradeStore.account(context)) }
    // با آخرین قیمت‌های کش‌شده شروع می‌شود تا آفلاین هم چیزی برای نمایش باشد.
    var prices by remember { mutableStateOf(PaperTradeStore.cachedPrices(context)) }
    var priceAt by remember { mutableStateOf(PaperTradeStore.pricesUpdatedAt(context)) }
    var offline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    // وضعیت صفحه‌ی جزئیات و هوش مصنوعی
    var aiConfig by remember { mutableStateOf(PumpAiConfig()) }
    var selectedCoinId by remember { mutableStateOf<String?>(null) }
    var histories by remember { mutableStateOf<Map<String, List<AiReviewStore.Entry>>>(emptyMap()) }
    var marketCoins by remember { mutableStateOf<Map<String, PumpScanner.PumpCoin>>(emptyMap()) }
    var aiBusyCoinId by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        aiConfig = withContext(Dispatchers.IO) { PumpAiConfigStore.load(context) }
    }

    suspend fun refresh(showNotice: Boolean) {
        busy = true
        // finally تضمین می‌کند دکمه‌ی «به‌روزرسانی» حتی با خطای غیرمنتظره دوباره فعال
        // شود؛ وگرنه یک استثنا در میانه‌ی مسیر، دکمه را برای همیشه روی «در حال گرفتن…» قفل می‌کرد.
        try {
            val loaded = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
            val ids = loaded.filter { it.isOpen }.map { it.coinId }
            val fresh = if (ids.isEmpty()) emptyMap() else CoinPrices.fetch(ids)
            if (fresh.isNotEmpty()) {
                val closed = withContext(Dispatchers.IO) { PaperTradeStore.settle(context, fresh) }
                if (closed.isNotEmpty() && showNotice) {
                    notice = closed.joinToString(" • ") { trade ->
                        "${trade.name}: ${trade.closeReason?.label ?: "بسته شد"} — " +
                                PaperTradeStore.resultText(trade, trade.closePrice, persian)
                    }
                }
                prices = prices + fresh
                priceAt = System.currentTimeMillis()
                offline = false
                withContext(Dispatchers.IO) { PaperTradeStore.savePrices(context, fresh, priceAt) }
            } else if (ids.isNotEmpty()) {
                // اینترنت نبود یا سرویس پاسخ نداد؛ آخرین قیمت‌ها را نگه می‌داریم.
                offline = true
                if (showNotice) {
                    notice = "قیمت تازه گرفته نشد؛ اینترنت را بررسی کن. کیف پول با آخرین قیمت موجود نشان داده می‌شود."
                }
            }
            trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
            account = withContext(Dispatchers.IO) { PaperTradeStore.account(context) }
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh(showNotice = false) }

    // با باز شدن جزئیات: تاریخچه‌ی تحلیل و داده‌ی کاملِ بازار گرفته می‌شود.
    LaunchedEffect(selectedCoinId) {
        val id = selectedCoinId ?: return@LaunchedEffect
        aiError = null
        histories = histories + (id to withContext(Dispatchers.IO) { AiReviewStore.history(context, id) })
        if (marketCoins[id] == null) {
            val coin = withContext(Dispatchers.IO) { PumpScanner.fetchCoin(id) }
            if (coin != null) {
                marketCoins = marketCoins + (id to coin)
                coin.price?.let { p ->
                    prices = prices + (id to p)
                    priceAt = System.currentTimeMillis()
                    offline = false
                    withContext(Dispatchers.IO) { PaperTradeStore.savePrices(context, mapOf(id to p), priceAt) }
                }
            }
        }
    }

    fun runWalletAi(trade: PaperTradeStore.Trade) {
        val config = aiConfig
        if (!config.anyReady || aiBusyCoinId != null) return
        aiBusyCoinId = trade.coinId
        aiError = null
        scope.launch {
            val coin = marketCoins[trade.coinId]
                ?: withContext(Dispatchers.IO) { PumpScanner.fetchCoin(trade.coinId) }
                ?: PumpScanner.PumpCoin(
                    id = trade.coinId,
                    symbol = trade.symbol,
                    name = trade.name,
                    price = prices[trade.coinId] ?: trade.entryPrice
                )
            if (marketCoins[trade.coinId] == null) marketCoins = marketCoins + (trade.coinId to coin)
            val outcome = withContext(Dispatchers.IO) { PumpAiReviewer.review(config, coin) }
            outcome.review?.let {
                withContext(Dispatchers.IO) {
                    AiReviewStore.add(context, trade.coinId, trade.symbol, trade.name, it)
                }
                histories = histories +
                        (trade.coinId to withContext(Dispatchers.IO) { AiReviewStore.history(context, trade.coinId) })
            }
            outcome.error?.let { aiError = it }
            aiBusyCoinId = null
        }
    }

    val open = trades.filter { it.isOpen }
    val closed = trades.filterNot { it.isOpen }
    val summary = PaperTradeStore.summarize(trades, prices, account)

    // پنجره‌های ویرایش
    var showCapital by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PaperTradeStore.Trade?>(null) }
    // برای کارایی، تاریخچه به‌صورت صفحه‌ای نشان داده می‌شود تا صدها کارت یک‌جا رندر نشوند.
    var showAllHistory by remember { mutableStateOf(false) }
    val historyPageSize = 30

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            "معامله‌های من",
            "خرید و فروش آزمایشی؛ بدون پول واقعی و فقط روی همین گوشی ذخیره می‌شود."
        )

        TradeSummaryCard(
            summary = summary,
            persian = persian,
            onEditCapital = { showCapital = true }
        )

        if (offline && prices.isNotEmpty()) {
            val stamp = priceAt.takeIf { it > 0L }?.let { Format.dateTime(it, persian) }
            InfoCard(
                "آفلاین: قیمت تازه گرفته نشد. کیف پول با آخرین قیمت موجود" +
                        (stamp?.let { " (ثبت‌شده در $it)" } ?: "") + " نشان داده می‌شود و آپدیت نشده است."
            )
        }

        Button(
            onClick = { scope.launch { refresh(showNotice = true) } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (busy) "در حال گرفتن قیمت‌ها…" else "به‌روزرسانی قیمت‌ها و بررسی حد سود/ضرر",
                fontSize = 12.sp
            )
        }
        notice?.let { Hint(it) }

        if (trades.isEmpty()) {
            InfoCard(
                "هنوز معامله‌ای ثبت نکرده‌ای. در بخش «پامپ‌های کریپتو» روی یک کوین بزن و از قسمت " +
                        "«خرید و فروش آزمایشی» یک خرید ثبت کن؛ از آن به بعد همین‌جا پیگیری می‌شود."
            )
        } else {
            Hint("روی هر معامله بزن تا نمودار، همه‌ی اطلاعات و تحلیل هوش مصنوعی‌اش را ببینی.")
        }

        if (open.isNotEmpty()) {
            SectionHeader("معامله‌های باز", "با رسیدن قیمت به حد سود یا ضرر، خودکار بسته می‌شوند.")
            for (trade in open) key(trade.id) {
                WalletTradeCard(
                    trade = trade,
                    price = prices[trade.coinId],
                    persian = persian,
                    onClick = { selectedCoinId = trade.coinId },
                    onSell = {
                        scope.launch {
                            val price = prices[trade.coinId]
                                ?: CoinPrices.fetch(listOf(trade.coinId))[trade.coinId]
                            val result = withContext(Dispatchers.IO) {
                                PaperTradeStore.sell(context, trade.id, price)
                            }
                            notice = if (result == null) "قیمت تازه برای فروش پیدا نشد؛ دوباره تلاش کن."
                            else "فروش ${result.name}: " +
                                    PaperTradeStore.resultText(result, result.closePrice, persian)
                            refresh(showNotice = false)
                        }
                    },
                    onEdit = { editing = trade },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { PaperTradeStore.remove(context, trade.id) }
                            refresh(showNotice = false)
                        }
                    }
                )
            }
        }

        if (closed.isNotEmpty()) {
            SectionHeader("تاریخچه", "نتیجه‌ی نهایی معامله‌های بسته‌شده.")
            val shownClosed = if (showAllHistory) closed else closed.take(historyPageSize)
            for (trade in shownClosed) key(trade.id) {
                WalletTradeCard(
                    trade = trade,
                    price = trade.closePrice,
                    persian = persian,
                    onClick = { selectedCoinId = trade.coinId },
                    onSell = null,
                    onEdit = { editing = trade },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { PaperTradeStore.remove(context, trade.id) }
                            refresh(showNotice = false)
                        }
                    }
                )
            }
            if (!showAllHistory && closed.size > historyPageSize) {
                TextButton(
                    onClick = { showAllHistory = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "نمایش همه‌ی ${Format.toPersianDigits("${closed.size}")} مورد",
                        fontSize = 11.5.sp
                    )
                }
            }
            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { PaperTradeStore.clearClosed(context) }
                        notice = null
                        refresh(showNotice = false)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("پاک کردن کل تاریخچه", fontSize = 11.5.sp) }
        }

        Hint(
            "این بخش شبیه‌ساز است: هیچ سفارشی به هیچ صرافی فرستاده نمی‌شود و هیچ پولی جابه‌جا نمی‌شود. " +
                    "کارمزد هر دو سمت در محاسبه‌ی سود لحاظ شده است."
        )
    }

    // ── پنجره‌ی سرمایه‌ی کیف ──
    if (showCapital) {
        CapitalEditDialog(
            currentCapital = account.capitalUsd,
            persian = persian,
            onSetCapital = { value ->
                scope.launch {
                    withContext(Dispatchers.IO) { PaperTradeStore.setCapital(context, value) }
                    refresh(showNotice = false)
                }
            },
            onAddCapital = { delta ->
                scope.launch {
                    withContext(Dispatchers.IO) { PaperTradeStore.addCapital(context, delta) }
                    refresh(showNotice = false)
                }
            },
            onResetCarried = {
                scope.launch {
                    withContext(Dispatchers.IO) { PaperTradeStore.resetCarried(context) }
                    notice = null
                    refresh(showNotice = false)
                }
            },
            onDismiss = { showCapital = false }
        )
    }

    // ── پنجره‌ی ویرایش معامله ──
    editing?.let { target ->
        EditTradeDialog(
            trade = target,
            persian = persian,
            onSave = { edit ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        PaperTradeStore.edit(
                            context = context,
                            tradeId = target.id,
                            amountUsd = edit.amountUsd,
                            entryPrice = edit.entryPrice,
                            takeProfitPct = edit.takeProfitPct,
                            stopLossPct = edit.stopLossPct,
                            feePct = edit.feePct,
                            note = edit.note,
                            clearTakeProfit = edit.clearTakeProfit,
                            clearStopLoss = edit.clearStopLoss,
                            closePrice = edit.closePrice,
                            closeReason = edit.closeReason
                        )
                    }
                    notice = "معامله ویرایش شد."
                    refresh(showNotice = false)
                }
            },
            onDismiss = { editing = null }
        )
    }

    // ── صفحه‌ی جزئیات ──
    val selectedTrade = selectedCoinId?.let { id -> trades.firstOrNull { it.coinId == id } }
    if (selectedTrade != null) {
        val coinId = selectedTrade.coinId
        WalletDetailSheet(
            trade = selectedTrade,
            price = prices[coinId],
            priceStale = offline || prices[coinId] == null,
            priceAt = priceAt,
            persian = persian,
            aiEnabled = aiConfig.enabled,
            aiReady = aiConfig.anyReady,
            aiBusy = aiBusyCoinId == coinId,
            aiError = aiError,
            history = histories[coinId].orEmpty(),
            marketCoin = marketCoins[coinId],
            onAiReview = { runWalletAi(selectedTrade) },
            onSell = if (selectedTrade.isOpen) {
                {
                    scope.launch {
                        val price = prices[coinId]
                            ?: CoinPrices.fetch(listOf(coinId))[coinId]
                        val result = withContext(Dispatchers.IO) {
                            PaperTradeStore.sell(context, selectedTrade.id, price)
                        }
                        notice = if (result == null) "قیمت تازه برای فروش پیدا نشد؛ دوباره تلاش کن."
                        else "فروش ${result.name}: " +
                                PaperTradeStore.resultText(result, result.closePrice, persian)
                        selectedCoinId = null
                        refresh(showNotice = false)
                    }
                }
            } else null,
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { PaperTradeStore.remove(context, selectedTrade.id) }
                    selectedCoinId = null
                    refresh(showNotice = false)
                }
            },
            onOpenLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onDismiss = { selectedCoinId = null }
        )
    }
}
