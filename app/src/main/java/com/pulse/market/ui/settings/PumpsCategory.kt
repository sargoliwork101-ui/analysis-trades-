package com.pulse.market.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.MAX_SYMBOLS
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
    onChange: (WidgetConfig) -> Unit,
    onAddSymbol: (SymbolDef) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scan by remember { mutableStateOf<PumpScanner.PumpScan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

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
            busy = false
            note = res.error?.let { "⚠️ اسکن تازه نگرفت — $it (فهرست قبلی نمایش داده می‌شود)" } ?: ""
        }
    }

    val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
    val shown = scan?.matches.orEmpty()

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

        // ── ۳) نتیجه ──
        val summary = when {
            scan == null -> "برای دیدن نتیجه، «اسکن تازه» را بزن."
            shown.isEmpty() -> "الان در ${Format.toPersianDigits("${scan!!.universe}")} کوین برتر، هیچ کوینی " +
                    "بیشتر از ${Format.toPersianDigits("${scan!!.minChange.toInt()}")}٪ رشد ۲۴ ساعته ندارد — " +
                    "یعنی بازار فعلاً پامپ‌دار نیست (خودش یک خبر خوب است)."

            else -> "${Format.toPersianDigits("${shown.size}")} کوین از " +
                    "${Format.toPersianDigits("${scan!!.coins.size}")} کوین بررسی‌شده، بالای " +
                    "${Format.toPersianDigits("${scan!!.minChange.toInt()}")}٪ رشد ۲۴ ساعته‌اند."
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
