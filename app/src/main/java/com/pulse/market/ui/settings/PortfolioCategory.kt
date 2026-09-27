package com.pulse.market.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.CoinPrices
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PROFIT = Color(0xFF16A34A)
private val LOSS = Color(0xFFDC2626)

/**
 * تب «معامله‌های من» — هر کوینی که خرید آزمایشی برایش ثبت کرده‌ای اینجا می‌ماند.
 *
 * قیمت‌ها مستقل از اسکن پامپ و مستقیم برای همین کوین‌ها گرفته می‌شوند، پس حتی اگر
 * کوین از فهرست پامپ خارج شده باشد، نتیجه‌ی خرید تو قابل پیگیری است.
 */
@Composable
fun PortfolioCategory(persian: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trades by remember { mutableStateOf<List<PaperTradeStore.Trade>>(emptyList()) }
    var prices by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun refresh(showNotice: Boolean) {
        busy = true
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
        } else if (showNotice && ids.isNotEmpty()) {
            notice = "قیمت تازه گرفته نشد؛ اینترنت را بررسی کن (نتیجه‌ها با آخرین قیمت موجود است)."
        }
        trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
        busy = false
    }

    LaunchedEffect(Unit) { refresh(showNotice = false) }

    val open = trades.filter { it.isOpen }
    val closed = trades.filterNot { it.isOpen }
    val summary = PaperTradeStore.summarize(trades, prices)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            "معامله‌های من",
            "خرید و فروش آزمایشی؛ بدون پول واقعی و فقط روی همین گوشی ذخیره می‌شود."
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val total = summary.realizedUsd + summary.openUsd
                Text(
                    "نتیجه‌ی کل: ${signed(total, persian)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (total >= 0.0) PROFIT else LOSS
                )
                Text(
                    "سود محقق‌شده: ${signed(summary.realizedUsd, persian)} • " +
                            "سود معامله‌های باز: ${signed(summary.openUsd, persian)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "باز: ${Format.toPersianDigits("${summary.openCount}")} • " +
                            "بسته: ${Format.toPersianDigits("${summary.closedCount}")} • " +
                            "برد ${Format.toPersianDigits("${summary.wins}")} / " +
                            "باخت ${Format.toPersianDigits("${summary.losses}")} • " +
                            "نرخ برد ${Format.price(summary.winRatePct, persian)}٪",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "سرمایه‌ی درگیر: ${QuoteText.priceWithUnit(summary.investedOpenUsd, "$", persian)} • " +
                            "کارمزد پرداخت‌شده: ${Format.price(PaperTradeStore.totalFees(trades, prices), persian)} دلار",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        }

        if (open.isNotEmpty()) {
            SectionHeader("معامله‌های باز", "با رسیدن قیمت به حد سود یا ضرر، خودکار بسته می‌شوند.")
            for (trade in open) {
                TradeCard(
                    trade = trade,
                    price = prices[trade.coinId],
                    persian = persian,
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
            for (trade in closed) {
                TradeCard(
                    trade = trade,
                    price = trade.closePrice,
                    persian = persian,
                    onSell = null,
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { PaperTradeStore.remove(context, trade.id) }
                            refresh(showNotice = false)
                        }
                    }
                )
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
}

/** کارت یک معامله با همه‌ی جزئیات تصمیم */
@Composable
private fun TradeCard(
    trade: PaperTradeStore.Trade,
    price: Double?,
    persian: Boolean,
    onSell: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val pct = trade.profitPct(price)
    val positive = (pct ?: 0.0) >= 0.0
    val accent = if (pct == null) MaterialTheme.colorScheme.onSurfaceVariant else if (positive) PROFIT else LOSS
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${trade.name} (${trade.symbol})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    PaperTradeStore.resultText(trade, price, persian),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            DetailLine("قیمت خرید", QuoteText.priceWithUnit(trade.entryPrice, "$", persian))
            DetailLine(
                if (trade.isOpen) "قیمت فعلی" else "قیمت فروش",
                QuoteText.priceWithUnit(price, "$", persian)
            )
            DetailLine("مبلغ سرمایه", QuoteText.priceWithUnit(trade.amountUsd, "$", persian))
            DetailLine(
                "کارمزد (${Format.price(trade.feePct, persian)}٪ هر سمت)",
                QuoteText.priceWithUnit(trade.totalFeeUsd(price) ?: trade.buyFeeUsd, "$", persian)
            )
            trade.breakEvenPrice?.let {
                DetailLine("سر به سر با کارمزد", QuoteText.priceWithUnit(it, "$", persian))
            }
            trade.takeProfitPrice?.let {
                DetailLine("حد سود", QuoteText.priceWithUnit(it, "$", persian))
            }
            trade.stopLossPrice?.let {
                DetailLine("حد ضرر", QuoteText.priceWithUnit(it, "$", persian))
            }
            trade.rawChangePct(price)?.let {
                DetailLine("تغییر خام قیمت", "${Format.price(it, persian)}٪")
            }
            DetailLine("زمان خرید", Format.dateTime(trade.openedAt, persian))
            trade.closedAt?.let { DetailLine("زمان فروش", Format.dateTime(it, persian)) }
            trade.closeReason?.let { DetailLine("دلیل بسته‌شدن", it.label) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSell != null) {
                    Button(onClick = onSell, modifier = Modifier.weight(1f)) {
                        Text("فروش با قیمت فعلی", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun signed(value: Double, persian: Boolean): String {
    val sign = if (value >= 0.0) "+" else "−"
    return "$sign${Format.price(kotlin.math.abs(value), persian)} دلار"
}
