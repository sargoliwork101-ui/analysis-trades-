package com.pulse.market.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pulse.market.data.AiReviewStore
import com.pulse.market.data.Ichimoku
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpOhlc
import com.pulse.market.data.TradingViewSymbols
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

    // نمادِ تریدینگ‌ویو پیش از نمایش با سرویسِ جست‌وجوی خودِ تریدینگ‌ویو تأیید می‌شود:
    //  • null       → هنوز در حال تشخیص (نمای «در حال آماده‌سازی»).
    //  • غیرِ null   → نمادِ آماده برای ویجت (EXCHANGE:SYMBOL یا SYMBOLUSDT).
    // اگر جفتِ USDT وجود نداشته باشد، خودکار به «نمودار داخلی» برمی‌گردیم و پیام می‌دهیم.
    var tvSymbol by remember(coinId) { mutableStateOf<String?>(null) }
    var tvUnavailable by remember(coinId) { mutableStateOf(false) }
    var fullscreen by remember(coinId) { mutableStateOf(false) }
    LaunchedEffect(coinId, symbol, advanced) {
        if (!advanced || !hasSymbol || tvSymbol != null) return@LaunchedEffect
        when (val r = TradingViewSymbols.resolve(symbol)) {
            is TradingViewSymbols.Outcome.Found -> tvSymbol = r.tvSymbol
            TradingViewSymbols.Outcome.NotFound -> { tvUnavailable = true; advanced = false }
            // خطای شبکه/تجزیه: خوش‌بینانه با حدسِ رایج ادامه می‌دهیم (مثل رفتار قبلی).
            TradingViewSymbols.Outcome.Unknown -> tvSymbol = tradingViewSymbol(symbol)
        }
    }

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
                        // کلیکِ دوباره روی «پیشرفته» بعد از بازگشتِ خودکار، دوباره تلاش می‌کند.
                        onClick = { tvUnavailable = false; advanced = true },
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
                val ready = tvSymbol
                if (ready == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "در حال آماده‌سازی نمودار حرفه‌ای…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // در حالت تمام‌صفحه، نمودارِ داخلِ کارت موقتاً بارگذاری نمی‌شود تا دو WebViewِ
                    // تریدینگ‌ویو هم‌زمان اجرا نشوند (حافظه/باتری). با بستنِ تمام‌صفحه دوباره می‌آید.
                    if (fullscreen) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(480.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "نمودار در حالت تمام‌صفحه باز است…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FullscreenChartDialog(
                            tvSymbol = ready,
                            dark = dark,
                            persian = persian,
                            onDismiss = { fullscreen = false }
                        )
                    } else {
                        TradingViewChart(
                            tvSymbol = ready,
                            dark = dark,
                            persian = persian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(480.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "با دو انگشت زوم کن، با کشیدن جابه‌جا کن، از نوار بالا تایم‌فریم و اندیکاتور را عوض کن. " +
                                        "برای بررسی بزرگ‌تر، «تمام‌صفحه» را بزن (چرخش افقی هم دارد).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { fullscreen = true }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تمام‌صفحه", fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                if (tvUnavailable) {
                    Hint("این کوین جفتِ USDT در تریدینگ‌ویو نداشت؛ «نمودار داخلی» نمایش داده می‌شود.")
                    Spacer(Modifier.height(8.dp))
                }
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
    tvSymbol: String,
    dark: Boolean,
    persian: Boolean,
    modifier: Modifier = Modifier
) {
    val html = remember(tvSymbol, dark, persian) { tradingViewHtml(tvSymbol, dark, persian) }
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
                    // صفحه‌ی ویجت روی HTTPS است؛ اجازه‌ی افتِ منابع به HTTP ساده داده نمی‌شود
                    // (هرچند برنامه برای منابع داده‌ی ایرانی cleartext را کلی مجاز می‌کند).
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    // تا وقتی کاربر روی نمودار می‌کشد، صفحه‌ی بالادستی (شیتِ اسکرول‌شونده)
                    // اسکرول را نگیرد؛ در پایانِ لمس، کنترل را پس می‌دهیم و performClick را
                    // صدا می‌زنیم تا دسترس‌پذیری/کلیک هم درست کار کند.
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) v.performClick()
                            }
                        }
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
            },
            // بدون این، WebView هنگام خروج از composition (بستن شیت یا تعویض تم/نماد که
            // WebView را از نو می‌سازد) آزاد نمی‌شد و به Context اکتیویتی نشت می‌کرد.
            onRelease = { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.setOnTouchListener(null)
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        )
    }
}

/**
 * نمایشِ نمودار حرفه‌ای در «تمام‌صفحه» — برای بررسی دقیق‌تر. یک دیالوگِ تمام‌صفحه که خودِ
 * صفحه‌ی دستگاه را می‌گیرد و دکمه‌ی «چرخش» دارد تا نمودار را افقی (لنداسکیپ) هم ببینی.
 *
 * چرخش با ست‌کردنِ requestedOrientation انجام می‌شود؛ چون MainActivity در منیفست
 * configChanges دارد، دستگاه بدون بازسازیِ اکتیویتی می‌چرخد و دیالوگ باز می‌ماند.
 */
@Composable
fun FullscreenChartDialog(
    tvSymbol: String,
    dark: Boolean,
    persian: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // جهتِ فعلیِ اکتیویتی پیش از تمام‌صفحه؛ هنگام بستن دقیقاً همین برگردانده می‌شود
    // (به‌جای فرضِ ثابتِ UNSPECIFIED) تا رفتارِ برنامه بعد از خروج عوض نشود.
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // ورود به تمام‌صفحه به‌صورت افقی (خواسته‌ی اصلی: دیدِ عریض‌تر)؛ با دکمه به عمودی هم می‌رود.
    var landscape by remember { mutableStateOf(true) }

    LaunchedEffect(landscape, activity) {
        activity?.requestedOrientation = if (landscape) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    // با بسته‌شدن دیالوگ، جهتِ صفحه به همان حالتِ قبل از تمام‌صفحه برمی‌گردد.
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = originalOrientation }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                TradingViewChart(
                    tvSymbol = tvSymbol,
                    dark = dark,
                    persian = persian,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(onClick = { landscape = !landscape }) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "چرخش افقی/عمودی")
                    }
                    FilledTonalIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن تمام‌صفحه")
                    }
                }
            }
        }
    }
}

/** یافتنِ Activityِ میزبان از روی Context (برای کنترلِ جهتِ صفحه). */
internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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
