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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.MAX_SYMBOLS
import com.pulse.market.data.PumpAiConfig
import com.pulse.market.data.PumpAiConfigStore
import com.pulse.market.data.PumpAiReviewer
import com.pulse.market.data.PumpAlertEngine
import com.pulse.market.data.PumpScanner
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import kotlinx.coroutines.launch

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
@OptIn(ExperimentalLayoutApi::class)
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

    LaunchedEffect(Unit) {
        aiConfig = PumpAiConfigStore.load(context)
    }

    fun saveAiConfig(new: PumpAiConfig) {
        if (new != aiConfig) {
            aiReviews = emptyMap()
            aiErrors = emptyMap()
        }
        aiConfig = new
        aiStorageError = !PumpAiConfigStore.save(context, new)
    }

    fun runAiReview(coin: PumpScanner.PumpCoin) {
        val config = aiConfig
        if (!config.isReady || coin.id in aiBusyIds) return
        aiBusyIds = aiBusyIds + coin.id
        aiErrors = aiErrors - coin.id
        scope.launch {
            val outcome = PumpAiReviewer.review(config, coin)
            outcome.review?.let { aiReviews = aiReviews + (coin.id to it) }
            outcome.error?.let { aiErrors = aiErrors + (coin.id to it) }
            aiBusyIds = aiBusyIds - coin.id
        }
    }

    // فهرست قبلی (ذخیره‌شده روی گوشی) فوراً نشان داده می‌شود؛ اگر نبود یک بار اسکن می‌کنیم
    LaunchedEffect(Unit) {
        val cached = PumpScanner.cached(context)
        scan = cached
        if (cached == null) {
            busy = true
            scan = PumpScanner.scan(context, cfg.pumpUniverse, cfg.pumpMinChange)
            busy = false
        }
    }

    fun runScan(force: Boolean) {
        busy = true
        note = ""
        scope.launch {
            val res = PumpScanner.scan(context, cfg.pumpUniverse, cfg.pumpMinChange, force = force)
            scan = res
            if (res.error == null) PumpAlertEngine.evaluateScan(context, alertOwnerKey, cfg, res)
            busy = false
            note = res.error?.let { "⚠️ اسکن تازه نگرفت — $it (فهرست قبلی نمایش داده می‌شود)" } ?: ""
        }
    }

    val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
    // تغییر آستانه یک فیلتر محلی است و نباید تا اسکن شبکه‌ی بعدی بی‌اثر بماند.
    val shown = scan?.coins.orEmpty().filter {
        (it.change24h ?: Double.NEGATIVE_INFINITY) >= cfg.pumpMinChange
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        SectionHeader(
            "پامپ‌های کریپتو",
            "کوین‌هایی که تند رشد کرده‌اند — اول بفهم پامپ چیست، بعد نگاه کن"
        )

        // ── ۱) پامپ چیست؟ (آموزش) ──
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        "پامپ (Pump) چیست؟",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "پامپ یعنی قیمت یک کوین در زمان کوتاه (دقیقه تا چند ساعت) شارپ بالا می‌رود. " +
                            "دو شکل دارد:\n" +
                            "• «پامپ ارگانیک»: خبر واقعی یا ورود پول بزرگ باعث رشد می‌شود؛ حجم معاملات " +
                            "به‌طور طبیعی بالا می‌رود.\n" +
                            "• «پامپ گروهی / Pump & Dump»: یک گروه، پیش از رشد، کوین ارزان و کم‌عمق را " +
                            "می‌خرند؛ بعد با پیام‌های «سیگنال قطعی» در گروه‌ها و شبکه‌های اجتماعی هجوم " +
                            "خریدراه می‌اندازند؛ وقتی قیمت بالا رفت، خودشان می‌فروشند و بقیه در سقف می‌مانند.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "نشانه‌های هشدار پامپ گروهی: رشد ناگهانی کوینی که ارزش بازارش کوچک است • جهش حجم " +
                            "بدون هیچ خبر رسمی • تبلیغ «سیگنال ۱۰۰٪ تضمینی» و گروه‌های تلگرامی • سرمایه‌گذاری " +
                            "بدون امکان برداشت در همان صرافی/بات • کندل‌های بلند پشت‌سرهم و بعد فروریختن سریع قیمت.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── ۲) تنظیمات اسکن ──
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PumpScanner.UNIVERSE_CHOICES.forEach { n ->
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3.0, 5.0, 8.0, 15.0, 25.0).forEach { t ->
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 180, 360).forEach { minutes ->
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
                    OutlinedTextField(
                        value = aiConfig.endpoint,
                        onValueChange = { saveAiConfig(aiConfig.copy(endpoint = it)) },
                        label = { Text("آدرس API سازگار") },
                        placeholder = { Text("https://example.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiConfig.model,
                        onValueChange = { saveAiConfig(aiConfig.copy(model = it)) },
                        label = { Text("نام مدل") },
                        placeholder = { Text("نام مدل سرویس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiConfig.apiKey,
                        onValueChange = { saveAiConfig(aiConfig.copy(apiKey = it)) },
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
                    if (aiStorageError) {
                        Hint("⚠️ Android Keystore کلید را ذخیره نکرد؛ برای امنیت، کلید روی دیسک نوشته نشد.")
                    }
                    Hint(
                        "کلید با Android Keystore رمزگذاری می‌شود و وارد بکاپ دستی نمی‌شود. " +
                                "با زدن دکمه، نام کوین و داده‌های قیمت/حجم/ریسک برای همین API فرستاده می‌شود. " +
                                "برای خبر، جست‌وجوی وب خود سرویس درخواست می‌شود؛ این قابلیت باید توسط مدل/API پشتیبانی شود."
                    )
                }
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
                shown.forEachIndexed { i, coin ->
                    if (i > 0) RowDivider()
                    PumpRow(
                        coin = coin,
                        persian = cfg.persianDigits,
                        canAdd = cfg.symbols.none { it.code == coin.id } && room > 0,
                        alreadyAdded = cfg.symbols.any { it.code == coin.id },
                        aiEnabled = aiConfig.enabled,
                        aiReady = aiConfig.isReady,
                        aiBusy = coin.id in aiBusyIds,
                        aiReview = aiReviews[coin.id],
                        aiError = aiErrors[coin.id],
                        onAiReview = { runAiReview(coin) },
                        onOpenNews = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        onAdd = { onAddSymbol(coin.toSymbolDef()) }
                    )
                }
            }
            if (room <= 0) {
                Hint("ویجت پر است (سقف ${Format.toPersianDigits("$MAX_SYMBOLS")} نماد) — برای افزودن کوین تازه، یکی از نمادها را حذف کن.")
            }
        }

        // ── ۴) قواعد احتیاط ──
        SectionHeader("چطور در دام پامپ نیفتیم", "چهار قاعده‌ی ساده")
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleLine("۱", "هرگز فقط به خاطر «رشد زیاد» نخر. رشد زیاد یعنی ریسک زیاد — نه سود تضمینی.")
                RuleLine("۲", "به حجم نگاه کن، نه به درصد. اگر حجم واقعی نیست، قیمت هم واقعی نیست.")
                RuleLine("۳", "کوین تازه/کوچک را فقط با پولی بخر که از دست دادنش زندگی‌ات را عوض نمی‌کند.")
                RuleLine("۴", "به گروه‌ها و «سیگنال‌های تضمینی» اعتماد نکن؛ کسی که سیگنال می‌دهد، قبل از تو خریده است.")
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "این بخش ابزار مشاهده و آموزش است، نه توصیه‌ی مالی یا سیگنال خرید.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Hint(
            "منبع داده: CoinGecko (کوین‌های برتر بر اساس ارزش بازار). برای اینکه کوین تازه‌ای را " +
                    "به ویجت اضافه کنی، لازم است منبع «کریپتو — CoinGecko» روشن باشد."
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

/** یک کوین در فهرست پامپ */
@Composable
private fun PumpRow(
    coin: PumpScanner.PumpCoin,
    persian: Boolean,
    canAdd: Boolean,
    alreadyAdded: Boolean,
    aiEnabled: Boolean,
    aiReady: Boolean,
    aiBusy: Boolean,
    aiReview: PumpAiReviewer.Review?,
    aiError: String?,
    onAiReview: () -> Unit,
    onOpenNews: (String) -> Unit,
    onAdd: () -> Unit
) {
    val riskColor = when (coin.risk) {
        PumpScanner.Risk.LOW -> Color(0xFF22C55E)
        PumpScanner.Risk.MEDIUM -> Color(0xFFF59E0B)
        PumpScanner.Risk.HIGH -> Color(0xFFF43F5E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                coin.displayName,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                buildString {
                    append(QuoteText.priceWithUnit(coin.price, "$", persian))
                    append(" • ۱ساعت ")
                    append(Format.pct(coin.change1h, persian).ifBlank { "—" })
                    append(" • ۲۴ساعت ")
                    append(Format.pct(coin.change24h, persian).ifBlank { "—" })
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(coin.risk.label, fontSize = 10.sp, color = riskColor)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        if (coin.rank > 0) append("رتبه ${Format.toPersianDigits("${coin.rank}")}")
                        if (coin.volume != null) {
                            if (isNotEmpty()) append(" • ")
                            append("حجم ${Format.volume(coin.volume, persian)}")
                        }
                        if (coin.marketCap != null) {
                            if (isNotEmpty()) append(" • ")
                            append("ارزش بازار ${Format.volume(coin.marketCap, persian)}")
                        }
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val advice = coin.advice
            Text(
                "پیشنهاد: ${advice.recommendation.label}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = riskColor,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "دلیل: ${advice.reason}",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (aiEnabled) {
                OutlinedButton(
                    onClick = onAiReview,
                    enabled = aiReady && !aiBusy,
                    modifier = Modifier.padding(top = 7.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when {
                            aiBusy -> "در حال بررسی و جست‌وجوی خبر…"
                            aiReview != null -> "بررسی دوباره با AI"
                            else -> "بررسی با AI"
                        },
                        fontSize = 11.sp
                    )
                }
                if (!aiReady) {
                    Text(
                        "برای بررسی، آدرس API و نام مدل را بالای صفحه کامل کن.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (!aiError.isNullOrBlank()) {
                    Text(
                        "خطای AI: $aiError",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                aiReview?.let { review ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "نظر AI: ${review.recommendation}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val confidence = review.confidence?.let {
                                val n = if (persian) Format.toPersianDigits(it.toString()) else it.toString()
                                " • اطمینان $n٪"
                            }.orEmpty()
                            Text(
                                "وضعیت: ${review.verdict}$confidence",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "دلیل AI: ${review.reason}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (review.news.isEmpty()) {
                                Text(
                                    if (review.providerSearchRequested)
                                        "خبر مرتبطِ دارای لینک از پاسخ سرویس دریافت نشد."
                                    else "جست‌وجوی خبر درخواست نشده بود.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    "خبرهای مرتبط گزارش‌شده توسط سرویس:",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                review.news.forEach { news ->
                                    TextButton(onClick = { onOpenNews(news.url) }) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(news.title, fontSize = 10.5.sp)
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
        }
        Spacer(Modifier.width(8.dp))
        if (alreadyAdded) {
            Text(
                "✓ در ویجت",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            OutlinedButton(onClick = onAdd, enabled = canAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("ویجت", fontSize = 11.5.sp)
            }
        }
    }
}
