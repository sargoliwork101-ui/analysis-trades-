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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    symbol: String = "",
    sparkFallback: List<Double> = emptyList(),
    rising: Boolean = true,
    modifier: Modifier = Modifier
) {
    // «نمودار پیشرفته» = همان نمودار حرفه‌ای سایت‌ها (تریدینگ‌ویو) داخل خودِ برنامه، با همه‌ی
    // امکاناتش: زوم، جابه‌جایی، ده‌ها اندیکاتور، ابزار ترسیم، عوض‌کردن تایم‌فریم و نماد.
    // «نمودار داخلی» = نمودار شمعیِ خودِ برنامه که آفلاین و برای کوین‌های کم‌نام هم کار می‌کند.
    val hasSymbol = symbol.isNotBlank()
    var advanced by remember(coinId) { mutableStateOf(hasSymbol) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var range by remember(coinId) { mutableStateOf(PumpOhlc.Range.WEEK) }
    var candles by remember(coinId) { mutableStateOf<List<PumpOhlc.Candle>>(emptyList()) }
    var chartBusy by remember(coinId) { mutableStateOf(false) }
    // با هر بار زدن دکمه‌ی «به‌روزرسانی» این کلید بالا می‌رود و افکت دوباره اجرا می‌شود؛
    // فقط بارِ دستی کش را دور می‌زند (force) تا داده‌ی تازه از شبکه گرفته شود؛ عوض‌کردن بازه
    // همچنان از کش استفاده می‌کند (سهمیه‌ی رایگان محدود است).
    var reloadKey by remember(coinId) { mutableStateOf(0) }
    var forceReload by remember(coinId) { mutableStateOf(false) }
    LaunchedEffect(coinId, range, reloadKey, advanced) {
        if (advanced) return@LaunchedEffect   // در حالت پیشرفته داده از تریدینگ‌ویو می‌آید
        chartBusy = true
        val force = forceReload
        val loaded = PumpOhlc.load(coinId, range, force = force)
        // همه‌ی نمودارها شمعی‌اند: اگر endpointِ ohlc داده نداد، کندلِ *همان بازه* از
        // market_chart ساخته می‌شود؛ و اگر شبکه نبود، از سریِ کش‌شدهٔ اسپارک کندل می‌سازیم.
        candles = when {
            loaded.size >= 3 -> loaded
            else -> PumpOhlc.loadSynthetic(coinId, range, force = force).ifEmpty {
                PumpOhlc.candlesFromValues(sparkFallback)
            }
        }
        forceReload = false
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
            // ── انتخاب نوع نمودار ──
            if (hasSymbol) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = advanced,
                        onClick = { advanced = true },
                        label = { Text("نمودار پیشرفته", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = !advanced,
                        onClick = { advanced = false },
                        label = { Text("نمودار داخلی", fontSize = 11.sp) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (advanced && hasSymbol) {
                TradingViewChart(
                    symbol = symbol,
                    dark = dark,
                    persian = persian,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "نمودار حرفه‌ای تریدینگ‌ویو داخل برنامه: با دو انگشت زوم کن، با کشیدن جابه‌جا کن، از نوار بالا " +
                            "تایم‌فریم و اندیکاتور را عوض کن و از ذره‌بین، نماد را تغییر بده. اگر نماد پیدا نشد، به «نمودار داخلی» برگرد.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    for (option in PumpOhlc.Range.entries) {
                        FilterChip(
                            selected = range == option,
                            onClick = { range = option },
                            label = { Text(option.label, fontSize = 11.sp) }
                        )
                    }
                }
                IconButton(
                    onClick = { if (!chartBusy) { forceReload = true; reloadKey++ } },
                    enabled = !chartBusy
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "به‌روزرسانی نمودار",
                        tint = MaterialTheme.colorScheme.primary
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
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("داده‌ی نمودار برای این کوین در دسترس نبود (اینترنت را بررسی کن).")
                    OutlinedButton(
                        onClick = { if (!chartBusy) { forceReload = true; reloadKey++ } },
                        enabled = !chartBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تلاش دوباره برای گرفتن نمودار", fontSize = 12.sp)
                    }
                }
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
}

/**
 * نمودار حرفه‌ایِ تریدینگ‌ویو داخل خودِ برنامه (WebView). دقیقاً همان نموداری که سایت‌های
 * حرفه‌ای نشان می‌دهند: زوم، جابه‌جایی، اندیکاتورها، ابزار ترسیم، تغییر تایم‌فریم و نماد.
 * برای عملکرد به اینترنت نیاز دارد؛ آفلاین باید به «نمودار داخلی» برگشت.
 */
@Composable
fun TradingViewChart(
    symbol: String,
    dark: Boolean,
    persian: Boolean,
    modifier: Modifier = Modifier
) {
    val html = remember(symbol, dark, persian) { tradingViewHtml(tradingViewSymbol(symbol), dark, persian) }
    // با تغییر نماد/تم، WebView از نو ساخته و بارگذاری می‌شود.
    key(html) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    // تا وقتی کاربر روی نمودار می‌کشد، صفحه‌ی بالادستی اسکرول را نگیرد.
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    loadDataWithBaseURL(
                        "https://www.tradingview.com",
                        html,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            }
        )
    }
}

/** نمادِ تریدینگ‌ویو از روی نماد کوین (جفت USDT رایج‌ترین بازار است). */
internal fun tradingViewSymbol(symbol: String): String {
    val clean = symbol.trim().uppercase().filter { it.isLetterOrDigit() }.take(12)
    return if (clean.isEmpty()) "BTCUSDT" else "${clean}USDT"
}

/** HTMLِ ویجتِ «نمودار پیشرفتهٔ» تریدینگ‌ویو برای بارگذاری در WebView. */
internal fun tradingViewHtml(tvSymbol: String, dark: Boolean, persian: Boolean): String {
    val theme = if (dark) "dark" else "light"
    val locale = if (persian) "fa_IR" else "en"
    val bg = if (dark) "#0f1115" else "#ffffff"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>html,body{height:100%;margin:0;padding:0;background:$bg;overflow:hidden}#tv{height:100%;width:100%}</style>
        </head>
        <body>
        <div id="tv"></div>
        <script type="text/javascript" src="https://s3.tradingview.com/tv.js"></script>
        <script type="text/javascript">
        new TradingView.widget({
          "autosize": true,
          "symbol": "$tvSymbol",
          "interval": "60",
          "timezone": "Etc/UTC",
          "theme": "$theme",
          "style": "1",
          "locale": "$locale",
          "enable_publishing": false,
          "hide_side_toolbar": false,
          "allow_symbol_change": true,
          "studies": ["IchimokuCloud@tv-basicstudies"],
          "container_id": "tv"
        });
        </script>
        </body>
        </html>
    """.trimIndent()
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
