package com.pulse.market.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AiReviewStore
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpScanner
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText

/**
 * صفحه‌ی جزئیاتِ یک معامله/کوینِ کیف پول:
 *  • همه‌ی اطلاعات معامله (قیمت ورود، مبلغ، سود/زیان، حد سود/ضرر، تاریخ‌ها…)
 *  • نمودار کندلی با بازه‌ی انتخابی
 *  • داده‌های بازار (اگر آنلاین گرفته شده باشد)
 *  • تحلیل هوش مصنوعی: آخرین تحلیل + تاریخچه + گرفتن تحلیل تازه
 *
 * حتی آفلاین هم باز می‌شود: قیمت و سود/زیان با «آخرین مقدار موجود» نشان داده می‌شوند و
 * فقط برچسب «آپدیت نشده» می‌خورند.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletDetailSheet(
    trade: PaperTradeStore.Trade,
    price: Double?,
    priceStale: Boolean,
    priceAt: Long,
    persian: Boolean,
    aiEnabled: Boolean,
    aiReady: Boolean,
    aiBusy: Boolean,
    aiError: String?,
    history: List<AiReviewStore.Entry>,
    marketCoin: PumpScanner.PumpCoin?,
    onAiReview: () -> Unit,
    onSell: (() -> Unit)?,
    onDelete: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // برای معامله‌ی بسته، مرجع قیمت همان قیمتِ بسته‌شدن است.
    val reference = if (trade.isOpen) price else trade.closePrice
    val profitPct = trade.profitPct(reference)
    val profit = (profitPct ?: 0.0) >= 0.0
    val profitColor = if (profit) Color(0xFF16A34A) else Color(0xFFDC2626)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── هدر ──
            Column {
                Text(
                    trade.name.ifBlank { trade.symbol },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildString {
                        if (trade.symbol.isNotBlank()) append(trade.symbol.uppercase())
                        if (trade.coinId.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("شناسه: ${trade.coinId}")
                        }
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── قیمت فعلی + وضعیت به‌روزرسانی ──
            Text(
                QuoteText.priceWithUnit(reference, "$", persian),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (trade.isOpen) {
                val stamp = priceAt.takeIf { it > 0L }?.let { Format.dateTime(it, persian) }
                if (priceStale || price == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (stamp != null)
                                "⚠️ آپدیت نشده (آفلاین) — آخرین قیمتِ ثبت‌شده: $stamp"
                            else "⚠️ هنوز قیمت تازه‌ای گرفته نشده؛ اعداد بر اساس قیمت خرید است.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (stamp != null) {
                    Text(
                        "به‌روزرسانی: $stamp",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── نمودار ──
            CoinChartCard(
                coinId = trade.coinId,
                price = reference,
                persian = persian,
                sparkFallback = marketCoin?.spark ?: emptyList(),
                rising = (marketCoin?.change7d ?: 0.0) >= 0.0
            )

            // ── سود و زیان ──
            Section("سود و زیان معامله")
            Card(
                colors = CardDefaults.cardColors(containerColor = profitColor.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        (if (trade.isOpen) "سود/زیان فعلی: " else "نتیجه‌ی نهایی: ") +
                                PaperTradeStore.resultText(trade, reference, persian),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = profitColor
                    )
                    StatRow(
                        if (trade.isLadder) "میانگین ورود (پله‌های پرشده)" else "قیمت خرید",
                        QuoteText.priceWithUnit(trade.entryPrice, "$", persian)
                    )
                    StatRow(
                        if (trade.isLadder) "سرمایه‌ی به‌کاررفته" else "مبلغ",
                        QuoteText.priceWithUnit(trade.amountUsd, "$", persian)
                    )
                    trade.breakEvenPrice?.let {
                        StatRow("قیمت سر‌به‌سر (با کارمزد)", QuoteText.priceWithUnit(it, "$", persian))
                    }
                    trade.takeProfitPrice?.let {
                        StatRow("حد سود", QuoteText.priceWithUnit(it, "$", persian))
                    }
                    trade.stopLossPrice?.let {
                        StatRow("حد ضرر", QuoteText.priceWithUnit(it, "$", persian))
                    }
                    if (trade.isLadder) {
                        StatRow(
                            "خرید پله‌ای",
                            "${Format.toPersianDigits("${trade.filledStepCount}")} از " +
                                    "${Format.toPersianDigits("${trade.steps.size}")} پله پر شده"
                        )
                    }
                    StatRow("زمان خرید", Format.dateTime(trade.openedAt, persian))
                    if (!trade.isOpen) {
                        trade.closedAt?.let { StatRow("زمان فروش", Format.dateTime(it, persian)) }
                        trade.closeReason?.let { StatRow("دلیل بسته‌شدن", it.label) }
                    }
                }
            }

            // ── داده‌های بازار (اگر آنلاین بود) ──
            if (marketCoin != null) {
                Section("داده‌های بازار")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PumpChangeBadge("۱ ساعت", marketCoin.change1h, persian)
                    PumpChangeBadge("۱ روز", marketCoin.change24h, persian)
                    PumpChangeBadge("۱ هفته", marketCoin.change7d, persian)
                    PumpChangeBadge("۱ ماه", marketCoin.change30d, persian)
                }
                if (marketCoin.rank > 0) {
                    StatRow("رتبه‌ی بازار", Format.toPersianDigits("${marketCoin.rank}"))
                }
                StatRow("حجم ۲۴ ساعته", Format.volume(marketCoin.volume, persian))
                StatRow("ارزش بازار", Format.volume(marketCoin.marketCap, persian))
                StatRow("سقف ۲۴ ساعته", QuoteText.priceWithUnit(marketCoin.high24h, "$", persian))
                StatRow("کف ۲۴ ساعته", QuoteText.priceWithUnit(marketCoin.low24h, "$", persian))
                StatRow("بالاترین قیمت تاریخ (ATH)", QuoteText.priceWithUnit(marketCoin.ath, "$", persian))
                marketCoin.athChangePct?.let {
                    StatRow("فاصله تا ATH", Format.pct(it, persian).ifBlank { "—" })
                }
            } else if (trade.isOpen) {
                Hint("داده‌ی کاملِ بازار گرفته نشد؛ اینترنت را بررسی کن. نمودار و سود/زیان با آخرین قیمت موجود نشان داده می‌شوند.")
            }

            // ── تحلیل هوش مصنوعی ──
            Section("تحلیل هوش مصنوعی")
            if (!aiEnabled) {
                Hint("بررسی با AI خاموش است؛ از بخش «پامپ‌های کریپتو ← نظر دوم هوش مصنوعی» می‌توانی روشنش کنی.")
            } else {
                OutlinedButton(
                    onClick = onAiReview,
                    enabled = aiReady && !aiBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            aiBusy -> "در حال بررسی و جست‌وجوی خبر…"
                            history.isNotEmpty() -> "گرفتن تحلیل تازه با AI"
                            else -> "تحلیل با AI"
                        },
                        fontSize = 11.5.sp
                    )
                }
                if (!aiReady) {
                    Hint("برای تحلیل، آدرس API و نام مدل را در بخش «نظر دوم هوش مصنوعی» کامل کن.")
                }
                if (!aiError.isNullOrBlank()) {
                    Text(
                        "خطای AI: $aiError",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val latest = history.firstOrNull()
                if (latest != null) {
                    AiReviewCard(
                        review = latest.review,
                        at = latest.at,
                        persian = persian,
                        onOpenLink = onOpenLink
                    )
                    AiReviewHistorySection(
                        older = history.drop(1),
                        persian = persian,
                        onOpenLink = onOpenLink
                    )
                } else if (aiReady) {
                    Hint("هنوز تحلیلی برای این کوین ذخیره نشده؛ دکمه‌ی بالا را بزن تا اولین تحلیل گرفته شود.")
                }
            }

            // ── دکمه‌های عمل ──
            if (trade.isOpen && onSell != null) {
                Button(onClick = onSell, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("فروش آزمایشی با قیمت فعلی", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("حذف این معامله", fontSize = 11.5.sp)
            }

            Hint(
                "این بخش شبیه‌ساز است: هیچ سفارشی به هیچ صرافی فرستاده نمی‌شود. تحلیل AI هم فقط " +
                        "نظر دوم است، نه توصیه‌ی سرمایه‌گذاری."
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
