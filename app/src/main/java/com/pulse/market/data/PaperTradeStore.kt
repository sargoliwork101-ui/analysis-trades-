package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * «معامله‌ی آزمایشی» (paper trading) — شبیه‌ساز خرید و فروش بدون پول واقعی.
 *
 * هدف: بتوانی تصمیم‌هایی که همین برنامه پیشنهاد می‌دهد را ثبت کنی و بعداً ببینی
 * چقدر سود یا زیان می‌کردی. هر معامله می‌تواند «حد سود» و «حد ضرر» درصدی داشته
 * باشد؛ با هر اسکن تازه، قیمت‌ها بررسی می‌شوند و معامله‌ای که به حد خود رسیده
 * خودکار بسته می‌شود.
 *
 * همه‌چیز فقط روی همین گوشی ذخیره می‌شود و هیچ سفارشی به هیچ صرافی نمی‌رود.
 */
object PaperTradeStore {

    private const val PREF = "pulse_paper_trades"
    private const val KEY_TRADES = "trades"
    private const val MAX_TRADES = 200

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** رواداری مقایسه‌ی قیمت با سطح حد سود/ضرر (خطای اعشار شناور) */
    private const val LEVEL_TOLERANCE = 1e-9

    /** دلیل بسته‌شدن معامله */
    enum class CloseReason(val label: String) {
        MANUAL("فروش دستی"),
        TAKE_PROFIT("رسیدن به حد سود"),
        STOP_LOSS("رسیدن به حد ضرر")
    }

    @Serializable
    data class Trade(
        val id: String,
        val coinId: String,
        val symbol: String,
        val name: String,
        /** قیمت لحظه‌ی خرید (دلار) */
        val entryPrice: Double,
        /** مبلغ فرضی سرمایه‌گذاری‌شده (دلار) */
        val amountUsd: Double,
        val openedAt: Long,
        /** حد سود بر حسب درصد مثبت؛ null یعنی ندارد */
        val takeProfitPct: Double? = null,
        /** حد ضرر بر حسب درصد مثبت (۵ یعنی ۵٪ افت)؛ null یعنی ندارد */
        val stopLossPct: Double? = null,
        val closedAt: Long? = null,
        val closePrice: Double? = null,
        val closeReasonName: String? = null,
        /** یادداشت کوتاه کاربر */
        val note: String = ""
    ) {
        val isOpen: Boolean get() = closedAt == null || closePrice == null

        val closeReason: CloseReason?
            get() = closeReasonName?.let { name ->
                CloseReason.entries.firstOrNull { it.name == name }
            }

        /** تعداد واحد کوین که با این مبلغ خریده می‌شد */
        val units: Double
            get() = if (entryPrice > 0.0) amountUsd / entryPrice else 0.0

        /** قیمتی که حد سود در آن فعال می‌شود */
        val takeProfitPrice: Double?
            get() = takeProfitPct?.takeIf { it > 0.0 }?.let { entryPrice * (1.0 + it / 100.0) }

        /** قیمتی که حد ضرر در آن فعال می‌شود */
        val stopLossPrice: Double?
            get() = stopLossPct?.takeIf { it > 0.0 }?.let { entryPrice * (1.0 - it / 100.0) }

        /** درصد سود/زیان با قیمت داده‌شده (برای معامله‌ی بسته، قیمت بسته‌شدن) */
        fun profitPct(price: Double?): Double? {
            val reference = if (isOpen) price else closePrice
            if (reference == null || !reference.isFinite() || entryPrice <= 0.0) return null
            return (reference - entryPrice) / entryPrice * 100.0
        }

        /** سود/زیان دلاری */
        fun profitUsd(price: Double?): Double? =
            profitPct(price)?.let { amountUsd * it / 100.0 }
    }

