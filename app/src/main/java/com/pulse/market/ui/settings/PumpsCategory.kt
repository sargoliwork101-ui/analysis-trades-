package com.pulse.market.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.MAX_SYMBOLS
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import com.pulse.market.data.NobitexMarkets
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpAlertEngine
import com.pulse.market.data.PumpScanner
import com.pulse.market.data.PumpSortPeriod
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * بخش «پامپ‌های کریپتو» — دو کار را با هم می‌کند:
 *
 * ۱) **آموزش**: پامپ چیست، چطور شکل می‌گیرد و چطور می‌شود در دامش نیفتاد.
 * ۲) **ابزار**: با اسکن CoinGecko، کوین‌هایی که همین حالا رشد شارپ + جهش حجم
 *    دارند را نشان می‌دهد تا کاربر *ببیند* پامپ زنده چه شکلی است (و اگر خواست،
 *    همان کوین را به ویجت اضافه کند و رفتارش را زیر نظر بگیرد).
 *
 * این بخش عمداً «سیگنال خرید» نیست؛ متن‌های هشدار بخشی از خودِ قابلیت‌اند.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PumpsCategory(
    cfg: WidgetConfig,
    alertOwnerKey: String,
    onChange: (WidgetConfig) -> Unit,
    onAlertToggle: (Boolean) -> Unit,
    onAddSymbol: (SymbolDef) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scan by remember { mutableStateOf<PumpScanner.PumpScan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var aiConfig by remember { mutableStateOf(PumpAiConfig()) }
    var aiBusyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var aiReviews by remember { mutableStateOf<Map<String, PumpAiReviewer.Review>>(emptyMap()) }
    var aiErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var aiStorageError by remember { mutableStateOf(false) }
    var aiTestBusy by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<PumpAiReviewer.TestResult?>(null) }
    var aiEdited by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAllResults by remember { mutableStateOf(false) }
    var selectedCoinId by remember { mutableStateOf<String?>(null) }
    var aiReviewAt by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var previousMatches by remember { mutableStateOf<Pair<Long, Set<String>>?>(null) }
    var trades by remember { mutableStateOf<List<PaperTradeStore.Trade>>(emptyList()) }
    var tradeNotice by remember { mutableStateOf<String?>(null) }
    var nobitex by remember { mutableStateOf<Map<String, NobitexMarkets.Result>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { PumpAiConfigStore.load(context) }
        if (!aiEdited) aiConfig = loaded
    }

    fun saveAiConfig(new: PumpAiConfig) {
        if (new != aiConfig) {
            aiReviews = emptyMap()
            aiErrors = emptyMap()
            aiTestResult = null
        }
        aiEdited = true
        aiConfig = new
        PumpAiConfigStore.saveDebounced(context, new) { saved ->
            aiStorageError = !saved
        }
    }

    fun runAiReview(coin: PumpScanner.PumpCoin) {
        val config = aiConfig
        if (!config.isReady || coin.id in aiBusyIds) return
        aiBusyIds = aiBusyIds + coin.id
        aiErrors = aiErrors - coin.id
        scope.launch {
            try {
                val outcome = PumpAiReviewer.review(config, coin)
                outcome.review?.let {
                    aiReviews = aiReviews + (coin.id to it)
                    aiReviewAt = aiReviewAt + (coin.id to System.currentTimeMillis())
                }
                outcome.error?.let { aiErrors = aiErrors + (coin.id to it) }
            } finally {
                aiBusyIds = aiBusyIds - coin.id
            }
        }
    }

    // فهرست قبلی (ذخیره‌شده روی گوشی) فوراً نشان داده می‌شود؛ اگر نبود یک بار اسکن می‌کنیم
    LaunchedEffect(Unit) {
        previousMatches = withContext(Dispatchers.IO) { PumpScanner.previousMatches(context) }
        val cached = PumpScanner.cached(context)
        // با باز شدن صفحه هم حد سود/ضرر با آخرین قیمت کش‌شده بررسی می‌شود.
        trades = withContext(Dispatchers.IO) {
            cached?.coins?.mapNotNull { coin -> coin.price?.let { coin.id to it } }?.toMap()
                ?.let { PaperTradeStore.settle(context, it) }
            PaperTradeStore.all(context)
        }
        scan = cached
        if (cached == null) {
            busy = true
            try {
                scan = PumpScanner.scan(context, cfg.pumpUniverse, cfg.pumpMinChange)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                note = "⚠️ اسکن پامپ کامل نشد؛ اتصال را بررسی کن"
            } finally {
                busy = false
            }
        }
    }

    fun runScan(force: Boolean) {
        busy = true
        note = ""
        scope.launch {
            try {
                val res = PumpScanner.scan(context, cfg.pumpUniverse, cfg.pumpMinChange, force = force)
                scan = res
                previousMatches = withContext(Dispatchers.IO) { PumpScanner.previousMatches(context) }
                // حد سود/حد ضرر معامله‌های آزمایشی با قیمت‌های تازه بررسی می‌شود.
                val closed = withContext(Dispatchers.IO) {
                    val prices = res.coins.mapNotNull { coin ->
                        coin.price?.let { coin.id to it }
                    }.toMap()
                    PaperTradeStore.settle(context, prices)
                }
                trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
                if (closed.isNotEmpty()) {
                    tradeNotice = closed.joinToString(" • ") { trade ->
                        "${trade.name}: ${trade.closeReason?.label ?: "بسته شد"} — " +
                                PaperTradeStore.resultText(trade, trade.closePrice, cfg.persianDigits)
                    }
                }
                if (res.error == null) PumpAlertEngine.evaluateScan(context, alertOwnerKey, cfg, res)
                note = res.error?.let {
                    "⚠️ اسکن تازه نگرفت — $it (فهرست قبلی نمایش داده می‌شود)"
                } ?: ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                note = "⚠️ اسکن پامپ کامل نشد؛ اتصال را بررسی کن"
            } finally {
                busy = false
            }
        }
    }

    val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
    // تغییر آستانه یک فیلتر محلی است و نباید تا اسکن شبکه‌ی بعدی بی‌اثر بماند.
    val matchingCoins = scan?.coins.orEmpty().filter {
        (it.change24h ?: Double.NEGATIVE_INFINITY) >= cfg.pumpMinChange
    }
    val shown = PumpScanner.sortByPeriod(matchingCoins, cfg.pumpSortPeriod)
    val visibleCoins = if (showAllResults) shown else shown.take(5)

    LaunchedEffect(scan?.at, cfg.pumpMinChange, cfg.pumpSortPeriod) {
        showAllResults = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        SectionHeader(
            "پامپ‌های کریپتو",
            "کوین‌هایی که تند رشد کرده‌اند — اول بفهم پامپ چیست، بعد نگاه کن"
        )

        // آموزش در آیکون راهنما جمع شده تا صفحه روی گوشی‌های کوچک شلوغ نشود.
        OutlinedButton(
            onClick = { showHelp = !showHelp },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text(if (showHelp) "بستن راهنمای پامپ" else "راهنمای پامپ و معنی پیام‌ها")
        }
        if (showHelp) PumpHelpCard()

        // ── تنظیمات اسکن ──
        SectionHeader("اسکن زنده", "داده‌ی لحظه‌ای CoinGecko — کوین‌های برتر بازار")
        RowsCard {
            SwitchRow(
                "نمایش این بخش در منو",
                "اگر نمی‌خواهی، از منوی تنظیمات مخفی می‌شود (اطلاعاتش می‌ماند)",
                cfg.showPumps
            ) { onChange(cfg.copy(showPumps = it)) }

            RowDivider()

            InnerRow {
                Text("دامنه‌ی اسکن", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (n in PumpScanner.UNIVERSE_CHOICES) {
                        FilterChip(
                            selected = cfg.pumpUniverse == n,
                            onClick = { onChange(cfg.copy(pumpUniverse = n)) },
                            label = { Text("${Format.toPersianDigits("$n")} کوین برتر") }
                        )
                    }
                }
                Hint("بیشتر پامپ‌ها بین کوین‌های کوچک‌تر (رتبه‌ی ۱۰۰ به بالا) رخ می‌دهد؛ دامنه‌ی بزرگ‌تر = دیدِ بازتر.")
            }

            RowDivider()

            InnerRow {
                Text("آستانه‌ی رشد ۲۴ ساعته", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (t in listOf(3.0, 5.0, 8.0, 15.0, 25.0)) {
                        FilterChip(
                            selected = cfg.pumpMinChange == t,
                            onClick = { onChange(cfg.copy(pumpMinChange = t)) },
                            label = { Text("${Format.toPersianDigits("${t.toInt()}")}٪ و بیشتر") }
                        )
                    }
                }
            }

            RowDivider()

            SwitchRow(
                "آلارم پامپ",
                if (cfg.pumpAlertEnabled)
                    "فعال است؛ اسکن دوره‌ای همراه با پیشنهاد احتیاطی و دلیل"
                else "در صورت عبور از آستانه اعلان بده (سیگنال خرید نیست)",
                cfg.pumpAlertEnabled
            ) { onAlertToggle(it) }

            if (cfg.pumpAlertEnabled) {
                InnerRow {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "فاصله‌ی اعلان‌ها",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (minutes in listOf(30, 60, 180, 360)) {
                            val label = if (minutes < 60) "$minutes دقیقه" else "${minutes / 60} ساعت"
                            FilterChip(
                                selected = cfg.pumpAlertCooldownMin == minutes,
                                onClick = { onChange(cfg.copy(pumpAlertCooldownMin = minutes)) },
                                label = { Text(Format.toPersianDigits(label)) }
                            )
                        }
                    }
                    Hint("برای جلوگیری از اسپم، هر نتیجه‌ی اسکن فقط یک‌بار بررسی می‌شود و در این فاصله اعلان دیگری نمی‌آید.")
                }
            }

            RowDivider()

            SettingRow(
                title = "آخرین اسکن",
                desc = when {
                    busy -> "در حال خواندن از CoinGecko…"
                    scan == null -> "هنوز اسکنی انجام نشده"
                    else -> "ساعت ${Format.time(scan!!.at)} • ${Format.toPersianDigits("${scan!!.coins.size}")} کوین بررسی شد"
                }
            ) {
                OutlinedButton(onClick = { runScan(true) }, enabled = !busy) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("اسکن تازه", fontSize = 12.sp)
                }
            }
        }

        if (note.isNotEmpty()) InfoCard(note)

        // ── ۳) نظر دوم هوش مصنوعی ──
        SectionHeader(
            "نظر دوم هوش مصنوعی",
            "اختیاری و مستقل از مدل — API سازگار، نام مدل و کلید را خودت تعیین می‌کنی"
        )
        RowsCard {
            SwitchRow(
                "بررسی با AI",
                if (aiConfig.enabled)
                    "برای هر کوین با دکمه اجرا می‌شود و پیشنهاد پایه را تغییر نمی‌دهد"
                else "خاموش؛ هیچ داده‌ای برای سرویس هوش مصنوعی فرستاده نمی‌شود",
                aiConfig.enabled
            ) { saveAiConfig(aiConfig.copy(enabled = it)) }

            if (aiConfig.enabled) {
                RowDivider()
                InnerRow {
                    Text(
                        "تنظیمات پیش‌فرض سرویس",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (preset in PumpAiConfig.PRESETS) {
                            FilterChip(
                                selected = aiConfig.matches(preset),
                                onClick = {
                                    saveAiConfig(
                                        aiConfig.copy(
                                            endpoint = preset.endpoint,
                                            model = preset.model,
                                            providerSearch = preset.providerSearch
                                        )
                                    )
                                },
                                label = { Text(preset.title, fontSize = 11.5.sp) }
                            )
                        }
                    }
                    val activePreset = PumpAiConfig.PRESETS.firstOrNull { aiConfig.matches(it) }
                    Hint(
                        activePreset?.hint
                            ?: "با انتخاب هر سرویس، آدرس API و نام مدل خودکار پر می‌شود؛ فقط کلید خودت را وارد کن. " +
                            "می‌توانی مقادیر را دستی هم تغییر بدهی."
                    )
                    OutlinedTextField(
                        value = aiConfig.endpoint,
                        onValueChange = { saveAiConfig(aiConfig.copy(endpoint = it.take(500))) },
                        label = { Text("آدرس API سازگار") },
                        placeholder = { Text("https://example.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiConfig.model,
                        onValueChange = { saveAiConfig(aiConfig.copy(model = it.take(150))) },
                        label = { Text("نام مدل") },
                        placeholder = { Text("نام مدل سرویس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiConfig.apiKey,
                        onValueChange = { saveAiConfig(aiConfig.copy(apiKey = it.take(1_000))) },
                        label = { Text("API Key (اگر سرویس لازم دارد)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (aiConfig.insecureKeyTransport) {
                        Hint("⚠️ برای امنیت، API Key روی HTTP ارسال نمی‌شود؛ آدرس HTTPS بگذار یا کلید را خالی کن.")
                    } else if (aiConfig.endpoint.startsWith("http://", ignoreCase = true)) {
                        Hint("⚠️ پاسخ HTTP رمزنگاری نشده و قابل دست‌کاری است؛ در صورت امکان HTTPS استفاده کن.")
                    }
                    if (aiConfig.endpoint.isNotBlank() && !aiConfig.endpointValid) {
                        Hint("آدرس باید HTTP(S) معتبر، بدون نام کاربری، query یا fragment باشد.")
                    }
                    if (aiConfig.model.isBlank()) {
                        Hint("نام مدل خالی است؛ یکی از سرویس‌های بالا را بزن یا نام مدل را دستی بنویس.")
                    }
                    if (aiStorageError) {
                        Hint("⚠️ Android Keystore کلید را ذخیره نکرد؛ برای امنیت، کلید روی دیسک نوشته نشد.")
                    }
                    Hint(
                        "کلید با Android Keystore رمزگذاری می‌شود و وارد بکاپ دستی نمی‌شود. " +
                                "با زدن دکمه، نام کوین و داده‌های قیمت/حجم/ریسک برای همین API فرستاده می‌شود. " +
                                "برای خبر، جست‌وجوی وب خود سرویس درخواست می‌شود؛ این قابلیت باید توسط مدل/API پشتیبانی شود."
                    )
                }
                RowDivider()
                InnerRow {
                    OutlinedButton(
                        onClick = {
                            val config = aiConfig
                            aiTestBusy = true
                            aiTestResult = null
                            scope.launch {
                                try {
                                    aiTestResult = PumpAiReviewer.testConnection(config)
                                } finally {
                                    aiTestBusy = false
                                }
                            }
                        },
                        enabled = !aiTestBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (aiTestBusy) "در حال تست اتصال…" else "تست اتصال به سرویس AI",
                            fontSize = 11.5.sp
                        )
                    }
                    aiTestResult?.let { result ->
                        Text(
                            result.message,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                        )
                    }
                    Hint(
                        "تست، یک پیام خیلی کوتاه برای سرویس می‌فرستد (بدون داده‌ی کوین) و نتیجه‌ی دقیق " +
                                "آدرس، مدل و کلید را می‌گوید."
                    )
                }
                RowDivider()
                SwitchRow(
                    "درخواست جست‌وجوی وب از سرویس",
                    if (aiConfig.providerSearch)
                        "برای OpenAI و OpenRouter افزونه‌ی جست‌وجو فعال می‌شود؛ سرویس‌های دیگر فقط بر اساس قابلیت خود مدل جست‌وجو می‌کنند."
                    else "خاموش؛ مدل فقط با داده‌های همین صفحه نظر می‌دهد و خبر تازه جست‌وجو نمی‌کند.",
                    aiConfig.providerSearch
                ) { saveAiConfig(aiConfig.copy(providerSearch = it)) }
            }
        }

        if (aiConfig.enabled) {
            InfoCard(
                "هوش مصنوعی فقط نظر دوم است و ممکن است اشتباه کند. لینک خبرها را پیش از تصمیم باز کن؛ " +
                        "نبود خبر معتبر یا اختلاف نظر، دلیل خرید نیست."
            )
        }

        // ── ۴) نتیجه ──
        val summary = when {
            scan == null -> "برای دیدن نتیجه، «اسکن تازه» را بزن."
            shown.isEmpty() -> "الان در ${Format.toPersianDigits("${scan!!.universe}")} کوین برتر، هیچ کوینی " +
                    "بیشتر از ${Format.toPersianDigits("${cfg.pumpMinChange.toInt()}")}٪ رشد ۲۴ ساعته ندارد — " +
                    "یعنی بازار فعلاً پامپ‌دار نیست (خودش یک خبر خوب است)."

            else -> "${Format.toPersianDigits("${shown.size}")} کوین از " +
                    "${Format.toPersianDigits("${scan!!.coins.size}")} کوین بررسی‌شده، بالای " +
                    "${Format.toPersianDigits("${cfg.pumpMinChange.toInt()}")}٪ رشد ۲۴ ساعته‌اند."
        }
        SectionHeader("نتیجه", summary)

        if (shown.isNotEmpty()) {
            RowsCard {
                InnerRow {
                    Text(
                        "مرتب‌سازی بر اساس بیشترین رشد",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((period, label) in listOf(
                            PumpSortPeriod.ONE_HOUR to "۱ ساعت",
                            PumpSortPeriod.ONE_DAY to "۱ روز",
                            PumpSortPeriod.ONE_MONTH to "۱ ماه"
                        )) {
                            FilterChip(
                                selected = cfg.pumpSortPeriod == period,
                                onClick = { onChange(cfg.copy(pumpSortPeriod = period)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Hint("ابتدا ۵ کوین اول دیده می‌شود؛ «نمایش بیشتر» بقیه را باز می‌کند.")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (coin in visibleCoins) {
                    PumpRow(
                        coin = coin,
                        persian = cfg.persianDigits,
                        alreadyAdded = cfg.symbols.any {
                            it.code == coin.id && it.sourceId == PumpScanner.CRYPTO_SOURCE_ID
                        },
                        aiReviewed = aiReviews[coin.id] != null,
                        onClick = { selectedCoinId = coin.id }
                    )
                }
            }
            if (shown.size > 5) {
                OutlinedButton(
                    onClick = { showAllResults = !showAllResults },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showAllResults) "نمایش فقط ۵ کوین اول"
                        else "نمایش ${Format.toPersianDigits((shown.size - 5).toString())} کوین دیگر"
                    )
                }
            }
            if (room <= 0) {
                Hint("ویجت پر است (سقف ${Format.toPersianDigits("$MAX_SYMBOLS")} نماد) — برای افزودن کوین تازه، یکی از نمادها را حذف کن.")
            }
        }

        Hint(
            "منبع داده: CoinGecko (کوین‌های برتر بر اساس ارزش بازار). برای اینکه کوین تازه‌ای را " +
                    "به ویجت اضافه کنی، لازم است منبع «کریپتو — CoinGecko» روشن باشد."
        )

        PaperWalletCard(
            trades = trades,
            prices = (scan?.coins ?: emptyList()).mapNotNull { coin ->
                coin.price?.let { coin.id to it }
            }.toMap(),
            persian = cfg.persianDigits,
            notice = tradeNotice,
            onSell = { trade ->
                scope.launch {
                    val price = scan?.coins?.firstOrNull { it.id == trade.coinId }?.price
                    val closed = withContext(Dispatchers.IO) {
                        PaperTradeStore.sell(context, trade.id, price)
                    }
                    trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
                    tradeNotice = if (closed == null) "برای فروش، اول یک اسکن تازه بزن تا قیمت به‌روز شود"
                    else "فروش آزمایشی ${closed.name}: " +
                            PaperTradeStore.resultText(closed, closed.closePrice, cfg.persianDigits)
                }
            },
            onClearHistory = {
                scope.launch {
                    withContext(Dispatchers.IO) { PaperTradeStore.clearClosed(context) }
                    trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
                    tradeNotice = null
                }
            }
        )

        Button(
            onClick = { runScan(true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (busy) "در حال اسکن…" else "اسکن تازه‌ی پامپ‌ها", fontSize = 12.5.sp)
        }
    }

    LaunchedEffect(selectedCoinId) {
        val coin = selectedCoinId?.let { id -> shown.firstOrNull { it.id == id } } ?: return@LaunchedEffect
        if (nobitex.containsKey(coin.id)) return@LaunchedEffect
        val result = NobitexMarkets.check(context, coin.symbol)
        nobitex = nobitex + (coin.id to result)
    }

    val selected = selectedCoinId?.let { id -> shown.firstOrNull { it.id == id } }
    if (selected != null) {
        PumpDetailSheet(
            coin = selected,
            persian = cfg.persianDigits,
            scanAt = scan?.at ?: 0L,
            previousMatches = previousMatches,
            canAdd = room > 0 && cfg.symbols.none {
                it.code == selected.id && it.sourceId == PumpScanner.CRYPTO_SOURCE_ID
            },
            alreadyAdded = cfg.symbols.any {
                it.code == selected.id && it.sourceId == PumpScanner.CRYPTO_SOURCE_ID
            },
            aiEnabled = aiConfig.enabled,
            aiReady = aiConfig.isReady,
            aiBusy = selected.id in aiBusyIds,
            aiReview = aiReviews[selected.id],
            aiReviewAt = aiReviewAt[selected.id],
            aiError = aiErrors[selected.id],
            nobitex = nobitex[selected.id],
            openTrade = trades.firstOrNull { it.coinId == selected.id && it.isOpen },
            onBuy = { amount, takeProfit, stopLoss, fee, stepCount, rangeFloorPct ->
                scope.launch {
                    val opened = withContext(Dispatchers.IO) {
                        PaperTradeStore.buy(
                            context, selected, amount, takeProfit, stopLoss, fee,
                            stepCount = stepCount, rangeFloorPct = rangeFloorPct
                        )
                    }
                    trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
                    tradeNotice = when {
                        opened == null -> "ثبت خرید آزمایشی ممکن نشد (قیمت یا مبلغ نامعتبر)"
                        opened.isLadder -> "خرید پله‌ای آزمایشی ${opened.name} ثبت شد " +
                                "(${Format.toPersianDigits("${opened.steps.size}")} پله)."
                        else -> "خرید آزمایشی ${opened.name} ثبت شد."
                    }
                }
            },
            onSell = {
                scope.launch {
                    val open = trades.firstOrNull { it.coinId == selected.id && it.isOpen }
                    val closed = if (open == null) null else withContext(Dispatchers.IO) {
                        PaperTradeStore.sell(context, open.id, selected.price)
                    }
                    trades = withContext(Dispatchers.IO) { PaperTradeStore.all(context) }
                    tradeNotice = if (closed == null) "فروش آزمایشی ممکن نشد"
                    else "فروش آزمایشی ${closed.name}: " +
                            PaperTradeStore.resultText(closed, closed.closePrice, cfg.persianDigits)
                }
            },
            onAiReview = { runAiReview(selected) },
            onOpenLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onAdd = { onAddSymbol(selected.toSymbolDef()) },
            onDismiss = { selectedCoinId = null }
        )
    }
}

/** راهنمای جمع‌شونده‌ی این صفحه؛ معنی اصطلاح‌ها را بدون شلوغ‌کردن نتیجه توضیح می‌دهد. */
@Composable
private fun PumpHelpCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "پامپ و پیام‌های این صفحه یعنی چه؟",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "پامپ یعنی قیمت در زمان کوتاه با سرعت زیادی بالا رفته است. این رشد ممکن است از خبر واقعی باشد، " +
                        "اما در کوین‌های کوچک گاهی گروهی است و بعد از فروش بازیگران اولیه، قیمت سریع می‌ریزد.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "معنی نتیجه‌های احتیاطی",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HelpMeaning(
                "فعلاً فقط زیر نظر بگیر",
                "یعنی داده‌ها ارزش مشاهده دارند، نه اینکه برنامه خرید را پیشنهاد کرده باشد."
            )
            HelpMeaning(
                "صبر کن؛ ورود عجولانه نکن",
                "یعنی ادامه‌ی رشد هنوز تأیید نشده و بهتر است تثبیت قیمت و حجم را ببینی."
            )
            HelpMeaning(
                "فعلاً وارد نشو؛ قیمت را تعقیب نکن",
                "یعنی قیمت قبلاً ناگهانی بالا رفته است؛ فقط از ترس جاماندن دنبال آن نرو، چون ممکن است نزدیک سقف بخری. این پیام دستور فروش دارایی فعلی نیست."
            )
            Text(
                "بازه‌ها: ۱ ساعت حرکت خیلی کوتاه‌مدت، ۱ روز تغییر ۲۴ ساعت، ۱ هفته تغییر ۷ روز و ۱ ماه تغییر ۳۰ روز اخیر است. مرتب‌سازی فقط جای نمایش را عوض می‌کند و سیگنال خرید نیست.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "امتیاز پامپ یعنی چه؟",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "امتیاز = رشد ۲۴ ساعته + ۲× رشد ۱ ساعته + ۵۰× (حجم ۲۴ ساعته ÷ ارزش بازار). " +
                        "عدد بزرگ‌تر یعنی حرکت شدیدتر، نه فرصت بهتر.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            for (band in PumpScanner.ScoreBand.entries) {
                HelpMeaning(band.label, band.meaning)
            }
            RuleLine("۱", "فقط به دلیل رشد زیاد خرید نکن؛ رشد زیاد معمولاً یعنی ریسک بیشتر.")
            RuleLine("۲", "حجم، ارزش بازار، خبر معتبر و امکان برداشت از صرافی را جداگانه بررسی کن.")
            RuleLine("۳", "به گروه‌ها و عبارت‌هایی مثل «سود قطعی» یا «سیگنال تضمینی» اعتماد نکن.")
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "این بخش ابزار مشاهده و آموزش است؛ هیچ‌کدام از پیام‌ها توصیه‌ی مالی یا دستور خرید و فروش نیست.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HelpMeaning(title: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            explanation,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** یک خط از قواعد احتیاط */
@Composable
private fun RuleLine(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                Format.toPersianDigits(number),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
internal fun PumpChangeBadge(label: String, value: Double?, persian: Boolean) {
    val color = when {
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        value > 0.0 -> Color(0xFF16A34A)
        value < 0.0 -> Color(0xFFDC2626)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            "$label ${Format.pct(value, persian).ifBlank { "—" }}",
            fontSize = 10.5.sp,
            color = color
        )
    }
}

/** کارت یک کوین در فهرست پامپ — با کلیک، جزئیات کامل باز می‌شود. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PumpRow(
    coin: PumpScanner.PumpCoin,
    persian: Boolean,
    alreadyAdded: Boolean,
    aiReviewed: Boolean,
    onClick: () -> Unit
) {
    val risk = riskColor(coin.risk)
    val bandColor = scoreBandColor(coin.scoreBand)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, bandColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // نوار رنگی بالای کارت: رنگ = دسته‌ی امتیاز پامپ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(bandColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            coin.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            QuoteText.priceWithUnit(coin.price, "$", persian),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .background(risk.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(coin.risk.label, fontSize = 10.sp, color = risk)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "امتیاز ${Format.price(coin.score, persian)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = bandColor
                        )
                    }
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "جزئیات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PumpChangeBadge("۱ ساعت", coin.change1h, persian)
                    PumpChangeBadge("۱ روز", coin.change24h, persian)
                    PumpChangeBadge("۱ هفته", coin.change7d, persian)
                    PumpChangeBadge("۱ ماه", coin.change30d, persian)
                }
                Text(
                    "${coin.scoreBand.label} • مرحله: ${coin.stage.label}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bandColor
                )
                Text(
                    "نتیجه‌ی احتیاطی: ${coin.advice.recommendation.label}",
                    fontSize = 11.sp,
                    color = risk
                )
                val footer = buildString {
                    append("برای جزئیات، نمودار و نظر AI بزن")
                    if (alreadyAdded) append(" • ✓ در ویجت")
                    if (aiReviewed) append(" • نظر AI آماده است")
                }
                Text(
                    footer,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** رنگ دسته‌ی امتیاز پامپ — همان رنگی که در جزئیات هم استفاده می‌شود. */
internal fun scoreBandColor(band: PumpScanner.ScoreBand): Color = when (band) {
    PumpScanner.ScoreBand.CALM -> Color(0xFF64748B)
    PumpScanner.ScoreBand.MODERATE -> Color(0xFF16A34A)
    PumpScanner.ScoreBand.STRONG -> Color(0xFFF59E0B)
    PumpScanner.ScoreBand.OVERHEATED -> Color(0xFFDC2626)
}

/** رنگ ثابت هر سطح ریسک — در فهرست و صفحه‌ی جزئیات یکسان است. */
internal fun riskColor(risk: PumpScanner.Risk): Color = when (risk) {
    PumpScanner.Risk.LOW -> Color(0xFF22C55E)
    PumpScanner.Risk.MEDIUM -> Color(0xFFF59E0B)
    PumpScanner.Risk.HIGH -> Color(0xFFF43F5E)
}

/** کیف آزمایشی: معامله‌های باز، نتیجه‌ی معامله‌های بسته و خلاصه‌ی عملکرد. */
@Composable
private fun PaperWalletCard(
    trades: List<PaperTradeStore.Trade>,
    prices: Map<String, Double>,
    persian: Boolean,
    notice: String?,
    onSell: (PaperTradeStore.Trade) -> Unit,
    onClearHistory: () -> Unit
) {
    if (trades.isEmpty() && notice == null) return
    val summary = PaperTradeStore.summarize(trades, prices)
    val open = trades.filter { it.isOpen }
    val closed = trades.filterNot { it.isOpen }.take(10)
    SectionHeader(
        "کیف آزمایشی",
        "خلاصه‌ی معامله‌ها؛ جزئیات کامل و تاریخچه در تب «معامله‌های من» است."
    )
    RowsCard {
        if (!notice.isNullOrBlank()) {
            InnerRow { Hint(notice) }
            RowDivider()
        }
        InnerRow {
            Text(
                "سود محقق‌شده: ${money(summary.realizedUsd, persian)} • " +
                        "سود باز: ${money(summary.openUsd, persian)}",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (summary.realizedUsd + summary.openUsd >= 0.0) Color(0xFF16A34A)
                else Color(0xFFDC2626)
            )
            Text(
                "کارمزد پرداخت‌شده: ${Format.price(PaperTradeStore.totalFees(trades, prices), persian)} دلار",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "معامله‌ی باز: ${Format.toPersianDigits("${summary.openCount}")} • " +
                        "بسته‌شده: ${Format.toPersianDigits("${summary.closedCount}")} • " +
                        "برد: ${Format.toPersianDigits("${summary.wins}")} / " +
                        "باخت: ${Format.toPersianDigits("${summary.losses}")}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for (trade in open) {
            RowDivider()
            InnerRow {
                Text(
                    "${trade.name} (${trade.symbol})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "خرید در ${QuoteText.priceWithUnit(trade.entryPrice, "$", persian)} • " +
                            "مبلغ ${QuoteText.priceWithUnit(trade.amountUsd, "$", persian)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "نتیجه‌ی فعلی: ${PaperTradeStore.resultText(trade, prices[trade.coinId], persian)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((trade.profitPct(prices[trade.coinId]) ?: 0.0) >= 0.0)
                        Color(0xFF16A34A) else Color(0xFFDC2626)
                )
                val levels = buildString {
                    trade.takeProfitPrice?.let {
                        append("حد سود ${QuoteText.priceWithUnit(it, "$", persian)}")
                    }
                    trade.stopLossPrice?.let {
                        if (isNotEmpty()) append(" • ")
                        append("حد ضرر ${QuoteText.priceWithUnit(it, "$", persian)}")
                    }
                }
                if (levels.isNotBlank()) {
                    Text(levels, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onSell(trade) }, modifier = Modifier.fillMaxWidth()) {
                    Text("فروش آزمایشی", fontSize = 11.sp)
                }
            }
        }
        if (closed.isNotEmpty()) {
            RowDivider()
            InnerRow {
                Text(
                    "معامله‌های بسته‌شده",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                for (trade in closed) {
                    Text(
                        "${trade.name}: ${PaperTradeStore.resultText(trade, null, persian)} — " +
                                (trade.closeReason?.label ?: "بسته شد"),
                        fontSize = 10.5.sp,
                        color = if ((trade.profitPct(null) ?: 0.0) >= 0.0) Color(0xFF16A34A)
                        else Color(0xFFDC2626)
                    )
                }
                OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                    Text("پاک کردن تاریخچه", fontSize = 11.sp)
                }
            }
        }
    }
}

/** مبلغ دلاری با علامت سود/زیان */
private fun money(value: Double, persian: Boolean): String {
    val sign = if (value >= 0.0) "+" else "−"
    val text = Format.price(kotlin.math.abs(value), persian)
    return "$sign$text دلار"
}
