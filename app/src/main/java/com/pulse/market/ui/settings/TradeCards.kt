package com.pulse.market.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import java.util.Locale
import kotlin.math.abs

internal val ProfitGreen = Color(0xFF16A34A)
internal val LossRed = Color(0xFFDC2626)
private const val USD = "$"

/**
 * کارت خلاصه‌ی کیف — نتیجه‌ی کل به‌صورت درشت و بقیه‌ی آمار در چیپ‌های کوچک و خوانا.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TradeSummaryCard(
    summary: PaperTradeStore.Summary,
    feesUsd: Double,
    persian: Boolean
) {
    val total = summary.realizedUsd + summary.openUsd
    val accent = if (total >= 0.0) ProfitGreen else LossRed
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "نتیجه‌ی کل کیف",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    signedUsd(total, persian),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip("محقق‌شده", signedUsd(summary.realizedUsd, persian),
                    if (summary.realizedUsd >= 0.0) ProfitGreen else LossRed)
                StatChip("سود باز", signedUsd(summary.openUsd, persian),
                    if (summary.openUsd >= 0.0) ProfitGreen else LossRed)
                StatChip(
                    "برد / باخت",
                    "${Format.toPersianDigits("${summary.wins}")} / ${Format.toPersianDigits("${summary.losses}")}",
                    MaterialTheme.colorScheme.onSurface
                )
                StatChip(
                    "نرخ برد",
                    "${Format.price(summary.winRatePct, persian)}٪",
                    MaterialTheme.colorScheme.onSurface
                )
                StatChip(
                    "باز / بسته",
                    "${Format.toPersianDigits("${summary.openCount}")} / ${Format.toPersianDigits("${summary.closedCount}")}",
                    MaterialTheme.colorScheme.onSurface
                )
                StatChip(
                    "کارمزد کل",
                    "${Format.price(feesUsd, persian)} $USD",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/**
 * کارت یک معامله — نگاه اول: کوین + سود/زیان درشت؛ نگاه دوم: قیمت‌ها، مبلغ، و
 * مهم‌تر از همه «کِی خریدی / کِی فروختی». چیدمان دو‌ستونه تا شلوغ نشود.
 *
 * @param onSell اگر null باشد دکمه‌ی فروش نمایش داده نمی‌شود (معامله‌ی بسته).
 * @param onDelete اگر null باشد دکمه‌ی حذف نمایش داده نمی‌شود.
 */
@Composable
fun WalletTradeCard(
    trade: PaperTradeStore.Trade,
    price: Double?,
    persian: Boolean,
    onSell: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val pct = trade.profitPct(price)
    val usd = trade.profitUsd(price)
    val accent = when {
        pct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        pct >= 0.0 -> ProfitGreen
        else -> LossRed
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // نوار رنگی کناری = وضعیت سود/زیان در یک نگاه
            Spacer(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // سرصفحه: نام کوین + برچسب وضعیت + سود/زیان درشت
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${trade.name} (${trade.symbol.uppercase()})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (trade.isOpen) "معامله‌ی باز"
                            else "بسته — ${trade.closeReason?.label ?: "فروش"}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (pct == null) "—"
                            else "${if (pct >= 0.0) "▲ +" else "▼ −"}${twoDecimals(abs(pct), persian)}٪",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                        if (usd != null) {
                            Text(
                                signedUsd(usd, persian),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }

                // آمار کلیدی در دو ستون
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FactCell(
                        if (trade.isLadder) "میانگین ورود" else "قیمت خرید",
                        QuoteText.priceWithUnit(trade.entryPrice, USD, persian)
                    )
                    FactCell(
                        if (trade.isOpen) "قیمت فعلی" else "قیمت فروش",
                        QuoteText.priceWithUnit(price, USD, persian)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FactCell("مبلغ سرمایه", QuoteText.priceWithUnit(trade.amountUsd, USD, persian))
                    FactCell(
                        if (trade.isOpen) "ارزش فعلی" else "دریافتی فروش",
                        QuoteText.priceWithUnit(trade.exitValueUsd(price), USD, persian)
                    )
                }

                if (trade.isLadder) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FactCell(
                            "پله‌های پرشده",
                            "${Format.toPersianDigits("${trade.filledStepCount}")}/${Format.toPersianDigits("${trade.steps.size}")}"
                        )
                        FactCell(
                            "سرمایه (پرشده/کل)",
                            QuoteText.priceWithUnit(trade.amountUsd, USD, persian) + " / " +
                                    QuoteText.priceWithUnit(trade.plannedAmountUsd, USD, persian)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                // زمان‌ها — چیزی که کاربر می‌خواست: کِی خریدی، کِی فروختی
                MetaLine(Icons.Default.Schedule, "خرید", Format.dateTime(trade.openedAt, persian))
                trade.closedAt?.let {
                    MetaLine(Icons.Default.CheckCircle, "فروش", Format.dateTime(it, persian))
                }

                // سطوح تصمیم (اختیاری) — فشرده در یک خط
                val levels = buildString {
                    trade.takeProfitPrice?.let { append("حد سود ${QuoteText.priceWithUnit(it, USD, persian)}") }
                    trade.stopLossPrice?.let {
                        if (isNotEmpty()) append("  •  ")
                        append("حد ضرر ${QuoteText.priceWithUnit(it, USD, persian)}")
                    }
                    trade.breakEvenPrice?.let {
                        if (isNotEmpty()) append("  •  ")
                        append("سربه‌سر ${QuoteText.priceWithUnit(it, USD, persian)}")
                    }
                }
                if (levels.isNotBlank()) {
                    Text(levels, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "کارمزد ${Format.price(trade.feePct, persian)}٪ هر سمت • مجموع " +
                            QuoteText.priceWithUnit(trade.totalFeeUsd(price) ?: trade.buyFeeUsd, USD, persian),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (onSell != null || onDelete != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onSell != null) {
                            Button(onClick = onSell, modifier = Modifier.weight(1f)) {
                                Text("فروش با قیمت فعلی", fontSize = 11.sp)
                            }
                        }
                        if (onDelete != null) {
                            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حذف", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.FactCell(label: String, value: String) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MetaLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text("$label: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun twoDecimals(value: Double, persian: Boolean): String {
    val t = String.format(Locale.US, "%.2f", value)
    return if (persian) Format.toPersianDigits(t) else t
}

internal fun signedUsd(value: Double, persian: Boolean): String {
    val sign = if (value >= 0.0) "+" else "−"
    return "$sign${Format.price(abs(value), persian)} $USD"
}
