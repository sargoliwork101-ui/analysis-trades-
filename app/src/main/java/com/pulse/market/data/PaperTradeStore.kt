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

    /** کارمزد پیش‌فرض هر سمت معامله (درصد) — نزدیک به کارمزد معمول نوبیتکس */
    const val DEFAULT_FEE_PCT = 0.2

    /** بیشترین تعداد پله‌ی مجاز در خرید پله‌ای */
    const val MAX_STEPS = 10

    /** دلیل بسته‌شدن معامله */
    enum class CloseReason(val label: String) {
        MANUAL("فروش دستی"),
        TAKE_PROFIT("رسیدن به حد سود"),
        STOP_LOSS("رسیدن به حد ضرر")
    }

    /**
     * یک «پله»ی خرید در محدوده‌ی ورود (شبیه سفارش خرید پله‌ای نوبیتکس).
     * پله وقتی «پر» می‌شود که قیمت بازار به قیمت هدفِ آن پله یا پایین‌تر برسد.
     */
    @Serializable
    data class LadderStep(
        /** قیمت هدف این پله (خرید محدود، دلار) */
        val price: Double,
        /** مبلغ اختصاص‌یافته به این پله (دلار) */
        val amountUsd: Double,
        val filled: Boolean = false,
        val filledAt: Long? = null
    )

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
        /** کارمزد هر سمت معامله بر حسب درصد (پیش‌فرض نوبیتکس ≈ ۰٫۲٪) */
        val feePct: Double = DEFAULT_FEE_PCT,
        /** یادداشت کوتاه کاربر */
        val note: String = "",
        /**
         * پله‌های خرید پله‌ای. خالی = خرید ساده‌ی تک‌مرحله‌ای (مثل نسخه‌های قبل).
         * قیمت/مبلغِ بالای این کلاس همیشه «مؤثر» (پله‌های پرشده) را نشان می‌دهند.
         */
        val steps: List<LadderStep> = emptyList(),
        /** سقف محدوده‌ی ورود = قیمت پله‌ی اول (فقط برای نمایش) */
        val entryHigh: Double? = null,
        /** کف محدوده‌ی ورود = قیمت آخرین پله (فقط برای نمایش) */
        val entryLow: Double? = null
    ) {
        val isOpen: Boolean get() = closedAt == null || closePrice == null

        /** آیا این معامله خرید پله‌ای است؟ */
        val isLadder: Boolean get() = steps.isNotEmpty()

        /** کل سرمایه‌ی برنامه‌ریزی‌شده روی همه‌ی پله‌ها (پر و پرنشده) */
        val plannedAmountUsd: Double get() = if (isLadder) steps.sumOf { it.amountUsd } else amountUsd

        val filledStepCount: Int get() = steps.count { it.filled }
        val pendingStepCount: Int get() = steps.count { !it.filled }

        val closeReason: CloseReason?
            get() = closeReasonName?.let { name ->
                CloseReason.entries.firstOrNull { it.name == name }
            }

        /** کارمزد به‌صورت ضریب (۰٫۰۰۲ برای ۰٫۲٪) */
        val feeRate: Double
            get() = (feePct.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0).coerceAtMost(5.0) / 100.0

        /** کارمزد خرید (دلار) */
        val buyFeeUsd: Double
            get() = amountUsd * feeRate

        /** تعداد واحد کوین پس از کسر کارمزد خرید */
        val units: Double
            get() = if (entryPrice > 0.0) (amountUsd - buyFeeUsd) / entryPrice else 0.0

        /** قیمتی که حد سود در آن فعال می‌شود */
        val takeProfitPrice: Double?
            get() = takeProfitPct?.takeIf { it > 0.0 }?.let { entryPrice * (1.0 + it / 100.0) }

        /** قیمتی که حد ضرر در آن فعال می‌شود */
        val stopLossPrice: Double?
            get() = stopLossPct?.takeIf { it > 0.0 }?.let { entryPrice * (1.0 - it / 100.0) }

        /** ارزش فروش پس از کسر کارمزد فروش */
        fun exitValueUsd(price: Double?): Double? {
            val reference = (if (isOpen) price else closePrice)?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val gross = units * reference
            return gross - gross * feeRate
        }

        /** کارمزد فروش (دلار) */
        fun sellFeeUsd(price: Double?): Double? {
            val reference = (if (isOpen) price else closePrice)?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            return units * reference * feeRate
        }

        /** مجموع کارمزد رفت و برگشت */
        fun totalFeeUsd(price: Double?): Double? = sellFeeUsd(price)?.let { buyFeeUsd + it }

        /** سود/زیان خالص دلاری — کارمزد خرید و فروش کسر شده است */
        fun profitUsd(price: Double?): Double? =
            exitValueUsd(price)?.let { it - amountUsd }

        /** درصد سود/زیان خالص نسبت به سرمایه‌ی اولیه */
        fun profitPct(price: Double?): Double? {
            if (amountUsd <= 0.0) return null
            return profitUsd(price)?.let { it / amountUsd * 100.0 }
        }

        /** تغییر خام قیمت بدون کارمزد — برای مقایسه */
        fun rawChangePct(price: Double?): Double? {
            val reference = (if (isOpen) price else closePrice)?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            if (entryPrice <= 0.0) return null
            return (reference - entryPrice) / entryPrice * 100.0
        }

        /** قیمتی که در آن، بعد از کارمزد رفت و برگشت، سر به سر می‌شوی */
        val breakEvenPrice: Double?
            get() {
                val u = units
                if (u <= 0.0) return null
                val netFactor = 1.0 - feeRate
                if (netFactor <= 0.0) return null
                return amountUsd / (u * netFactor)
            }
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

    /**
     * ثبت خرید آزمایشی تازه.
     *
     * @param stepCount     تعداد پله‌ی خرید پله‌ای؛ ۱ یعنی خرید ساده‌ی تک‌مرحله‌ای.
     * @param rangeFloorPct کف محدوده‌ی ورود بر حسب درصدِ پایین‌ترِ قیمت فعلی
     *                      (مثلاً ۸ یعنی پله‌ها تا ۸٪ پایین‌تر پخش شوند). null = بدون پله.
     */
    fun buy(
        context: Context,
        coin: PumpScanner.PumpCoin,
        amountUsd: Double,
        takeProfitPct: Double?,
        stopLossPct: Double?,
        feePct: Double = DEFAULT_FEE_PCT,
        stepCount: Int = 1,
        rangeFloorPct: Double? = null,
        now: Long = System.currentTimeMillis()
    ): Trade? {
        val price = coin.price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val amount = amountUsd.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(1.0, 1_000_000.0)
            ?: return null
        val steps = buildLadderSteps(price, amount, stepCount, rangeFloorPct, now)
        val base = Trade(
            id = "${coin.id}_$now",
            coinId = coin.id,
            symbol = coin.symbol.uppercase(),
            name = coin.name,
            entryPrice = price,
            amountUsd = amount,
            openedAt = now,
            takeProfitPct = takeProfitPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 1000.0),
            stopLossPct = stopLossPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 99.0),
            feePct = feePct.takeIf { it.isFinite() && it >= 0.0 }?.coerceAtMost(5.0) ?: DEFAULT_FEE_PCT,
            steps = steps,
            entryHigh = steps.firstOrNull()?.price,
            entryLow = steps.lastOrNull()?.price
        )
        // در خرید پله‌ای، قیمت/مبلغِ مؤثر از پله‌های پرشده بازمحاسبه می‌شود
        // (پله‌ی اول همان لحظه پر می‌شود، بقیه با افت قیمت).
        val trade = if (steps.isEmpty()) base else recomputeFromFills(base)
        write(context, all(context) + trade)
        return trade
    }

    /**
     * ساخت پله‌های خرید در «محدوده‌ی ورود»: از قیمت فعلی تا [rangeFloorPct]٪ پایین‌تر،
     * در [stepCount] پله‌ی هم‌فاصله و هم‌مبلغ. پله‌ی اول (قیمت فعلی) همان لحظه پر می‌شود؛
     * پله‌های پایین‌تر با رسیدن قیمت به آن‌ها در بررسی‌های بعدی پر می‌شوند.
     * خروجی خالی = خرید ساده‌ی تک‌مرحله‌ای (بدون پله).
     */
    internal fun buildLadderSteps(
        price: Double,
        amountUsd: Double,
        stepCount: Int,
        rangeFloorPct: Double?,
        now: Long
    ): List<LadderStep> {
        val n = stepCount.coerceIn(1, MAX_STEPS)
        val floor = rangeFloorPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 90.0)
        if (n < 2 || floor == null || price <= 0.0 || amountUsd <= 0.0) return emptyList()
        val per = amountUsd / n
        val floorFrac = floor / 100.0
        val threshold = price * (1.0 - LEVEL_TOLERANCE)
        return List(n) { i ->
            val frac = floorFrac * i / (n - 1)          // i=0 → ۰ ، i=n-1 → floorFrac
            val stepPrice = price * (1.0 - frac)
            val fillNow = stepPrice >= threshold        // فقط پله‌ی اول همین حالا پر می‌شود
            LadderStep(
                price = stepPrice,
                amountUsd = per,
                filled = fillNow,
                filledAt = if (fillNow) now else null
            )
        }
    }

    /** بازمحاسبه‌ی قیمت میانگین و مبلغِ مؤثر از پله‌های پرشده (میانگین وزنیِ هزینه). */
    internal fun recomputeFromFills(trade: Trade): Trade {
        if (trade.steps.isEmpty()) return trade
        val filled = trade.steps.filter { it.filled && it.price > 0.0 && it.amountUsd > 0.0 }
        if (filled.isEmpty()) return trade
        val totalAmount = filled.sumOf { it.amountUsd }
        val denom = filled.sumOf { it.amountUsd / it.price }   // Σ (مبلغ ÷ قیمت)
        val avg = if (denom > 0.0) totalAmount / denom else trade.entryPrice
        return if (avg.isFinite() && avg > 0.0 && totalAmount > 0.0)
            trade.copy(entryPrice = avg, amountUsd = totalAmount)
        else trade
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
        var changed = false
        val updated = trades.map { trade ->
            if (!trade.isOpen) return@map trade
            val price = pricesByCoinId[trade.coinId]?.takeIf { it.isFinite() && it > 0.0 }
                ?: return@map trade
            // ۱) پله‌های پایین‌تری که قیمت به آن‌ها رسیده پر می‌شوند و میانگین ورود بازمحاسبه می‌شود.
            val afterFill = fillReachedSteps(trade, price, now)
            if (afterFill !== trade) changed = true
            // ۲) بعد از پرشدن پله‌ها، رسیدن به حد سود/ضرر بررسی می‌شود.
            val reason = closeReasonFor(afterFill, price) ?: return@map afterFill
            val closed = afterFill.copy(
                closedAt = now,
                closePrice = triggerPrice(afterFill, reason, price),
                closeReasonName = reason.name
            )
            justClosed += closed
            changed = true
            closed
        }
        if (changed) write(context, updated)
        return justClosed
    }

    /** پله‌هایی که قیمت فعلی به آن‌ها رسیده (قیمت ≤ قیمتِ پله) را پر می‌کند و میانگین را بازمحاسبه می‌کند. */
    internal fun fillReachedSteps(trade: Trade, price: Double, now: Long): Trade {
        if (trade.steps.isEmpty() || !price.isFinite() || price <= 0.0) return trade
        if (trade.steps.none { !it.filled }) return trade
        var any = false
        val newSteps = trade.steps.map { step ->
            if (!step.filled && price <= step.price * (1.0 + LEVEL_TOLERANCE)) {
                any = true
                step.copy(filled = true, filledAt = now)
            } else step
        }
        return if (any) recomputeFromFills(trade.copy(steps = newSteps)) else trade
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

    /** مجموع کارمزد پرداخت‌شده در کیف */
    fun totalFees(trades: List<Trade>, pricesByCoinId: Map<String, Double>): Double =
        trades.sumOf { trade ->
            trade.totalFeeUsd(pricesByCoinId[trade.coinId]) ?: trade.buyFeeUsd
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
