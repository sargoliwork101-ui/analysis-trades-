package com.pulse.market.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.Ichimoku
import com.pulse.market.data.NobitexMarkets
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpOhlc
import com.pulse.market.data.PumpScanner
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import kotlin.math.abs

/**
 * صفحه‌ی جزئیات یک کوین پامپ — همه‌ی چیزی که برای تصمیم لازم است در یک جا:
 * نمودار ۷ روزه، تغییرات همه‌ی بازه‌ها، حجم/ارزش بازار/گردش، فاصله تا ATH،
 * جای قیمت در دامنه‌ی ۲۴ ساعته، تفکیک امتیاز پامپ، پیشنهاد احتیاطی برنامه و
 * نظر دوم هوش مصنوعی همراه با خبرهای لینک‌دار.
 *
 * هیچ بخشی از این صفحه توصیه‌ی خرید نیست.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PumpDetailSheet(
    coin: PumpScanner.PumpCoin,
    persian: Boolean,
    scanAt: Long,
    previousMatches: Pair<Long, Set<String>>?,
    canAdd: Boolean,
    alreadyAdded: Boolean,
    aiEnabled: Boolean,
    aiReady: Boolean,
    aiBusy: Boolean,
    aiReview: PumpAiReviewer.Review?,
    aiReviewAt: Long?,
    aiError: String?,
    nobitex: NobitexMarkets.Result?,
    openTrade: PaperTradeStore.Trade?,
    onBuy: (Double, Double?, Double?, Double) -> Unit,
    onSell: () -> Unit,
    onAiReview: () -> Unit,
    onOpenLink: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val risk = riskColor(coin.risk)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        coin.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        buildString {
                            if (coin.rank > 0) append("رتبه ${Format.toPersianDigits("${coin.rank}")} بازار")
                            if (scanAt > 0L) {
                                if (isNotEmpty()) append(" • ")
                                append("داده‌ی ساعت ${Format.time(scanAt)}")
                            }
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Chip(coin.risk.label, risk)
            }

            Text(
                QuoteText.priceWithUnit(coin.price, "$", persian),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── نمودار کندلی با بازه‌ی انتخابی و زوم ──
            var range by remember(coin.id) { mutableStateOf(PumpOhlc.Range.WEEK) }
            var candles by remember(coin.id) { mutableStateOf<List<PumpOhlc.Candle>>(emptyList()) }
            var chartBusy by remember(coin.id) { mutableStateOf(false) }
            LaunchedEffect(coin.id, range) {
                chartBusy = true
                candles = PumpOhlc.load(coin.id, range)
                chartBusy = false
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
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
                    val ichimoku = remember(candles, coin.spark) {
                        Ichimoku.of(closes.ifEmpty { coin.spark })
                    }
                    when {
                        candles.size >= 3 -> CandleChart(
                            candles = candles,
                            ichimoku = ichimoku,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )
                        chartBusy -> Text(
                            "در حال گرفتن کندل‌ها…",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        coin.spark.size >= 3 -> PriceSparkline(
                            values = coin.spark,
                            rising = (coin.change7d ?: 0.0) >= 0.0,
                            ichimoku = ichimoku,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        )
                        else -> Hint("داده‌ی نمودار برای این کوین در دسترس نبود.")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ایچیموکو: ${Ichimoku.summary(coin.price, ichimoku)}",
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

            // ── تغییرات ──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PumpChangeBadge("۱ ساعت", coin.change1h, persian)
                PumpChangeBadge("۱ روز", coin.change24h, persian)
                PumpChangeBadge("۱ هفته", coin.change7d, persian)
                PumpChangeBadge("۱ ماه", coin.change30d, persian)
            }

            // ── مرحله‌ی حرکت ──
            Card(
                colors = CardDefaults.cardColors(containerColor = risk.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "مرحله‌ی حرکت: ${coin.stage.label}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        coin.stage.note,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── داده‌های کلیدی ──
            Section("داده‌های کلیدی")
            StatRow("حجم ۲۴ ساعته", Format.volume(coin.volume, persian))
            StatRow("ارزش بازار", Format.volume(coin.marketCap, persian))
            StatRow(
                "گردش حجم به ارزش بازار",
                "${Format.price(coin.turnover * 100.0, persian)}٪"
            )
            StatRow("سقف ۲۴ ساعته", QuoteText.priceWithUnit(coin.high24h, "$", persian))
            StatRow("کف ۲۴ ساعته", QuoteText.priceWithUnit(coin.low24h, "$", persian))
            StatRow("بالاترین قیمت تاریخ (ATH)", QuoteText.priceWithUnit(coin.ath, "$", persian))
            coin.athChangePct?.let {
                StatRow("فاصله تا ATH", Format.pct(it, persian).ifBlank { "—" })
            }
            StatRow(
                "نوبیتکس",
                nobitex?.label ?: "در حال بررسی…"
            )
            coin.circulatingSupply?.let {
                StatRow("عرضه در گردش", Format.volume(it, persian))
            }
            coin.totalSupply?.let {
                StatRow("کل عرضه", Format.volume(it, persian))
            }

            coin.rangePosition24h?.let { position ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "جای قیمت در دامنه‌ی ۲۴ ساعته: " +
                                "${Format.price(position * 100.0, persian)}٪",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LinearProgressIndicator(
                        progress = { position.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                    Text(
                        if (position >= 0.85) "قیمت نزدیک سقف ۲۴ ساعته است؛ ورود در این ناحیه ریسک اصلاح دارد."
                        else "۰٪ یعنی کف و ۱۰۰٪ یعنی سقف ۲۴ ساعت اخیر.",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (coin.thinMarket) {
                WarnCard(
                    "بازار کم‌عمق: ارزش بازار کوچک است و حجم نسبت به آن بسیار بالاست؛ " +
                            "قیمت با سفارش‌های نه‌چندان بزرگ هم می‌تواند شدید جابه‌جا شود."
                )
            }

            // ── تفکیک امتیاز ──
            Section("چرا این کوین در فهرست است؟")
            val parts = coin.scoreParts
            ScoreBar("رشد ۲۴ ساعته", parts.day, parts.total, persian)
            ScoreBar("شتاب ۱ ساعته (وزن ۲)", parts.hour, parts.total, persian)
            ScoreBar("ورود حجم (وزن ۵۰)", parts.flow, parts.total, persian)
            val band = coin.scoreBand
            Text(
                "امتیاز کل: ${Format.price(coin.score, persian)} — ${band.label}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when (band) {
                    PumpScanner.ScoreBand.CALM -> MaterialTheme.colorScheme.onSurfaceVariant
                    PumpScanner.ScoreBand.MODERATE -> Color(0xFF16A34A)
                    PumpScanner.ScoreBand.STRONG -> Color(0xFFF59E0B)
                    PumpScanner.ScoreBand.OVERHEATED -> Color(0xFFDC2626)
                }
            )
            Hint(band.meaning)
            Hint(
                "فرمول: رشد ۲۴ ساعته + ۲× رشد ۱ ساعته + ۵۰× گردش حجم. عدد بالاتر یعنی حرکت شدیدتر، " +
                        "نه فرصت بهتر؛ بهترین محدوده برای «زیر نظر گرفتن» معمولاً ۱۵ تا ۳۵ است و بالای ۷۰ " +
                        "بیشتر نشانه‌ی اشباع و ریسک برگشت است."
            )

            // ── تاریخچه‌ی کوتاه ──
            previousMatches?.let { (at, ids) ->
                Hint(
                    if (coin.id in ids)
                        "در اسکن قبلی (ساعت ${Format.time(at)}) هم بالای آستانه بود؛ یعنی حرکت ادامه‌دار است."
                    else "در اسکن قبلی (ساعت ${Format.time(at)}) در فهرست نبود؛ این حرکت تازه شروع شده است."
                )
            }

            // ── پیشنهاد احتیاطی برنامه ──
            Section("نتیجه‌ی احتیاطی برنامه")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        coin.advice.recommendation.label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = risk
                    )
                    Text(
                        coin.advice.reason,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── نظر دوم AI ──
            Section("نظر دوم هوش مصنوعی")
            if (!aiEnabled) {
                Hint("بررسی با AI خاموش است؛ از بالای همین صفحه می‌توانی روشنش کنی.")
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
                            aiReview != null -> "بررسی دوباره با AI"
                            else -> "بررسی با AI"
                        },
                        fontSize = 11.5.sp
                    )
                }
                if (!aiReady) {
                    Hint("برای بررسی، آدرس API و نام مدل را در بخش «نظر دوم هوش مصنوعی» کامل کن.")
                }
                if (!aiError.isNullOrBlank()) {
                    Text(
                        "خطای AI: $aiError",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                aiReview?.let { review ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
                            val at = aiReviewAt?.takeIf { it > 0L }?.let { " • ساعت ${Format.time(it)}" }.orEmpty()
                            Text(
                                "وضعیت: ${review.verdict}$confidence$at",
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
                            Text(
                                "دلیل AI: ${review.reason}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
            }

            // ── معامله‌ی آزمایشی ──
            Section("خرید و فروش آزمایشی (شبیه‌ساز)")
            if (openTrade != null) {
                val pnl = PaperTradeStore.resultText(openTrade, coin.price, persian)
                val profit = (openTrade.profitPct(coin.price) ?: 0.0) >= 0.0
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = (if (profit) Color(0xFF16A34A) else Color(0xFFDC2626))
                            .copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "سود/زیان فعلی: $pnl",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profit) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                        StatRow("قیمت خرید", QuoteText.priceWithUnit(openTrade.entryPrice, "$", persian))
                        StatRow("مبلغ", QuoteText.priceWithUnit(openTrade.amountUsd, "$", persian))
                        StatRow(
                            "کارمزد رفت و برگشت",
                            QuoteText.priceWithUnit(openTrade.totalFeeUsd(coin.price), "$", persian) +
                                    " (${Format.price(openTrade.feePct, persian)}٪ هر سمت)"
                        )
                        openTrade.breakEvenPrice?.let {
                            StatRow("قیمت سر به سر (با کارمزد)", QuoteText.priceWithUnit(it, "$", persian))
                        }
                        openTrade.rawChangePct(coin.price)?.let {
                            StatRow("تغییر خام قیمت (بدون کارمزد)", "${Format.price(it, persian)}٪")
                        }
                        openTrade.takeProfitPrice?.let {
                            StatRow("فروش خودکار در سود", QuoteText.priceWithUnit(it, "$", persian))
                        }
                        openTrade.stopLossPrice?.let {
                            StatRow("فروش خودکار در ضرر", QuoteText.priceWithUnit(it, "$", persian))
                        }
                        Text(
                            "زمان خرید: ${Format.dateTime(openTrade.openedAt, persian)}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onSell() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("فروش آزمایشی با قیمت فعلی", fontSize = 11.5.sp) }
                    }
                }
            } else {
                var amount by remember(coin.id) { mutableStateOf("100") }
                var takeProfit by remember(coin.id) { mutableStateOf("10") }
                var stopLoss by remember(coin.id) { mutableStateOf("5") }
                var fee by remember(coin.id) {
                    mutableStateOf(PaperTradeStore.DEFAULT_FEE_PCT.toString())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.take(9) },
                        label = { Text("مبلغ (دلار)", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = takeProfit,
                        onValueChange = { takeProfit = it.take(6) },
                        label = { Text("حد سود ٪", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stopLoss,
                        onValueChange = { stopLoss = it.take(6) },
                        label = { Text("حد ضرر ٪", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it.take(5) },
                    label = { Text("کارمزد هر سمت (٪)", fontSize = 10.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onBuy(
                            amount.replace(',', '.').toDoubleOrNull() ?: 0.0,
                            takeProfit.replace(',', '.').toDoubleOrNull(),
                            stopLoss.replace(',', '.').toDoubleOrNull(),
                            fee.replace(',', '.').toDoubleOrNull() ?: PaperTradeStore.DEFAULT_FEE_PCT
                        )
                    },
                    enabled = (coin.price ?: 0.0) > 0.0 &&
                            (amount.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ثبت خرید آزمایشی با قیمت فعلی", fontSize = 11.5.sp) }
                Hint(
                    "هیچ سفارشی به هیچ صرافی نمی‌رود؛ فقط روی همین گوشی ثبت می‌شود. با هر اسکن تازه، " +
                            "اگر قیمت به حد سود یا حد ضرر برسد، معامله خودکار بسته و نتیجه ثبت می‌شود. " +
                            "کارمزد در هر دو سمت خرید و فروش از سود کم می‌شود (پیش‌فرض ۰٫۲٪ مثل نوبیتکس)."
                )
            }

            // ── اقدام‌ها ──
            Section("اقدام")
            if (alreadyAdded) {
                Text(
                    "✓ این کوین در ویجت هست.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = { onAdd(); onDismiss() },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (canAdd) "افزودن به ویجت" else "ویجت پر است", fontSize = 11.5.sp)
                }
            }
            OutlinedButton(
                onClick = { onOpenLink(tradingViewUrl(coin.symbol)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("باز کردن نمودار در TradingView", fontSize = 11.5.sp)
            }
            TextButton(
                onClick = { onOpenLink("https://www.coingecko.com/en/coins/${coin.id}") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("صفحه‌ی CoinGecko (داده‌ی بنیادی و لینک‌ها)", fontSize = 10.5.sp) }

            WarnCard(
                "هیچ‌کدام از داده‌ها و پیام‌های این صفحه توصیه‌ی مالی یا سیگنال خرید و فروش نیست."
            )
        }
    }
}

/** یک فیلد تحلیل AI؛ اگر مدل آن را پر نکرده باشد اصلاً نمایش داده نمی‌شود. */
@Composable
private fun AiField(label: String, value: String) {
    if (value.isBlank() || value.trim() == "نامشخص") return
    Text(
        "$label: $value",
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 1.dp)
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
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
            value.ifBlank { "—" },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 10.5.sp, color = color)
    }
}

