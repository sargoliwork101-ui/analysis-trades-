package com.pulse.market.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AlertRule
import com.pulse.market.data.MarketKind
import com.pulse.market.data.MarketStatus
import com.pulse.market.data.MAX_SYMBOLS
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.SymbolSort
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.data.marketKind
import com.pulse.market.ui.Format
import java.util.Locale

// ═══════════════════ رنگ‌های تم برای سواچ و پیش‌نمایش ═══════════════════

private fun themeBrush(theme: WidgetTheme): Brush = when (theme) {
    WidgetTheme.DARK -> Brush.linearGradient(listOf(Color(0xFF1B2A46), Color(0xFF0B1220)))
    WidgetTheme.LIGHT -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE8EEF6)))
    WidgetTheme.GLASS -> Brush.linearGradient(listOf(Color(0xFF3B4D6E), Color(0xFF151E30)))
    WidgetTheme.AURORA -> Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4F46E5), Color(0xFF0E7490)))
    WidgetTheme.NEON -> Brush.linearGradient(listOf(Color(0xFF2A1546), Color(0xFF0D0518)))
}

private data class PreviewColors(val text: Color, val sub: Color, val rowA: Color, val rowB: Color)

private fun previewColors(theme: WidgetTheme): PreviewColors = when (theme) {
    WidgetTheme.DARK -> PreviewColors(Color(0xFFF1F5F9), Color(0xFF8B9AB1), Color(0xFF2C3E5E), Color(0xFF1E2C48))
    WidgetTheme.LIGHT -> PreviewColors(Color(0xFF0F172A), Color(0xFF64748B), Color(0xFFFFFFFF), Color(0xFFEEF3F9))
    WidgetTheme.GLASS -> PreviewColors(Color(0xFFEAF2FF), Color(0xFF9DB4D4), Color(0x38FFFFFF), Color(0x24FFFFFF))
    WidgetTheme.AURORA -> PreviewColors(Color(0xFFFFFFFF), Color(0xFFDDD6FE), Color(0x38FFFFFF), Color(0x22FFFFFF))
    WidgetTheme.NEON -> PreviewColors(Color(0xFFF5F3FF), Color(0xFF67E8F9), Color(0xFF2E1B4E), Color(0xFF1C1032))
}

private fun themeLabel(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.DARK -> "🌙 تیره"
    WidgetTheme.LIGHT -> "☀️ روشن"
    WidgetTheme.GLASS -> "🫧 شیشه‌ای"
    WidgetTheme.AURORA -> "🌌 شفق قطبی"
    WidgetTheme.NEON -> "⚡ نئون"
}

// ═══════════════════ ۱) منابع داده ═══════════════════