    /** خلاصه‌ی عملکرد کیف آزمایشی */
    data class Summary(
        val openCount: Int,
        val closedCount: Int,
        val wins: Int,
        val losses: Int,
        val realizedUsd: Double,
        val openUsd: Double,
        val investedOpenUsd: Double
    ) {
        val winRatePct: Double
            get() = if (closedCount == 0) 0.0 else wins * 100.0 / closedCount
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<Trade> {
        val raw = prefs(context).getString(KEY_TRADES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Trade.serializer()), raw)
        }.getOrDefault(emptyList())
            .filter { it.coinId.isNotBlank() && it.entryPrice > 0.0 && it.amountUsd > 0.0 }
            .sortedByDescending { it.openedAt }
            .take(MAX_TRADES)
    }

    fun open(context: Context, trades: List<Trade>): List<Trade> = trades.filter { it.isOpen }

    @Synchronized
    private fun write(context: Context, trades: List<Trade>) {
        val bounded = trades.sortedByDescending { it.openedAt }.take(MAX_TRADES)
        prefs(context).edit()
            .putString(KEY_TRADES, json.encodeToString(ListSerializer(Trade.serializer()), bounded))
            .apply()
    }

    /** ثبت خرید آزمایشی تازه */
    fun buy(
        context: Context,
        coin: PumpScanner.PumpCoin,
        amountUsd: Double,
        takeProfitPct: Double?,
        stopLossPct: Double?,
        now: Long = System.currentTimeMillis()
    ): Trade? {
        val price = coin.price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val amount = amountUsd.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(1.0, 1_000_000.0)
            ?: return null
        val trade = Trade(
            id = "${coin.id}_$now",
            coinId = coin.id,
            symbol = coin.symbol.uppercase(),
            name = coin.name,
            entryPrice = price,
            amountUsd = amount,
            openedAt = now,
            takeProfitPct = takeProfitPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 1000.0),
            stopLossPct = stopLossPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 99.0)
        )
        write(context, all(context) + trade)
        return trade
    }

    /** فروش دستی */
    fun sell(
        context: Context,
        tradeId: String,
        price: Double?,
        reason: CloseReason = CloseReason.MANUAL,
        now: Long = System.currentTimeMillis()
    ): Trade? {
        val safePrice = price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val trades = all(context)
        val target = trades.firstOrNull { it.id == tradeId && it.isOpen } ?: return null
        val closed = target.copy(closedAt = now, closePrice = safePrice, closeReasonName = reason.name)
        write(context, trades.map { if (it.id == tradeId) closed else it })
        return closed
    }

    fun remove(context: Context, tradeId: String) {
        write(context, all(context).filterNot { it.id == tradeId })
    }

    fun clearClosed(context: Context) {
        write(context, all(context).filter { it.isOpen })
    }

    /**
     * بررسی حد سود/حد ضرر با قیمت‌های تازه و بستن خودکار معامله‌های رسیده.
     * خروجی: معامله‌هایی که همین حالا بسته شدند (برای نمایش پیام به کاربر).
     */
    fun settle(
        context: Context,
        pricesByCoinId: Map<String, Double>,
        now: Long = System.currentTimeMillis()
    ): List<Trade> {
        if (pricesByCoinId.isEmpty()) return emptyList()
        val trades = all(context)
        if (trades.none { it.isOpen }) return emptyList()
        val justClosed = mutableListOf<Trade>()
        val updated = trades.map { trade ->
            if (!trade.isOpen) return@map trade
            val price = pricesByCoinId[trade.coinId]?.takeIf { it.isFinite() && it > 0.0 }
                ?: return@map trade
            val reason = closeReasonFor(trade, price) ?: return@map trade
            val closed = trade.copy(
                closedAt = now,
                closePrice = triggerPrice(trade, reason, price),
                closeReasonName = reason.name
            )
            justClosed += closed
            closed
        }
        if (justClosed.isNotEmpty()) write(context, updated)
        return justClosed
    }

    /** منطق خالصِ «آیا این معامله باید بسته شود؟» — قابل تست بدون اندروید. */
    internal fun closeReasonFor(trade: Trade, price: Double): CloseReason? {
        if (!trade.isOpen || !price.isFinite() || price <= 0.0) return null
        val takeProfit = trade.takeProfitPrice
        val stopLoss = trade.stopLossPrice
        // اگر هر دو در یک به‌روزرسانی فعال شده باشند، محافظه‌کارانه حد ضرر مقدم است.
        // مقایسه‌ی اعشاری با رواداری کوچک: entry*(1+10/100) ممکن است ۱۱۰٫۰۰۰۰۰۰۰۰۰۰۰۰۰۱ شود
        // و دقیقاً روی همان سطح، حد سود فعال نمی‌شد.
        if (stopLoss != null && price <= stopLoss * (1.0 + LEVEL_TOLERANCE)) return CloseReason.STOP_LOSS
        if (takeProfit != null && price >= takeProfit * (1.0 - LEVEL_TOLERANCE)) return CloseReason.TAKE_PROFIT
        return null
    }

    /** قیمت ثبت‌شده هنگام بسته‌شدن خودکار: همان سطح حد، نه قیمتِ پرش‌کرده. */
    internal fun triggerPrice(trade: Trade, reason: CloseReason, price: Double): Double = when (reason) {
        CloseReason.TAKE_PROFIT -> trade.takeProfitPrice ?: price
        CloseReason.STOP_LOSS -> trade.stopLossPrice ?: price
        CloseReason.MANUAL -> price
    }

    /** خلاصه‌ی کیف: سود محقق‌شده، سود باز و نرخ برد. */
    fun summarize(trades: List<Trade>, pricesByCoinId: Map<String, Double>): Summary {
        var wins = 0
        var losses = 0
        var realized = 0.0
        var openPnl = 0.0
        var investedOpen = 0.0
        var openCount = 0
        var closedCount = 0
        for (trade in trades) {
            if (trade.isOpen) {
                openCount++
                investedOpen += trade.amountUsd
                openPnl += trade.profitUsd(pricesByCoinId[trade.coinId]) ?: 0.0
            } else {
                closedCount++
                val pnl = trade.profitUsd(null) ?: 0.0
                realized += pnl
                if (pnl > 0.0) wins++ else if (pnl < 0.0) losses++
            }
        }
        return Summary(
            openCount = openCount,
            closedCount = closedCount,
            wins = wins,
            losses = losses,
            realizedUsd = realized,
            openUsd = openPnl,
            investedOpenUsd = investedOpen
        )
    }

    /** متن کوتاه نتیجه‌ی یک معامله برای نمایش */
    fun resultText(trade: Trade, price: Double?, persianDigits: Boolean): String {
        val pct = trade.profitPct(price) ?: return "قیمت تازه در دسترس نیست"
        val usd = trade.profitUsd(price) ?: 0.0
        val sign = if (pct >= 0.0) "+" else "−"
        val pctText = format(abs(pct), persianDigits)
        val usdText = format(abs(usd), persianDigits)
        return "$sign$pctText٪ ($sign$usdText دلار)"
    }

    private fun format(value: Double, persianDigits: Boolean): String {
        val text = String.format(java.util.Locale.US, "%.2f", value)
        return if (persianDigits) com.pulse.market.ui.Format.toPersianDigits(text) else text
    }
}
