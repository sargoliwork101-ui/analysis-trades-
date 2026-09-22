package com.pulse.market.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AlertRule
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.ui.Format

// ═══════════════════ ۱) منابع داده (چند انتخابی) ═══════════════════

@Composable
fun SourcesCategory(
    allSources: List<SourceDef>,
    selectedIds: List<String>,
    onToggle: (SourceDef) -> Unit,
    onDelete: (SourceDef) -> Unit,
    onAddClick: () -> Unit
) {
    CategoryCard(
        title = "منابع داده",
        subtitle = "چند منبع را می‌توانی هم‌زمان روشن کنی — نمادهای همه‌ی منابع در یک ویجت"
    ) {
        allSources.forEach { src ->
            SourceCard(
                src = src,
                selected = src.id in selectedIds,
                onClick = { onToggle(src) },
                onDelete = if (!src.builtIn) ({ onDelete(src) }) else null
            )
        }
        OutlinedButton(onClick = onAddClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("افزودن منبع دلخواه (آدرس سایت + مسیر داده)", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SourceCard(
    src: SourceDef,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selected) "☑" else "☐",
                fontSize = 16.sp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(5.dp))
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف منبع")
                }
            }
        }
    }
}

// ═══════════════════ ۲) نمادها (گروه‌بندی بر اساس منبع) ═══════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolsCategory(
    selectedSources: List<SourceDef>,
    selectedSymbols: List<SymbolDef>,
    tseCustomSymbols: List<SymbolDef>,
    onToggle: (SymbolDef, SourceDef) -> Unit,
    onOpenTseSearch: () -> Unit
) {
    val isTseSelected = selectedSources.any { it.id == "tse_tsetmc" }

    CategoryCard(
        title = "نمادهای ویجت",
        subtitle = "نمادها را از هر منبعی که روشن است انتخاب کن • حداکثر ۴ نماد"
    ) {
        if (isTseSelected) {
            Button(
                onClick = onOpenTseSearch,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("🔍 جستجو در بورس تهران یا افزودن با لینک TSETMC", fontSize = 12.sp)
            }
            Hint("هر نماد دلخواه (صندوق‌ها، اهرم، عیار، طلا، …) یا لینک صفحه‌ی tsetmc.com را می‌توانی اضافه کنی.")
        }

        Text(
            "انتخاب‌شده: ${selectedSymbols.size} از ۴ نماد",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        selectedSources.forEach { src ->
            val available = if (src.id == "tse_tsetmc") {
                (src.symbols + tseCustomSymbols).distinctBy { it.code }
            } else {
                src.symbols
            }

            Text(
                src.title.substringBefore(" —"),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
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

// ═══════════════════ ۳) ظاهر و سرعت ═══════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookCategory(
    cfg: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    onLiveToggle: (Boolean) -> Unit
) {
    CategoryCard(title = "به‌روزرسانی زنده", subtitle = "سرویس پس‌زمینه قیمت‌ها را خودکار تازه می‌کند") {
        SettingRow("به‌روزرسانی خودکار", "برای هشدارهای قیمت هم لازم است") {
            Switch(checked = cfg.liveService, onCheckedChange = onLiveToggle)
        }
        Text(
            "فاصله‌ی به‌روزرسانی: ${cfg.intervalSec} ثانیه",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = cfg.intervalSec.toFloat(),
            onValueChange = { onChange(cfg.copy(intervalSec = it.toInt())) },
            valueRange = 5f..120f,
            steps = 22,
            enabled = cfg.liveService
        )
        Hint("کریپتو و سهام آمریکا: ۱۰ تا ۳۰ ثانیه • بورس تهران: ۶۰ ثانیه (داده‌ی TSETMC با تأخیر می‌آید)")
    }

    CategoryCard(title = "ظاهر ویجت") {
        SettingRow("تعداد ردیف‌ها (${cfg.rows})", "در ویجت کوچک خودکار کمتر نمایش داده می‌شود") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    FilterChip(
                        selected = cfg.rows == n,
                        onClick = { onChange(cfg.copy(rows = n)) },
                        label = { Text(Format.toPersianDigits("$n"), fontSize = 12.sp) }
                    )
                }
            }
        }
        SettingRow("نمودار مینیاتوری", "در ویجت باریک خودکار پنهان می‌شود") {
            Switch(
                checked = cfg.showSparkline,
                onCheckedChange = { onChange(cfg.copy(showSparkline = it)) }
            )
        }
        SettingRow("اعداد فارسی") {
            Switch(
                checked = cfg.persianDigits,
                onCheckedChange = { onChange(cfg.copy(persianDigits = it)) }
            )
        }
        Text("تم ویجت", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WidgetTheme.DARK to "🌙 تیره",
                WidgetTheme.LIGHT to "☀️ روشن",
                WidgetTheme.NEON to "🌃 نئون",
                WidgetTheme.AURORA to "🌌 شفق",
                WidgetTheme.MOCHA to "☕ موکا"
            ).forEach { (t, label) ->
                FilterChip(
                    selected = cfg.theme == t,
                    onClick = { onChange(cfg.copy(theme = t)) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Hint(
            when (cfg.theme) {
                WidgetTheme.DARK -> "تم تیره‌ی کلاسیک"
                WidgetTheme.LIGHT -> "روشن و تمیز"
                WidgetTheme.NEON -> "نئون سایبرپانک با اکسنت فیروزه‌ای — ترند این روزها"
                WidgetTheme.AURORA -> "شفق قطبی بنفش و آبی — ترند این روزها"
                WidgetTheme.MOCHA -> "گرم و خودمانی به سبک موکا — ترند این روزها"
            } + " • اندازه‌ی ویجت با نگه‌داشتن روی آن قابل تغییر است"
        )
    }
}

// ═══════════════════ ۴) هشدارها ═══════════════════

@Composable
fun AlertsCategory(
    cfg: WidgetConfig,
    onToggleAlert: (AlertRule, Boolean) -> Unit,
    onEditAlert: (AlertRule) -> Unit,
    onDeleteAlert: (AlertRule) -> Unit,
    onAddAlert: () -> Unit,
    onTestNotification: () -> Unit
) {
    CategoryCard(
        title = "هشدار قیمت",
        subtitle = "با هر بار تازه شدن قیمت‌ها بررسی می‌شود — حالت زنده را روشن نگه دار"
    ) {
        if (cfg.alerts.isEmpty()) {
            InfoCard("هنوز هشداری ثبت نشده. مثلاً: «وقتی بیت‌کوین از ۱۰۰٬۰۰۰ گذشت به من خبر بده».")
        } else {
            cfg.alerts.forEach { rule ->
                AlertRuleCard(
                    rule = rule,
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
                Spacer(Modifier.padding(3.dp))
                Text("افزودن هشدار", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(Modifier.padding(3.dp))
                Text("تست نوتیف", fontSize = 12.sp)
            }
        }
    }
}

/** کارت یک قانون هشدار در لیست */
@Composable
private fun AlertRuleCard(
    rule: AlertRule,
    persian: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    AlertRule.conditionText(rule.condition, rule.threshold, "", persian),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    rule.scheduleText(persian) +
                            if (rule.cooldownMin > 1) " • هر ${Format.toPersianDigits(rule.cooldownMin.toString())} دقیقه" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف هشدار")
            }
        }
    }
}