@Composable
fun SourcesCategory(
    allSources: List<SourceDef>,
    selectedIds: List<String>,
    onToggle: (SourceDef) -> Unit,
    onDelete: (SourceDef) -> Unit,
    onAddClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeader("منابع داده", "چند منبع را می‌توانی هم‌زمان روشن کنی — نمادهای همه در یک ویجت")
        RowsCard {
            allSources.forEachIndexed { i, src ->
                if (i > 0) RowDivider()
                SourceRow(
                    src = src,
                    selected = src.id in selectedIds,
                    onClick = { onToggle(src) },
                    onDelete = if (!src.builtIn) ({ onDelete(src) }) else null
                )
            }
        }
        OutlinedButton(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("افزودن منبع دلخواه (آدرس سایت + مسیر داده)", fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun SourceRow(
    src: SourceDef,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text("✓", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                src.title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.5.sp
            )
            if (src.subtitle.isNotEmpty()) {
                Text(
                    src.subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف منبع",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

// ═══════════════════ ۲) نمادها ═══════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolsCategory(
    selectedSources: List<SourceDef>,
    selectedSymbols: List<SymbolDef>,
    tseCustomSymbols: List<SymbolDef>,
    sortMode: SymbolSort = SymbolSort.MANUAL,
    onSortMode: (SymbolSort) -> Unit = {},
    rows: Int = 3,
    onRows: (Int) -> Unit = {},
    onToggle: (SymbolDef, SourceDef) -> Unit,
    onRemoveSymbol: (Int) -> Unit,
    onMoveSymbol: (Int, Int) -> Unit,
    onDeleteTseSymbol: (SymbolDef) -> Unit,
    onOpenSymbolSearch: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {

        // ── نمادهای این ویجت — یک کارت، هر ردیف: ترتیب + حذف ──
        SectionHeader(
            "نمادهای این ویجت",
            "${Format.toPersianDigits("${selectedSymbols.size}")} از ${Format.toPersianDigits("$MAX_SYMBOLS")} نماد • با فلش‌ها جابه‌جا و با 🗑 حذف کن"
        )
        RowsCard {
            if (selectedSymbols.isEmpty()) {
                Text(
                    "هنوز نمادی انتخاب نشده — از گروه‌های پایین اضافه کن.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                selectedSymbols.forEachIndexed { i, sym ->
                    if (i > 0) RowDivider()
                    SelectedSymbolRow(
                        index = i + 1,
                        sym = sym,
                        canUp = i > 0,
                        canDown = i < selectedSymbols.size - 1,
                        onUp = { onMoveSymbol(i, i - 1) },
                        onDown = { onMoveSymbol(i, i + 1) },
                        onRemove = { onRemoveSymbol(i) }
                    )
                }
            }
        }

        // ── مرتب‌سازی نمایش در ویجت ──
        SectionHeader(
            "مرتب‌سازی نمایش در ویجت",
            if (sortMode == SymbolSort.MANUAL) "ترتیب دستی با فلش‌های بالا/پایین"
            else "ترتیب با هر به‌روزرسانی دوباره محاسبه می‌شود"
        )
        RowsCard {
            InnerRow {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SymbolSort.MANUAL to "دستی (فلش‌ها)",
                        SymbolSort.BIGGEST_CHANGE to "بیشترین تغییر",
                        SymbolSort.ALPHABET to "الفبا"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = { onSortMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // ── چند نماد در ویجت دیده شود؟ ──
        SectionHeader(
            "تعداد نمایش در ویجت",
            "چند نماد از فهرست بالا در ویجت دیده شود — اگر ویجت کوچک باشد خودش کم می‌کند"
        )
        RowsCard {
            InnerRow {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..6).forEach { n ->
                        FilterChip(
                            selected = rows == n,
                            onClick = { onRows(n) },
                            label = { Text(Format.toPersianDigits("$n"), fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // ── جستجوی نماد — همه‌ی منابع ──
        SectionHeader(
            "جستجوی نماد",
            "در همه‌ی منابع فعال همین ویجت — بورس، کریپتو، آمریکا، طلا و ارز و منابع دلخواه"
        )
        Button(
            onClick = onOpenSymbolSearch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("جستجو یا افزودن نماد", fontSize = 12.5.sp)
        }
        Hint("بورس: نام/لینک TSETMC • کریپتو و آمریکا: جستجوی آنلاین • بقیه: فهرست منبع + کد دلخواه")

        // ── نمادهای دلخواه بورس من (قابل حذف) ──
        if (tseCustomSymbols.isNotEmpty()) {
            SectionHeader(
                "نمادهای دلخواه بورس من",
                "با 🗑 از فهرست حذف می‌شوند"
            )
            RowsCard {
                tseCustomSymbols.forEachIndexed { i, sym ->
                    if (i > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sym.label, fontSize = 13.sp)
                            Text(sym.code, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteTseSymbol(sym) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف نماد دلخواه",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── نمادهای آماده‌ی هر منبع ──
        selectedSources.forEach { src ->
            val available = if (src.marketKind == MarketKind.TSE) {
                (src.symbols + tseCustomSymbols).distinctBy { it.code }
            } else {
                src.symbols
            }
            SectionHeader(src.title.substringBefore(" —"))
            if (available.isEmpty()) {
                Hint("نماد آماده ندارد — نمادها را در «منبع دلخواه» وارد کن.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { sym ->
                        val selected = selectedSymbols.any {
                            it.code == sym.code && (it.sourceId.isEmpty() || it.sourceId == src.id)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onToggle(sym, src) },
                            label = { Text(sym.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}

/** ردیف نماد انتخاب‌شده داخل کارت مشترک */
@Composable
private fun SelectedSymbolRow(
    index: Int,
    sym: SymbolDef,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(4.dp))
        IndexBadge(index)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(sym.label, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            if (sym.code.isNotBlank()) {
                Text(sym.code, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "بالا", modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "پایین", modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "حذف نماد",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ═══════════════════ ۳) مقادیر نمایشی ═══════════════════

@Composable
fun ValuesCategory(
    cfg: WidgetConfig,
    onChange: (WidgetConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeader("مقادیر نمایشی", "انتخاب کن همین ویجت چه چیزهایی را نشان دهد")
        RowsCard {
            SwitchRow("حجم معاملات", "حجم امروز سهام/صندوق • حجم ۲۴ ساعت کریپتو", cfg.showVolume) {
                onChange(cfg.copy(showVolume = it))
            }
            RowDivider()
            SwitchRow("درصد تغییر", "بج سبز/قرمز رشد و افت", cfg.showChange) {
                onChange(cfg.copy(showChange = it))
            }
            RowDivider()
            SwitchRow("کد نماد", "زیر نام نماد — واحد همیشه زیر عدد قیمت می‌نشیند", cfg.showCode) {
                onChange(cfg.copy(showCode = it))
            }
            RowDivider()
            SwitchRow(
                "نمودار مینیاتوری",
                "روند قیمت کنار هر نماد — برای همه‌ی منابع حتی بورس تهران، از داده‌های ذخیره‌شده روی همین گوشی",
                cfg.showSparkline
            ) {
                onChange(cfg.copy(showSparkline = it))
            }
            if (cfg.showSparkline) {
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تعداد نقاط نمودار", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${Format.toPersianDigits("${cfg.sparkPoints}")} نقطه",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = cfg.sparkPoints.coerceIn(6, 60).toFloat(),
                        onValueChange = { onChange(cfg.copy(sparkPoints = it.toInt().coerceIn(6, 60))) },
                        valueRange = 6f..60f
                    )
                    Hint(
                        "داده‌های نمودار روی گوشی ذخیره می‌شوند و هر ویجت تعداد نقاط دلخواه خودش را " +
                                "از انتهای سری نشان می‌دهد — چند به‌روزرسانی اول طول می‌کشد تا نمودار شکل بگیرد."
                    )
                }
            }
            RowDivider()
            SwitchRow("ساعت بالای ویجت", null, cfg.showTime) {
                onChange(cfg.copy(showTime = it))
            }
            RowDivider()
            SwitchRow(
                "وضعیت بازارها",
                "کنار ساعت: باز/بسته بودن بازارهای همین ویجت — بورس، کریپتو، آمریکا، طلا و ارز",
                cfg.showMarketStatus
            ) {
                onChange(cfg.copy(showMarketStatus = it))
            }
            RowDivider()
            SwitchRow("نوار وضعیت پایین", "وضعیت زنده/دستی + تعداد هشدارها", cfg.showStatus) {
                onChange(cfg.copy(showStatus = it))
            }
            RowDivider()
            SwitchRow("اعداد فشرده", "۶۴٫۲ هزار / 12.4M به‌جای ۶۴٬۲۱۰", cfg.compactNumbers) {
                onChange(cfg.copy(compactNumbers = it))
            }
        }
    }
}

// ═══════════════════ ۴) ظاهر و فونت (با پیش‌نمایش زنده) ═══════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookCategory(
    cfg: WidgetConfig,
    onChange: (WidgetConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeader("ظاهر ویجت", "پیش‌نمایش زنده — هر تغییر را همین‌جا ببین")

        // ── پیش‌نمایش زنده‌ی ویجت ──
        WidgetPreviewCard(cfg)

        RowsCard {
            // عنوان دلخواه
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = cfg.title,
                    onValueChange = { onChange(cfg.copy(title = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("عنوان دلخواه ویجت", fontSize = 12.sp) },
                    placeholder = { Text("مثلاً: سبد من / طلا و ارز", fontSize = 12.sp) },
                    singleLine = true
                )
            }
            RowDivider()

            // تم — سواچ رنگی
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("تم ویجت", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WidgetTheme.entries.forEach { t ->
                        ThemeSwatch(
                            theme = t,
                            selected = cfg.theme == t,
                            onClick = { onChange(cfg.copy(theme = t)) }
                        )
                    }
                }
            }
            RowDivider()

            // اندازه‌ی فونت
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اندازه‌ی فونت", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "×${Format.toPersianDigits(String.format(Locale.US, "%.2f", cfg.fontScale))}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = cfg.fontScale,
                    onValueChange = { onChange(cfg.copy(fontScale = it)) },
                    valueRange = 0.75f..1.5f
                )
                Hint("فونت‌ها با اندازه‌ی ویجت هم خودکار بزرگ و کوچک می‌شوند؛ این ضریب روی همه‌ی متن‌ها اعمال می‌شود.")
            }
            RowDivider()

            // تعداد ردیف‌ها
            SettingRow("تعداد ردیف‌ها", "در ویجت کوچک خودکار کمتر نمایش داده می‌شود") {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..6).forEach { n ->
                        FilterChip(
                            selected = cfg.rows == n,
                            onClick = { onChange(cfg.copy(rows = n)) },
                            label = { Text(Format.toPersianDigits("$n"), fontSize = 12.sp) }
                        )
                    }
                }
            }
            RowDivider()
            SwitchRow("جداکننده‌ی رنگی ردیف‌ها", "هر نماد کارت جدا با رنگ متناوب دارد", cfg.rowSeparation) {
                onChange(cfg.copy(rowSeparation = it))
            }
            RowDivider()
            SwitchRow("اعداد فارسی", null, cfg.persianDigits) {
                onChange(cfg.copy(persianDigits = it))
            }
        }
        Hint("اندازه‌ی ویجت با نگه‌داشتن روی آن قابل تغییر است — محتوا و فونت با فضای واقعی تنظیم می‌شود")
    }
}

/** سواچ رنگی انتخاب تم */
@Composable
private fun ThemeSwatch(
    theme: WidgetTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(58.dp)
                .height(40.dp)
                .background(themeBrush(theme), RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Spacer(Modifier.height(5.dp))
        Text(
            themeLabel(theme),
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** پیش‌نمایش زنده‌ی ویجت — دقیقاً با تنظیمات فعلی (تم، فونت، مقادیر، جداکننده) */
@Composable
private fun WidgetPreviewCard(cfg: WidgetConfig) {
    val pal = previewColors(cfg.theme)
    val scale = cfg.fontScale.coerceIn(0.75f, 1.5f)
    val persian = cfg.persianDigits

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeBrush(cfg.theme), RoundedCornerShape(18.dp))
                .border(1.dp, pal.sub.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // سرصفحه — همان چیزی که ویجت واقعی نشان می‌دهد: ساعت + وضعیت بازارها
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        txt(cfg.title.ifBlank { "پیش‌نمایش" }, persian),
                        color = pal.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12f * scale).sp
                    )
                    Spacer(Modifier.weight(1f))
                    val headerBits = buildList {
                        if (cfg.showTime) add(txt("به‌روز ۱۴:۳۲", persian))
                        if (cfg.showMarketStatus)
                            add(txt(MarketStatus.headerFor(cfg.activeSourceIds, short = true), persian))
                    }
                    if (headerBits.isNotEmpty()) {
                        Text(
                            headerBits.joinToString(" • "),
                            color = pal.sub,
                            fontSize = (9.5f * scale).sp,
                            maxLines = 1
                        )
                    }
                }

                PreviewRow(cfg, "بیت‌کوین", 64210.0, 2.31, 2.84e10, pal, scale, first = true)
                PreviewRow(cfg, "اتریوم", 3180.0, -1.05, 1.21e10, pal, scale, first = false)
            }
        }
    }
}

@Composable
private fun PreviewRow(
    cfg: WidgetConfig,
    label: String,
    price: Double,
    change: Double,
    volume: Double,
    pal: PreviewColors,
    scale: Float,
    first: Boolean
) {
    val bg = if (first) pal.rowA else pal.rowB
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cfg.rowSeparation) Modifier.background(bg, RoundedCornerShape(10.dp)) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Color(0xFF22C55E), CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(txt(label, cfg.persianDigits), color = pal.text, fontWeight = FontWeight.Bold, fontSize = (12f * scale).sp)
            }
            val subParts = mutableListOf<String>()
            if (cfg.showCode) subParts += "BTC"
            if (cfg.showVolume) subParts += "حجم ${Format.volume(volume, cfg.persianDigits)}"
            if (subParts.isNotEmpty()) {
                Text(
                    txt(subParts.joinToString(" • "), cfg.persianDigits),
                    color = pal.sub,
                    fontSize = (9f * scale).sp
                )
            }
        }
        // نمودار مینیاتوری — در پیش‌نمایش هم دیده شود تا کاربر بداند چه چیزی فعال است
        if (cfg.showSparkline) {
            Spacer(Modifier.width(8.dp))
            PreviewSpark(
                up = change > 0,
                modifier = Modifier.size(width = 36.dp, height = 16.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.price(price, cfg.persianDigits, cfg.compactNumbers),
                color = pal.text,
                fontWeight = FontWeight.Bold,
                fontSize = (13.5f * scale).sp
            )
            if (cfg.showChange) {
                Text(
                    Format.pct(change, cfg.persianDigits),
                    color = if (change > 0) Color(0xFF22C55E) else Color(0xFFF43F5E),
                    fontWeight = FontWeight.Bold,
                    fontSize = (9f * scale).sp
                )
            }
        }
    }
}

/** نمودار مینیاتوری پیش‌نمایش — خط ساده‌ی صعودی/نزولی هم‌رنگ بج تغییر */
@Composable
private fun PreviewSpark(up: Boolean, modifier: Modifier = Modifier) {
    val color = if (up) Color(0xFF22C55E) else Color(0xFFF43F5E)
    Canvas(modifier = modifier) {
        // الگوی نمونه: صعودی/نزولی — مثل ویجت واقعی رنگش با جهت تغییر می‌شود
        val fractions = listOf(0.78f, 0.60f, 0.68f, 0.48f, 0.55f, 0.32f)
            .map { if (up) 1f - it else it }
        val path = Path()
        fractions.forEachIndexed { i, f ->
            val x = size.width * i / (fractions.size - 1)
            val y = size.height * f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path, color = color,
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
    }
}

private fun txt(s: String, persian: Boolean) = if (persian) Format.toPersianDigits(s) else s

// ═══════════════════ ۵) به‌روزرسانی ═══════════════════

@Composable
fun UpdateCategory(
    cfg: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    onLiveToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeader("به‌روزرسانی زنده", "سرویس پس‌زمینه قیمت‌ها را خودکار تازه می‌کند")
        RowsCard {
            SwitchRow("به‌روزرسانی خودکار", "برای هشدارهای قیمت هم لازم است", cfg.liveService, onLiveToggle)
            RowDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "فاصله‌ی به‌روزرسانی: ${Format.toPersianDigits("${cfg.intervalSec}")} ثانیه",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = cfg.intervalSec.toFloat(),
                    onValueChange = { onChange(cfg.copy(intervalSec = it.toInt())) },
                    valueRange = 5f..120f,
                    steps = 22,
                    enabled = cfg.liveService
                )
            }
        }
        Hint("کریپتو و سهام آمریکا: ۱۰ تا ۳۰ ثانیه • بورس تهران: ۶۰ ثانیه (داده‌ی TSETMC با تأخیر می‌آید)")
        Hint("بدون اینترنت یا بیرون از بازه‌ی بالا ویجت خالی نمی‌شود: آخرین قیمت‌ها روی صفحه می‌ماند و فقط چراغ کنار نمادها قرمز می‌شود.")

        // ── بازه‌ی ساعتی تازه‌سازی — صرفه‌جویی در مصرف اینترنت ──
        SectionHeader(
            "زمان‌بندی به‌روزرسانی",
            "فقط در این ساعت‌ها از اینترنت تازه می‌شود — بیرون از بازه آخرین قیمت می‌ماند تا بسته‌ی نت زود تمام نشود"
        )
        RowsCard {
            SwitchRow(
                title = "فقط در بازه‌ی مشخص تازه شود",
                desc = "مثلاً فقط ۹ صبح تا ۸ شب رفرش کند",
                checked = cfg.refreshWindowEnabled,
                onChange = { onChange(cfg.copy(refreshWindowEnabled = it)) }
            )
            if (cfg.refreshWindowEnabled) {
                RowDivider()
                InnerRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimePickButton("از ساعت", cfg.refreshFromMinute, Modifier.weight(1f)) {
                            onChange(cfg.copy(refreshFromMinute = it))
                        }
                        TimePickButton("تا ساعت", cfg.refreshToMinute, Modifier.weight(1f)) {
                            onChange(cfg.copy(refreshToMinute = it))
                        }
                    }
                    Text(
                        if (cfg.refreshFromMinute == cfg.refreshToMinute) "دو ساعت برابر یعنی شبانه‌روزی (بدون محدودیت)"
                        else "بازه می‌تواند شب‌گذر هم باشد — مثلاً ۲۲ شب تا ۷ صبح",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

// ═══════════════════ ۶) هشدارها ═══════════════════

@Composable
fun AlertsCategory(
    cfg: WidgetConfig,
    sources: List<SourceDef> = emptyList(),
    snoozeUntil: Long = 0L,
    onSnooze: (Int) -> Unit = {},
    onCancelSnooze: () -> Unit = {},
    onToggleAlert: (AlertRule, Boolean) -> Unit,
    onEditAlert: (AlertRule) -> Unit,
    onDeleteAlert: (AlertRule) -> Unit,
    onAddAlert: () -> Unit,
    onTestNotification: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── خواب موقت: نوتیف‌ها فعلاً می‌خوابند ──
        SectionHeader(
            "خواب موقت هشدارها",
            "برای میتینگ یا خواب — قیمت‌ها و ویجت مثل قبل تازه می‌شوند"
        )
        RowsCard {
            InnerRow {
                if (snoozeUntil > System.currentTimeMillis()) {
                    Text(
                        "🔕 هشدارها تا ساعت ${Format.time(snoozeUntil)} خواب‌اند",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "در این بازه هیچ نوتیفی نمی‌آید",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onCancelSnooze,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔 بیدار کن — هشدارها روشن", fontSize = 12.5.sp)
                    }
                } else {
                    Text(
                        "نوتیف‌ها برای مدتی می‌خوابند؛ با تمام شدن زمان خودشان بیدار می‌شوند",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15 to "۱۵ دقیقه", 30 to "۳۰ دقیقه", 60 to "۱ ساعت").forEach { (m, label) ->
                            OutlinedButton(
                                onClick = { onSnooze(m) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        SectionHeader("هشدار قیمت", "با هر بار تازه شدن قیمت‌ها بررسی می‌شود — حالت زنده را روشن نگه دار")

        if (cfg.alerts.isEmpty()) {
            InfoCard("هنوز هشداری ثبت نشده. مثلاً: «وقتی بیت‌کوین از ۱۰۰٬۰۰۰ گذشت به من خبر بده».")
        } else {
            cfg.alerts.forEach { rule ->
                // واحدِ نمادِ هشدار از تعریف منبع — تا شرط هشدار مثل ویجت، همراه واحد دیده شود
                val ruleUnit = sources.firstOrNull { it.id == rule.sourceId }?.unit.orEmpty()
                AlertRuleCard(
                    rule = rule,
                    unit = ruleUnit,
                    persian = cfg.persianDigits,
                    onToggle = { on -> onToggleAlert(rule, on) },
                    onEdit = { onEditAlert(rule) },
                    onDelete = { onDeleteAlert(rule) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onAddAlert, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("افزودن هشدار", fontSize = 12.5.sp)
            }
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("تست نوتیف", fontSize = 12.5.sp)
            }
        }
    }
}

/** کارت یک قانون هشدار — شرط با واحدِ نماد (ریال/تومان/$) نشان داده می‌شود */
@Composable
private fun AlertRuleCard(
    rule: AlertRule,
    unit: String,
    persian: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🔔 ${rule.symbolLabel}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    AlertRule.conditionText(rule.condition, rule.threshold, unit, persian),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    rule.scheduleText(persian) +
                            if (rule.cooldownMin > 1) " • هر ${Format.toPersianDigits(rule.cooldownMin.toString())} دقیقه" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف هشدار",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

// ═══════════════════ ۷) بکاپ و بازگردانی ═══════════════════

/** بخش بکاپ: خروجی JSON از همه‌ی تنظیمات + بازیابی از فایل */
@Composable
fun BackupCategory(
    busy: Boolean,
    result: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(
            "پشتیبان‌گیری",
            "همه‌ی تنظیمات — ویجت‌ها، الگو، منابع دلخواه، نمادهای بورس و هشدارها — در یک فایل JSON"
        )

        RowsCard {
            SettingRow(
                title = "خروجی گرفتن",
                desc = "فایل بکاپ را هر جا دوست داری ذخیره کن (گوگل درایو/تلگرام/حافظه)"
            ) {
                Button(onClick = onExport, enabled = !busy) {
                    Text("💾 ذخیره", fontSize = 12.5.sp)
                }
            }
            RowDivider()
            SettingRow(
                title = "بازیابی",
                desc = "فایل بکاپ نبض بازار را انتخاب کن — تنظیمات فعلی جایگزین می‌شود"
            ) {
                OutlinedButton(onClick = onImport, enabled = !busy) {
                    Text("📂 انتخاب فایل", fontSize = 12.5.sp)
                }
            }
        }

        if (result.isNotBlank()) {
            InfoCard(result)
        }

        Hint("بکاپ فقط تنظیمات برنامه است و هیچ اطلاعات شخصی یا رمزی داخلش نیست.")
    }
}

// ───────────── ابزار انتخاب ساعت ─────────────

/** انتخاب ساعت با دیالوگ استاندارد اندروید — برای بازه‌های زمانی تنظیمات */
@Composable
private fun TimePickButton(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, h, m -> onChange(h * 60 + m) },
                    minuteOfDay / 60,
                    minuteOfDay % 60,
                    true
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60))
        }
    }
}
