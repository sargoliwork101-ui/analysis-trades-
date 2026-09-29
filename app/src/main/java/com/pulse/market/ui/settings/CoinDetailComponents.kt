package com.pulse.market.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pulse.market.data.AiReviewStore
import com.pulse.market.data.Ichimoku
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpOhlc
import com.pulse.market.ui.Format

/**
 * اجزای مشترکِ صفحه‌های جزئیاتِ کوین (کیف پول و پامپ):
 *  • نمودار کندلی با بازه‌ی انتخابی + ایچیموکو
 *  • کارت نمایشِ یک تحلیل هوش مصنوعی
 *  • بخش تاریخچه‌ی تحلیل‌ها
 *
 * این‌ها یک‌بار نوشته می‌شوند تا هم بخش «پامپ» و هم «کیف پول» از یک ظاهر استفاده کنند.
 */

/** نمودار کندلی برای یک کوین با شناسه‌ی CoinGecko؛ خودش کندل‌ها را می‌گیرد و کش می‌کند. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CoinChartCard(
    coinId: String,
    price: Double?,
    persian: Boolean,
    sparkFallback: List<Double> = emptyList(),
    rising: Boolean = true,
    modifier: Modifier = Modifier
) {
    var range by remember(coinId) { mutableStateOf(PumpOhlc.Range.WEEK) }
    var candles by remember(coinId) { mutableStateOf<List<PumpOhlc.Candle>>(emptyList()) }
    var chartBusy by remember(coinId) { mutableStateOf(false) }
    LaunchedEffect(coinId, range) {
        chartBusy = true
        val loaded = PumpOhlc.load(coinId, range)
        // همه‌ی نمودارها شمعی‌اند: اگر endpointِ ohlc داده نداد، کندلِ *همان بازه* از
        // market_chart ساخته می‌شود؛ و اگر شبکه نبود، از سریِ کش‌شدهٔ اسپارک کندل می‌سازیم.
        candles = when {
            loaded.size >= 3 -> loaded
            else -> PumpOhlc.loadSynthetic(coinId, range).ifEmpty {
                PumpOhlc.candlesFromValues(sparkFallback)
            }
        }
        chartBusy = false
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (option in PumpOhlc.Range.entries) {
                    FilterChip(
                        selected = range == option,
                        onClick = { range = option },
                        label = { Text(option.label, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val closes = remember(candles) { PumpOhlc.closes(candles) }
            val ichimoku = remember(closes) { Ichimoku.of(closes) }
            when {
                candles.size >= 3 -> CandleChart(
                    candles = candles,
                    ichimoku = ichimoku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )
                chartBusy -> Text(
                    "در حال گرفتن نمودار…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Hint("داده‌ی نمودار برای این کوین در دسترس نبود (اینترنت را بررسی کن).")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "ایچیموکو: ${Ichimoku.summary(price, ichimoku)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${range.candleHint} • با دو انگشت زوم کن و با کشیدن، نمودار را جابه‌جا کن. " +
                        "خط آبی تنکان، خط نارنجی کیجون و ناحیه‌ی رنگی ابر ایچیموکو است.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** کارت نمایشِ یک تحلیلِ هوش مصنوعی. */
@Composable
fun AiReviewCard(
    review: PumpAiReviewer.Review,
    at: Long?,
    persian: Boolean,
    onOpenLink: (String) -> Unit,
    highlight: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
                .copy(alpha = if (highlight) 0.45f else 0.22f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "نظر AI: ${review.recommendation}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val confidence = review.confidence?.let {
                val n = if (persian) Format.toPersianDigits(it.toString()) else it.toString()
                " • اطمینان $n٪"
            }.orEmpty()
            val time = at?.takeIf { it > 0L }?.let { " • ${Format.dateTime(it, persian)}" }.orEmpty()
            Text(
                "وضعیت: ${review.verdict}$confidence$time",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (review.summary.isNotBlank()) {
                Text(
                    review.summary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (review.action.isNotBlank()) {
                Text(
                    "کار پیشنهادی: ${review.action}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            AiField("محدوده‌ی ورود", review.entry)
            AiField("حد ضرر", review.stopLoss)
            AiField("هدف‌ها", review.targets)
            AiField("افق زمانی", review.timeframe)
            AiField("باطل‌کننده‌ی سناریو", review.invalidation)
            AiField("تحلیل تکنیکال", review.technical)
            AiField("پشتوانه و پروژه", review.project)
            AiField("محرک‌های خبری", review.catalysts)
            AiField("ریسک‌ها", review.risks)
            if (review.reason.isNotBlank()) {
                Text(
                    "دلیل AI: ${review.reason}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (review.news.isEmpty()) {
                Text(
                    if (review.providerSearchRequested)
                        "خبر مرتبطِ دارای لینک از پاسخ سرویس دریافت نشد."
                    else "جست‌وجوی خبر درخواست نشده بود.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "خبرهای مرتبط گزارش‌شده توسط سرویس:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                for (news in review.news) {
                    TextButton(onClick = { onOpenLink(news.url) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(news.title, fontSize = 11.sp)
                            val meta = listOf(news.host, news.source, news.publishedAt)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" • ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (news.relation.isNotBlank()) {
                                Text(
                                    news.relation,
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** فهرست تاریخچه‌ی تحلیل‌های قبلی (بدون تازه‌ترین که جداگانه بالای صفحه است). */
@Composable
fun AiReviewHistorySection(
    older: List<AiReviewStore.Entry>,
    persian: Boolean,
    onOpenLink: (String) -> Unit
) {
    if (older.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val count = if (persian) Format.toPersianDigits(older.size.toString()) else older.size.toString()
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (expanded) "بستن تاریخچه‌ی تحلیل‌ها" else "نمایش $count تحلیل قبلی",
            fontSize = 11.5.sp
        )
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (entry in older) {
                AiReviewCard(
                    review = entry.review,
                    at = entry.at,
                    persian = persian,
                    onOpenLink = onOpenLink,
                    highlight = false
                )
            }
        }
    }
}

@Composable
private fun AiField(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label:",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