@Composable
private fun WarnCard(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** سهم یک جزء از امتیاز پامپ؛ سهم منفی هم با رنگ قرمز دیده می‌شود. */
@Composable
private fun ScoreBar(label: String, value: Double, total: Double, persian: Boolean) {
    val safeTotal = abs(total).takeIf { it > 0.0001 } ?: 1.0
    val ratio = (abs(value) / safeTotal).coerceIn(0.0, 1.0).toFloat()
    val color = if (value < 0.0) Color(0xFFDC2626) else Color(0xFF16A34A)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Format.price(value, persian),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
        )
    }
}

/**
 * نمودار قیمت + ابر ایچیموکو.
 * مقیاس عمودی روی همه‌ی سری‌ها (قیمت، تنکان، کیجون و ابر) حساب می‌شود تا هیچ خطی
 * بیرون کادر نیفتد؛ بخش جلوتر از آخرین قیمت، پیش‌بینیِ ابر است.
 */
@Composable
private fun PriceSparkline(
    values: List<Double>,
    rising: Boolean,
    ichimoku: Ichimoku.Series?,
    modifier: Modifier
) {
    val priceColor = if (rising) Color(0xFF16A34A) else Color(0xFFDC2626)
    val tenkanColor = Color(0xFF3B82F6)
    val kijunColor = Color(0xFFF59E0B)
    val bullCloud = Color(0xFF22C55E)
    val bearCloud = Color(0xFFF43F5E)
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val spanA = ichimoku?.spanA.orEmpty()
        val spanB = ichimoku?.spanB.orEmpty()
        val total = maxOf(values.size, spanA.size, spanB.size)
        val all = buildList {
            addAll(values)
            addAll(spanA.filterNotNull())
            addAll(spanB.filterNotNull())
            ichimoku?.tenkan?.let { addAll(it.filterNotNull()) }
            ichimoku?.kijun?.let { addAll(it.filterNotNull()) }
        }
        val min = all.minOrNull() ?: return@Canvas
        val max = all.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = size.height * 0.08f
        val usable = size.height - pad * 2f
        val dx = size.width / (total - 1).coerceAtLeast(1).toFloat()

        fun yOf(value: Double): Float = pad + (usable - (((value - min) / range).toFloat() * usable))
        fun xOf(index: Int): Float = index * dx

        // ابر: بین اسپن A و B پر می‌شود؛ رنگ سبز یعنی A بالای B (ابر صعودی).
        var i = 0
        while (i < total - 1) {
            val a1 = spanA.getOrNull(i)
            val b1 = spanB.getOrNull(i)
            val a2 = spanA.getOrNull(i + 1)
            val b2 = spanB.getOrNull(i + 1)
            if (a1 != null && b1 != null && a2 != null && b2 != null) {
                val path = Path().apply {
                    moveTo(xOf(i), yOf(a1))
                    lineTo(xOf(i + 1), yOf(a2))
                    lineTo(xOf(i + 1), yOf(b2))
                    lineTo(xOf(i), yOf(b1))
                    close()
                }
                val bullish = (a1 + a2) >= (b1 + b2)
                drawPath(path, color = (if (bullish) bullCloud else bearCloud).copy(alpha = 0.22f))
            }
            i++
        }

        fun drawSeries(series: List<Double?>, color: Color, width: Float) {
            var previous: Offset? = null
            for ((index, value) in series.withIndex()) {
                if (value == null) {
                    previous = null
                    continue
                }
                val point = Offset(xOf(index), yOf(value))
                previous?.let { start ->
                    drawLine(color, start, point, strokeWidth = width, cap = StrokeCap.Round)
                }
                previous = point
            }
        }

        ichimoku?.let {
            drawSeries(it.tenkan, tenkanColor, 2.5f)
            drawSeries(it.kijun, kijunColor, 2.5f)
        }
        drawSeries(values.map { it }, priceColor, 3.5f)
    }
}

