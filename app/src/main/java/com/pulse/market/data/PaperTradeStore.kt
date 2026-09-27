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
    private const val KEY_PLANS = "plans"
    private const val MAX_TRADES = 200
    private const val MAX_PLANS = 30
    const val MAX_STEPS = 5

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** رواداری مقایسه‌ی قیمت با سطح حد سود/ضرر (خطای اعشار شناور) */
    private const val LEVEL_TOLERANCE = 1e-9

    /** کارمزد پیش‌فرض هر سمت معامله (درصد) — نزدیک به کارمزد معمول نوبیتکس */
    const val DEFAULT_FEE_PCT = 0.2

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
        /** کارمزد هر سمت معامله بر حسب درصد (پیش‌فرض نوبیتکس ≈ ۰٫۲٪) */
        val feePct: Double = DEFAULT_FEE_PCT,
        /** یادداشت کوتاه کاربر */
        val note: String = ""
    ) {
        val isOpen: Boolean get() = closedAt == null || closePrice == null

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
     * «طرح خرید» شبیه سفارش‌های نوبیتکس: محدوده‌ی ورود + خرید پله‌ای + حد سود/ضرر.
     *
     * برخلاف خرید فوری، هیچ پولی تا وقتی قیمت وارد محدوده نشود درگیر نمی‌شود؛ هر
     * پله سفارش limit خودش را دارد و با رسیدن قیمت به آن پله، همان بخش از سرمایه
     * خرید می‌شود (میانگین‌گیری در ریزش). حد سود و حد ضرر روی هر پله‌ی پرشده
     * جداگانه اعمال می‌شود.
     */
    @Serializable
    data class Plan(
        val id: String,
        val coinId: String,
        val symbol: String,
        val name: String,
        /** کل سرمایه‌ی طرح (دلار) که بین پله‌ها تقسیم می‌شود */
        val totalUsd: Double,
        /** بالاترین قیمت محدوده‌ی ورود (پله‌ی اول) */
        val entryHigh: Double,
        /** پایین‌ترین قیمت محدوده‌ی ورود (پله‌ی آخر) */
        val entryLow: Double,
        val steps: Int,
        val filledSteps: Int = 0,
        val takeProfitPct: Double? = null,
        val stopLossPct: Double? = null,
        val feePct: Double = DEFAULT_FEE_PCT,
        val createdAt: Long = 0L,
        val lastFillAt: Long? = null,
        val canceledAt: Long? = null,
        val cancelReason: String = ""
    ) {
        val isActive: Boolean get() = canceledAt == null && filledSteps < steps

        /** مبلغ هر پله */
        val stepUsd: Double get() = if (steps > 0) totalUsd / steps else 0.0

        /** قیمت سفارش هر پله، از بالای محدوده به پایین */
        val ladder: List<Double> get() = ladderPrices(entryHigh, entryLow, steps)

        /** قیمتی که کل طرح باطل می‌شود (زیر محدوده + حد ضرر) */
        val cancelPrice: Double?
            get() = stopLossPct?.takeIf { it > 0.0 }?.let { entryLow * (1.0 - it / 100.0) }
    }

    /** پله‌بندی خطی قیمت‌ها: پله‌ی اول روی سقف محدوده، پله‌ی آخر روی کف. */
    internal fun ladderPrices(high: Double, low: Double, steps: Int): List<Double> {
        val count = steps.coerceIn(1, MAX_STEPS)
        val top = maxOf(high, low)
        val bottom = minOf(high, low)
        if (count == 1) return listOf(top)
        val gap = (top - bottom) / (count - 1)
        return (0 until count).map { index -> top - gap * index }
    }

    /** با این قیمت، چند پله باید پر شده باشد؟ (سفارش خرید limit) */
    internal fun filledStepsAt(plan: Plan, price: Double): Int {
        if (!price.isFinite() || price <= 0.0) return plan.filledSteps
        val reached = plan.ladder.count { price <= it * (1.0 + LEVEL_TOLERANCE) }
        return maxOf(plan.filledSteps, reached.coerceAtMost(plan.steps))
    }

    /** آیا قیمت آن‌قدر ریخته که ادامه‌ی طرح بی‌معنی شود؟ */
    internal fun planShouldCancel(plan: Plan, price: Double): Boolean {
        val limit = plan.cancelPrice ?: return false
        return price.isFinite() && price > 0.0 && price <= limit * (1.0 + LEVEL_TOLERANCE)
    }

    @Synchronized
    fun plans(context: Context): List<Plan> {
        val raw = prefs(context).getString(KEY_PLANS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Plan.serializer()), raw)
        }.getOrDefault(emptyList())
            .filter { it.coinId.isNotBlank() && it.totalUsd > 0.0 && it.steps in 1..MAX_STEPS }
            .sortedByDescending { it.createdAt }
            .take(MAX_PLANS)
    }

    @Synchronized
    private fun writePlans(context: Context, plans: List<Plan>) {
        val bounded = plans.sortedByDescending { it.createdAt }.take(MAX_PLANS)
        prefs(context).edit()
            .putString(KEY_PLANS, json.encodeToString(ListSerializer(Plan.serializer()), bounded))
            .apply()
    }

    /** ثبت طرح خرید پله‌ای؛ اگر قیمت همین حالا داخل محدوده باشد، پله‌های رسیده فوراً پر می‌شوند. */
    fun planBuy(
        context: Context,
        coin: PumpScanner.PumpCoin,
        totalUsd: Double,
        entryHigh: Double,
        entryLow: Double,
        steps: Int,
        takeProfitPct: Double?,
        stopLossPct: Double?,
        feePct: Double = DEFAULT_FEE_PCT,
        now: Long = System.currentTimeMillis()
    ): Plan? {
        val total = totalUsd.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(1.0, 1_000_000.0) ?: return null
        val high = entryHigh.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val low = entryLow.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val plan = Plan(
            id = "plan_${coin.id}_$now",
            coinId = coin.id,
            symbol = coin.symbol.uppercase(),
            name = coin.name,
            totalUsd = total,
            entryHigh = maxOf(high, low),
            entryLow = minOf(high, low),
            steps = steps.coerceIn(1, MAX_STEPS),
            takeProfitPct = takeProfitPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 1000.0),
            stopLossPct = stopLossPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 99.0),
            feePct = feePct.takeIf { it.isFinite() && it >= 0.0 }?.coerceAtMost(5.0) ?: DEFAULT_FEE_PCT,
            createdAt = now
        )
        writePlans(context, plans(context) + plan)
        coin.price?.let { settlePlans(context, mapOf(coin.id to it), now) }
        return plans(context).firstOrNull { it.id == plan.id } ?: plan
    }

    fun cancelPlan(context: Context, planId: String, now: Long = System.currentTimeMillis()) {
        writePlans(
            context,
            plans(context).map {
                if (it.id == planId && it.isActive)
                    it.copy(canceledAt = now, cancelReason = "لغو دستی")
                else it
            }
        )
    }

    fun removePlan(context: Context, planId: String) {
        writePlans(context, plans(context).filterNot { it.id == planId })
    }

    /**
     * بررسی طرح‌ها با قیمت‌های تازه: پرکردن پله‌های رسیده (ساخت معامله برای هر پله)
     * و باطل‌کردن طرح‌هایی که قیمت از کف محدوده هم پایین‌تر رفته است.
     * خروجی: پیام‌های خوانا برای نمایش به کاربر.
     */
    fun settlePlans(
        context: Context,
        pricesByCoinId: Map<String, Double>,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        if (pricesByCoinId.isEmpty()) return emptyList()
        val current = plans(context)
        if (current.none { it.isActive }) return emptyList()
        val messages = mutableListOf<String>()
        val newTrades = mutableListOf<Trade>()
        val updated = current.map { plan ->
            if (!plan.isActive) return@map plan
            val price = pricesByCoinId[plan.coinId]?.takeIf { it.isFinite() && it > 0.0 }
                ?: return@map plan
            val target = filledStepsAt(plan, price)
            var result = plan
            if (target > plan.filledSteps) {
                val ladder = plan.ladder
                for (index in plan.filledSteps until target) {
                    val stepPrice = ladder.getOrNull(index) ?: continue
                    newTrades += Trade(
                        id = "${plan.id}_step${index + 1}_$now",
                        coinId = plan.coinId,
                        symbol = plan.symbol,
                        name = plan.name,
                        entryPrice = stepPrice,
                        amountUsd = plan.stepUsd,
                        openedAt = now,
                        takeProfitPct = plan.takeProfitPct,
                        stopLossPct = plan.stopLossPct,
                        feePct = plan.feePct,
                        note = "پله ${index + 1} از ${plan.steps}"
                    )
                }
                messages += "${plan.name}: پله‌ی ${plan.filledSteps + 1} تا ${target} از ${plan.steps} خریداری شد."
                result = plan.copy(filledSteps = target, lastFillAt = now)
            }
            if (result.isActive && planShouldCancel(result, price)) {
                messages += "${plan.name}: قیمت از کف محدوده و حد ضرر هم پایین‌تر رفت؛ پله‌های باقی‌مانده لغو شد."
                result = result.copy(canceledAt = now, cancelReason = "عبور از حد ضرر محدوده")
            }
            result
        }
        if (newTrades.isNotEmpty()) write(context, all(context) + newTrades)
        if (messages.isNotEmpty()) writePlans(context, updated)
        return messages
    }

    /** ثبت خرید آزمایشی تازه (سفارش بازار) */
    fun buy(
        context: Context,
        coin: PumpScanner.PumpCoin,
        amountUsd: Double,
        takeProfitPct: Double?,
        stopLossPct: Double?,
        feePct: Double = DEFAULT_FEE_PCT,
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
            stopLossPct = stopLossPct?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 99.0),
            feePct = feePct.takeIf { it.isFinite() && it >= 0.0 }?.coerceAtMost(5.0) ?: DEFAULT_FEE_PCT
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
