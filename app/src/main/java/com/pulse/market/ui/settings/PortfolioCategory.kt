package com.pulse.market.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.CoinPrices
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            } else if (showNotice && ids.isNotEmpty()) {
                notice = "قیمت تازه گرفته نشد؛ اینترنت را بررسی کن (نتیجه‌ها با آخرین قیمت موجود است)."
            }
            trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
        } finally {
            busy = false
        }
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

        TradeSummaryCard(
            summary = summary,
            feesUsd = PaperTradeStore.totalFees(trades, prices),
            persian = persian
        )

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
                WalletTradeCard(
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
                WalletTradeCard(
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
