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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AiUsageStore
import com.pulse.market.data.MAX_SYMBOLS
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PaperTradeStore
import com.pulse.market.data.PumpAlertEngine
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpScanner
import com.pulse.market.data.PumpSortPeriod
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText

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
    initialTab: Int = 0,
    onChange: (WidgetConfig) -> Unit,
    onAlertToggle: (Boolean) -> Unit,
    onAddSymbol: (SymbolDef) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vm: PumpsViewModel = viewModel()

    // بارگذاری اولیه یک‌بار انجام می‌شود؛ چون state در ViewModel است، با ترک و
    // بازگشت به این بخش (یا چرخش صفحه) نتیجه‌ی اسکن و معامله‌ها حفظ می‌شود.
    LaunchedEffect(Unit) { vm.start(cfg.pumpUniverse, cfg.pumpMinChange) }

    // آینه‌ی خواندنیِ state از ViewModel — خواندن این‌ها در composition، recomposition
    // درست هنگام تغییر می‌سازد و بدنه‌ی UI پایین بدون تغییر می‌ماند.
    val scan = vm.scan
    val busy = vm.busy
    val note = vm.note
    val aiConfig = vm.aiConfig
    val aiBusyIds = vm.aiBusyIds
    val aiReviews = vm.aiReviews
    val aiErrors = vm.aiErrors
    val aiReviewAt = vm.aiReviewAt
    val aiStorageError = vm.aiStorageError
    val aiTestBusy = vm.aiTestBusy
    val aiTestResult = vm.aiTestResult
    val aiTarget = remember(aiConfig) { PumpAiReviewer.connectionTarget(aiConfig) }
    val previousMatches = vm.previousMatches
    val trades = vm.trades
    val tradeNotice = vm.tradeNotice
    val nobitex = vm.nobitex

    var showHelp by remember { mutableStateOf(false) }
    var showAllResults by remember { mutableStateOf(false) }
    var selectedCoinId by remember { mutableStateOf<String?>(null) }
    var pumpTab by rememberSaveable(initialTab) { mutableStateOf(initialTab.coerceIn(0, 3)) }

    val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
    // تغییر آستانه یک فیلتر محلی است و نباید تا اسکن شبکه‌ی بعدی بی‌اثر بماند.
    val matchingCoins = scan?.coins.orEmpty().filter {
        (it.change24h ?: Double.NEGATIVE_INFINITY) >= cfg.pumpMinChange
    }
    val shown = PumpScanner.sortByPeriod(matchingCoins, cfg.pumpSortPeriod)
    val visibleCoins = if (showAllResults) shown else shown.take(5)

    // قیمت‌های زنده‌ی همین اسکن یک‌بار ساخته می‌شوند و در کیف/فروش دوباره استفاده
    // می‌شوند تا از ساختِ تکراریِ Map جلوگیری شود.
    val livePrices = remember(scan) {
        scan?.coins.orEmpty().mapNotNull { c -> c.price?.let { c.id to it } }.toMap()
    }

    LaunchedEffect(scan?.at, cfg.pumpMinChange, cfg.pumpSortPeriod) {
        showAllResults = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        SectionHeader(
            "پامپ‌های کریپتو",
            "کوین‌هایی که تند رشد کرده‌اند — اول بفهم پامپ چیست، بعد نگاه کن"
        )

        // ── نوار تب‌ها: صفحه‌ی شلوغِ پامپ به چند تبِ مرتب تقسیم شده تا روی
        //    گوشی‌های کوچک هم خلوت و امروزی بماند. حالت هر تب حفظ می‌شود. ──
        val pumpTabs = listOf(
            "پامپ‌ها" to Icons.Default.TrendingUp,
            "اسکن" to Icons.Default.Tune,
            "هوش‌مصنوعی" to Icons.Default.AutoAwesome,
            "کیف" to Icons.Default.AccountBalanceWallet
        )
        TabRow(
            selectedTabIndex = pumpTab,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[pumpTab]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            pumpTabs.forEachIndexed { index, (label, icon) ->
                val active = pumpTab == index
                Tab(
                    selected = active,
                    onClick = { pumpTab = index },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                )
            }
        }

        // بازخورد بصری هنگام اسکن — روی هر تبی که باشی نشان داده می‌شود.
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        when (pumpTab) {
            // ───────── تب ۱) پامپ‌ها: نتیجه‌ی اسکن + راهنما ─────────
            0 -> {
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

                if (note.isNotEmpty()) InfoCard(note)

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
                            key(coin.id) {
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

                Button(
                    onClick = { vm.runScan(cfg, alertOwnerKey, true) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "در حال اسکن…" else "اسکن تازه‌ی پامپ‌ها", fontSize = 12.5.sp)
                }
            }

            // ───────── تب ۲) اسکن: تنظیمات اسکن زنده ─────────
            1 -> {
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
                        OutlinedButton(onClick = { vm.runScan(cfg, alertOwnerKey, true) }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("اسکن تازه", fontSize = 12.sp)
                        }
                    }
                }

                if (note.isNotEmpty()) InfoCard(note)
            }

            // ───────── تب ۳) هوش مصنوعی: نظر دوم اختیاری ─────────
            2 -> {
                SectionHeader(
                    "نظر دوم هوش مصنوعی",
                    "اتصال بومی Gemini/Claude یا API سازگار OpenAI — مدل و کلید دست خودت"
                )
                RowsCard {
                    SwitchRow(
                        "بررسی با AI",
                        if (aiConfig.enabled)
                            "برای هر کوین با دکمه اجرا می‌شود و پیشنهاد پایه را تغییر نمی‌دهد"
                        else "خاموش؛ هیچ داده‌ای برای سرویس هوش مصنوعی فرستاده نمی‌شود",
                        aiConfig.enabled
                    ) { vm.saveAiConfig(aiConfig.copy(enabled = it)) }

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
                                            vm.saveAiConfig(
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
                            Hint(
                                "⚠️ اگر در ایران و بدون فیلترشکن هستی، Gemini، OpenAI و Claude درخواست را رد " +
                                    "می‌کنند (خطای محدودیت جغرافیایی یا «اتصال برقرار نشد»). در این حالت یکی از " +
                                    "درگاه‌های ایرانی «آوالای» یا «گپ‌جی‌پی‌تی» را انتخاب کن — سازگار با OpenAI و " +
                                    "بدون فیلترشکن — یا دست‌کم یکی از آن‌ها را پایین به‌عنوان سرویس پشتیبان اضافه کن."
                            )
                            val activePreset = PumpAiConfig.PRESETS.firstOrNull { aiConfig.matches(it) }
                            Hint(
                                activePreset?.hint
                                    ?: "با انتخاب هر سرویس، آدرس API و نام مدل خودکار پر می‌شود؛ فقط کلید خودت را وارد کن. " +
                                    "می‌توانی مقادیر را دستی هم تغییر بدهی."
                            )
                            OutlinedTextField(
                                value = aiConfig.endpoint,
                                onValueChange = { vm.saveAiConfig(aiConfig.copy(endpoint = it.take(500))) },
                                label = { Text("آدرس API") },
                                placeholder = { Text("https://example.com/v1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = aiConfig.model,
                                onValueChange = { vm.saveAiConfig(aiConfig.copy(model = it.take(150))) },
                                label = { Text("نام مدل") },
                                placeholder = { Text("نام مدل سرویس") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = aiConfig.apiKey,
                                onValueChange = { vm.saveAiConfig(aiConfig.copy(apiKey = it.take(1_000))) },
                                label = { Text("API Key (اگر سرویس لازم دارد)") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PumpAiReviewer.keyWarning(aiConfig.endpoint, aiConfig.apiKey)?.let { problem ->
                                Hint("⚠️ $problem")
                            }
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
                                    "نام کوین و داده‌های قیمت/حجم/ریسک برای همین API فرستاده می‌شود. " +
                                    "تیتر خبرها جداگانه از RSSهای معتبر گرفته می‌شود و ترجمه/تحلیل آن‌ها نیز با همین تنظیم AI انجام می‌شود."
                            )
                            aiTarget?.let { target ->
                                Hint(
                                    "مقصد واقعی و مستقیم: ${target.display} • پروتکل: ${target.route} • " +
                                        "مدل: ${aiConfig.model}"
                                )
                            }
                        }
                        RowDivider()
                        InnerRow {
                            OutlinedButton(
                                onClick = { vm.testAiConnection() },
                                enabled = !aiTestBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (aiTestBusy) {
                                        "در حال تست مستقیم ${aiTarget?.host ?: "API"}…"
                                    } else {
                                        "تست اتصال به سرویس AI"
                                    },
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
                                // گزارش کامل (بدون کلید) تا کاربر بتواند دقیقاً همان متن
                                // خطا را جایی بفرستد یا نگه دارد؛ تایپ دوباره‌ی متن طولانی لازم نیست.
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(
                                            AnnotatedString(
                                                "گزارش عیب‌یابی هوش مصنوعی — نبض بازار\n" +
                                                    "مدل: ${aiConfig.model}\n" +
                                                    "مقصد: ${aiTarget?.display ?: aiConfig.endpoint}\n" +
                                                    "کلید ذخیره‌شده: ${PumpAiReviewer.keyFingerprint(aiConfig.apiKey)}\n" +
                                                    "تعداد سرویس آماده: ${aiConfig.chain.size}\n\n" +
                                                    result.message
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("کپی گزارش عیب‌یابی (بدون کلید)", fontSize = 11.5.sp)
                                }
                            }
                            Hint(
                                "تست یک پیام کوتاه و بدون داده‌ی کوین مستقیماً به مقصد بالا می‌فرستد؛ " +
                                    "هر مسیر حداکثر ${PumpAiReviewer.CONNECTION_TEST_TIMEOUT_SECONDS} ثانیه فرصت دارد و " +
                                    "نتیجه، مقصد واقعی، پروتکل، زمان پاسخ و کیفیت اتصال را نشان می‌دهد. " +
                                    "اگر سرویس پشتیبان تعریف کرده باشی، همه‌ی سرویس‌ها هم‌زمان تست و نتیجه‌ی " +
                                    "هرکدام جداگانه گزارش می‌شود."
                            )
                        }
                        RowDivider()
                        SwitchRow(
                            "درخواست جست‌وجوی وب از سرویس",
                            if (aiConfig.providerSearch)
                                "برای OpenAI و OpenRouter افزونه‌ی جست‌وجو فعال می‌شود؛ سرویس‌های دیگر فقط بر اساس قابلیت خود مدل جست‌وجو می‌کنند."
                            else "خاموش؛ مدل فقط با داده‌های همین صفحه نظر می‌دهد و خبر تازه جست‌وجو نمی‌کند.",
                            aiConfig.providerSearch
                        ) { vm.saveAiConfig(aiConfig.copy(providerSearch = it)) }

                        RowDivider()
                        // ───── مدیریت مصرف توکن ─────
                        SwitchRow(
                            "حالت کم‌مصرف (صرفه‌جویی در توکن)",
                            if (aiConfig.economyMode)
                                "دستور فشرده، پاسخ کوتاه‌تر و خبرهای کمتر در هر درخواست — حدود نیمی از توکن قبلی."
                            else "خاموش؛ دستور کامل و پاسخ بلندتر فرستاده می‌شود (دقیق‌تر ولی گران‌تر).",
                            aiConfig.economyMode
                        ) { vm.saveAiConfig(aiConfig.copy(economyMode = it)) }
                        RowDivider()
                        InnerRow {
                            OutlinedTextField(
                                value = if (aiConfig.reuseMinutes == 0) "" else aiConfig.reuseMinutes.toString(),
                                onValueChange = { raw ->
                                    val minutes = raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                                    vm.saveAiConfig(aiConfig.copy(reuseMinutes = minutes.coerceIn(0, 24 * 60)))
                                },
                                label = { Text("استفاده از تحلیل ذخیره‌شده تا (دقیقه)") },
                                placeholder = { Text("۰ = همیشه درخواست تازه") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Hint(
                                "اگر همین کوین در این بازه تحلیل شده و قیمتش بیش از ۲٪ تکان نخورده باشد، " +
                                    "همان تحلیل نشان داده می‌شود و درخواست تازه‌ای نمی‌رود. زدن دوباره‌ی دکمه، " +
                                    "همیشه تحلیل تازه می‌گیرد."
                            )
                            OutlinedTextField(
                                value = if (aiConfig.dailyTokenBudget == 0) "" else aiConfig.dailyTokenBudget.toString(),
                                onValueChange = { raw ->
                                    val budget = raw.filter { it.isDigit() }.take(7).toIntOrNull() ?: 0
                                    vm.saveAiConfig(aiConfig.copy(dailyTokenBudget = budget.coerceIn(0, 5_000_000)))
                                },
                                label = { Text("سقف مصرف روزانه (توکن)") },
                                placeholder = { Text("۰ = بدون سقف") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                AiUsageStore.summaryText(
                                    today = vm.aiUsageToday,
                                    weekTotal = vm.aiUsageWeek,
                                    budget = aiConfig.dailyTokenBudget
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { vm.clearAiUsage() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("صفر کردن شمارنده‌ی مصرف", fontSize = 11.5.sp)
                            }
                            Hint(
                                "عددها از خودِ پاسخ سرویس خوانده می‌شوند (و اگر سرویس گزارش ندهد، تخمین زده می‌شود) " +
                                    "و فقط روی همین گوشی می‌مانند. با رسیدن به سقف، تا پایان شبانه‌روز درخواست تازه‌ای فرستاده نمی‌شود."
                            )
                        }
                        RowDivider()
                        AiBackupProvidersSection(
                            config = aiConfig,
                            onChange = { vm.saveAiConfig(it) }
                        )
                    }
                }

                if (aiConfig.enabled) {
                    InfoCard(
                        "هوش مصنوعی فقط نظر دوم است و ممکن است اشتباه کند. لینک خبرها را پیش از تصمیم باز کن؛ " +
                                "نبود خبر معتبر یا اختلاف نظر، دلیل خرید نیست."
                    )
                }
            }

            // ───────── تب ۴) کیف: معامله‌های آزمایشی ─────────
            else -> {
                PaperWalletCard(
                    trades = trades,
                    prices = livePrices,
                    persian = cfg.persianDigits,
                    notice = tradeNotice,
                    onSell = { trade ->
                        vm.sellTrade(trade, livePrices[trade.coinId], cfg.persianDigits)
                    },
                    onClearHistory = { vm.clearHistory() }
                )
            }
        }
    }

    LaunchedEffect(selectedCoinId) {
        val coin = selectedCoinId?.let { id -> shown.firstOrNull { it.id == id } } ?: return@LaunchedEffect
        vm.checkNobitex(coin)
        vm.loadAiHistory(coin.id)
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
            aiReady = aiConfig.anyReady,
            aiBusy = selected.id in aiBusyIds,
            aiReview = aiReviews[selected.id],
            aiReviewAt = aiReviewAt[selected.id],
            aiHistory = vm.aiHistory[selected.id].orEmpty(),
            aiError = aiErrors[selected.id],
            aiNote = vm.aiNotes[selected.id],
            nobitex = nobitex[selected.id],
            openTrade = trades.firstOrNull { it.coinId == selected.id && it.isOpen },
            onBuy = { amount, takeProfit, stopLoss, fee, buyPrices, sellPrices ->
                vm.buy(selected, amount, takeProfit, stopLoss, fee, buyPrices, sellPrices, cfg.persianDigits)
            },
            onSell = { vm.sellOpenForCoin(selected.id, selected.price, cfg.persianDigits) },
            onAiReview = { vm.runAiReview(selected) },
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
    if (trades.isEmpty() && notice.isNullOrBlank()) {
        InfoCard(
            "هنوز معامله‌ای نداری. در تب «پامپ‌ها» روی یک کوین بزن و یک خرید آزمایشی ثبت کن؛ " +
                    "از آن‌جا به بعد زمان و قیمتِ خرید/فروش و سود هر معامله همین‌جا دیده می‌شود."
        )
        return
    }
    val context = LocalContext.current
    val account = remember(trades) { PaperTradeStore.account(context) }
    val summary = PaperTradeStore.summarize(trades, prices, account)
    val open = trades.filter { it.isOpen }
    val closed = trades.filterNot { it.isOpen }.take(10)

    SectionHeader(
        "کیف آزمایشی",
        "زمان و قیمتِ خرید/فروش و سود هر معامله. مدیریت کامل در تب «معامله‌های من» است."
    )

    if (!notice.isNullOrBlank()) InfoCard(notice)

    TradeSummaryCard(summary, persian)

    if (open.isNotEmpty()) {
        SectionHeader("معامله‌های باز")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (trade in open) {
                key(trade.id) {
                    WalletTradeCard(
                        trade = trade,
                        price = prices[trade.coinId],
                        persian = persian,
                        onSell = { onSell(trade) }
                    )
                }
            }
        }
    }

    if (closed.isNotEmpty()) {
        SectionHeader("تاریخچه")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (trade in closed) {
                key(trade.id) {
                    WalletTradeCard(
                        trade = trade,
                        price = trade.closePrice,
                        persian = persian
                    )
                }
            }
        }
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
            Text("پاک کردن تاریخچه", fontSize = 11.5.sp)
        }
    }
}