/** آدرس نمودار همین نماد در TradingView (جفت USDT رایج‌ترین بازار است). */
internal fun tradingViewUrl(symbol: String): String {
    val clean = symbol.trim().uppercase().filter { it.isLetterOrDigit() }.take(12)
    return if (clean.isEmpty()) "https://www.tradingview.com/markets/cryptocurrencies/"
    else "https://www.tradingview.com/chart/?symbol=${clean}USDT"
}

/**
 * نمودار شمعی با زوم و جابه‌جایی.
 * زوم با دو انگشت تعداد کندل‌های دیده‌شده را کم/زیاد می‌کند و کشیدن افقی،
 * پنجره‌ی دید را روی تاریخ جابه‌جا می‌کند. خطوط ایچیموکو هم روی همان پنجره رسم می‌شوند.
 */
@Composable
private fun CandleChart(
    candles: List<PumpOhlc.Candle>,
    ichimoku: Ichimoku.Series?,
    modifier: Modifier
) {
    val upColor = Color(0xFF16A34A)
    val downColor = Color(0xFFDC2626)
    val tenkanColor = Color(0xFF3B82F6)
    val kijunColor = Color(0xFFF59E0B)
    val bullCloud = Color(0xFF22C55E)
    val bearCloud = Color(0xFFF43F5E)

    var zoom by remember(candles) { mutableStateOf(1f) }
    var offset by remember(candles) { mutableStateOf(0f) }
    val total = candles.size
    val visibleCount = (total / zoom).toInt().coerceIn(6, total.coerceAtLeast(6))
    val maxStart = (total - visibleCount).coerceAtLeast(0)
    val start = offset.toInt().coerceIn(0, maxStart)
    val window = candles.subList(start, (start + visibleCount).coerceAtMost(total))

    Canvas(
        modifier = modifier.pointerInput(candles) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                val step = (size.width.toFloat() / visibleCount.coerceAtLeast(1)).coerceAtLeast(1f)
                offset = (offset - pan.x / step).coerceIn(0f, maxStart.toFloat())
            }
        }
    ) {
        if (window.size < 2) return@Canvas
        val spanA = ichimoku?.spanA.orEmpty()
        val spanB = ichimoku?.spanB.orEmpty()
        val tenkan = ichimoku?.tenkan.orEmpty()
        val kijun = ichimoku?.kijun.orEmpty()

        fun windowValues(series: List<Double?>): List<Double?> =
            (start until start + window.size).map { series.getOrNull(it) }

        val wSpanA = windowValues(spanA)
        val wSpanB = windowValues(spanB)
        val wTenkan = windowValues(tenkan)
        val wKijun = windowValues(kijun)

        val values = buildList {
            for (candle in window) {
                add(candle.high)
                add(candle.low)
            }
            addAll(wSpanA.filterNotNull())
            addAll(wSpanB.filterNotNull())
            addAll(wTenkan.filterNotNull())
            addAll(wKijun.filterNotNull())
        }
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val priceRange = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = size.height * 0.06f
        val usable = size.height - pad * 2f
        val slot = size.width / window.size.toFloat()
        val bodyWidth = (slot * 0.62f).coerceAtLeast(1.2f)

        fun yOf(value: Double): Float = pad + (usable - (((value - min) / priceRange).toFloat() * usable))
        fun xOf(index: Int): Float = slot * (index + 0.5f)

        // ابر ایچیموکو زیر کندل‌ها
        for (i in 0 until window.size - 1) {
            val a1 = wSpanA.getOrNull(i)
            val b1 = wSpanB.getOrNull(i)
            val a2 = wSpanA.getOrNull(i + 1)
            val b2 = wSpanB.getOrNull(i + 1)
            if (a1 != null && b1 != null && a2 != null && b2 != null) {
                val path = Path().apply {
                    moveTo(xOf(i), yOf(a1))
                    lineTo(xOf(i + 1), yOf(a2))
                    lineTo(xOf(i + 1), yOf(b2))
                    lineTo(xOf(i), yOf(b1))
                    close()
                }
                val bullish = (a1 + a2) >= (b1 + b2)
                drawPath(path, color = (if (bullish) bullCloud else bearCloud).copy(alpha = 0.20f))
            }
        }

        // کندل‌ها
        for ((index, candle) in window.withIndex()) {
            val color = if (candle.bullish) upColor else downColor
            val x = xOf(index)
            drawLine(
                color = color,
                start = Offset(x, yOf(candle.high)),
                end = Offset(x, yOf(candle.low)),
                strokeWidth = 1.6f
            )
            val top = yOf(maxOf(candle.open, candle.close))
            val bottom = yOf(minOf(candle.open, candle.close))
            drawRect(
                color = color,
                topLeft = Offset(x - bodyWidth / 2f, top),
                size = Size(bodyWidth, (bottom - top).coerceAtLeast(1.2f))
            )
        }

        fun drawSeries(series: List<Double?>, color: Color, width: Float) {
            var previous: Offset? = null
            for ((index, value) in series.withIndex()) {
                if (value == null) {
                    previous = null
                    continue
                }
                val point = Offset(xOf(index), yOf(value))
                previous?.let { drawLine(color, it, point, strokeWidth = width, cap = StrokeCap.Round) }
                previous = point
            }
        }
        drawSeries(wTenkan, tenkanColor, 2.2f)
        drawSeries(wKijun, kijunColor, 2.2f)
    }
}
