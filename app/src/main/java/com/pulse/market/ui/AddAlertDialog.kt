package com.pulse.market.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AlertCondition
import com.pulse.market.data.AlertRule
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.ui.settings.DialogActionsRow
import com.pulse.market.ui.settings.DialogShell
import com.pulse.market.ui.settings.InnerRow
import com.pulse.market.ui.settings.RowDivider
import com.pulse.market.ui.settings.RowsCard
import com.pulse.market.ui.settings.SectionHeader
import com.pulse.market.ui.settings.SwitchRow

/**
 * پنجره‌ی ساخت/ویرایش هشدار قیمت — با همان زبان طراحی تنظیمات:
 * هر بخش یک کارت یکپارچه با ردیف‌های جداشده‌ی خط‌دار.
 * شرط (بالاتر/پایین‌تر/درصد) + بازه‌ی زمانی و روزهای فعال + فاصله‌ی ضد‌اسپم.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAlertDialog(
    sources: List<SourceDef>,
    existing: AlertRule?,
    widgetSymbols: List<SymbolDef> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (AlertRule) -> Unit
) {
    // نمادهای قابل هشدار برای هر منبع: اول نمادهایی که کاربر واقعاً در ویجت دارد
    // (حتی جستجوشده‌ها مثل صندوق‌ها)، بعد نمادهای آماده‌ی خود منبع — بدون تکرار.
    // این‌طور هشدار روی «همه‌ی» نمادهای دیده‌شده ممکن است، نه فقط لیست ثابت منبع.
    val symbolsPerSource: List<Pair<SourceDef, List<SymbolDef>>> = sources.map { src ->
        val mine = widgetSymbols.filter { it.sourceId == src.id }
        val rest = src.symbols.filter { s -> mine.none { it.code == s.code } }
            .map { it.copy(sourceId = src.id) }
        src to (mine + rest)
    }
    val firstSymbol = symbolsPerSource.firstNotNullOfOrNull { (_, list) -> list.firstOrNull() }

    val manualMode = symbolsPerSource.all { it.second.isEmpty() }

    var symbolCode by remember {
        mutableStateOf(existing?.symbolCode ?: firstSymbol?.code ?: "")
    }
    var symbolLabel by remember {
        mutableStateOf(existing?.symbolLabel ?: firstSymbol?.label ?: "")
    }
    var symbolSourceId by remember {
        mutableStateOf(existing?.sourceId ?: firstSymbol?.sourceId ?: sources.firstOrNull()?.id ?: "")
    }
    var manualCode by remember { mutableStateOf(if (manualMode) existing?.symbolCode ?: "" else "") }
    var manualLabel by remember { mutableStateOf(if (manualMode) existing?.symbolLabel ?: "" else "") }

    var condition by remember { mutableStateOf(existing?.condition ?: AlertCondition.ABOVE) }
    var threshold by remember {
        mutableStateOf(existing?.threshold?.let { Format.price(it, false).replace(",", "") } ?: "")
    }

    // پیش‌فرضِ هشدار تازه: همیشه فعال — چونِ محدودیت ساعت بورس برای کریپتو/آمریکا/طلا
    // یعنی هشدارِ شب‌ها و جمعه‌ها بی‌صدا از کار می‌افتاد؛ کاربر خودش اگر خواست محدود کند
    var scheduleEnabled by remember { mutableStateOf(existing?.scheduleEnabled ?: false) }
    var fromMinute by remember { mutableStateOf(existing?.fromMinute ?: 9 * 60) }
    var toMinute by remember { mutableStateOf(existing?.toMinute ?: 17 * 60) }
    var days by remember { mutableStateOf(existing?.days ?: setOf(0, 1, 2, 3, 4)) }

    var cooldown by remember { mutableStateOf(existing?.cooldownMin ?: 30) }
    var onlyOnCross by remember { mutableStateOf(existing?.onlyOnCross ?: true) }

    val canSave = (if (manualMode) manualCode.isNotBlank() else symbolCode.isNotBlank()) &&
            (threshold.replace(",", "").toDoubleOrNull() != null)

    // واحدِ خود نماد بر واحد منبع مقدم است (مثلاً «انس طلا» داخل TGJU دلار است،
    // در حالی که بقیه‌ی نمادهای همان منبع تومان‌اند).
    val selectedSource = sources.firstOrNull { it.id == symbolSourceId } ?: sources.firstOrNull()
    val selectedSymbol = symbolsPerSource.firstOrNull { it.first.id == selectedSource?.id }
        ?.second?.firstOrNull { it.code == symbolCode }
    val selectedUnit = selectedSymbol?.unit?.takeIf { it.isNotBlank() }
        ?: selectedSource?.unit.orEmpty()

    DialogShell(
        icon = Icons.Default.NotificationsActive,
        tint = Color(0xFFF43F5E),
        title = if (existing == null) "هشدار جدید" else "ویرایش هشدار",
        subtitle = "با هر به‌روزرسانی قیمت بررسی می‌شود",
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ─────────── ۱) نماد و شرط ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("نماد و شرط")
                RowsCard {
                    if (manualMode) {
                        InnerRow {
                            OutlinedTextField(
                                value = manualCode, onValueChange = { manualCode = it },
                                label = { Text("کد نماد (مثل فولاد یا AAPL)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = manualLabel, onValueChange = { manualLabel = it },
                                label = { Text("نام نمایشی (اختیاری)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    } else {
                        InnerRow {
                            symbolsPerSource.forEach { (src, syms) ->
                                if (syms.isNotEmpty()) {
                                    Text(
                                        src.title.substringBefore(" —"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        syms.forEach { sym ->
                                            val selected = symbolCode == sym.code && symbolSourceId == src.id
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    symbolCode = sym.code
                                                    symbolLabel = sym.label
                                                    symbolSourceId = src.id
                                                },
                                                label = { Text(sym.label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    RowDivider()

                    InnerRow {
                        Text(
                            "شرط فعال شدن",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                AlertCondition.ABOVE to "قیمت بالاتر از",
                                AlertCondition.BELOW to "قیمت پایین‌تر از",
                                AlertCondition.PCT_UP to "رشد بیش از ٪",
                                AlertCondition.PCT_DOWN to "افت بیش از ٪"
                            ).forEach { (c, label) ->
                                FilterChip(
                                    selected = condition == c,
                                    onClick = { condition = c },
                                    label = { Text(label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = threshold, onValueChange = { threshold = it },
                            label = {
                                Text(
                                    when (condition) {
                                        AlertCondition.ABOVE, AlertCondition.BELOW ->
                                            if (selectedUnit.isNotEmpty()) "عدد قیمت به $selectedUnit (مثلاً 6420)"
                                            else "عدد قیمت (مثلاً 6420)"
                                        else -> "درصد (مثلاً 5)"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ─────────── ۲) زمان‌بندی ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("زمان‌بندی هشدار")
                RowsCard {
                    SwitchRow(
                        title = "فقط در بازه‌ی زمانی فعال باشد",
                        desc = "خارج از این ساعت‌ها هشدار بی‌صداست",
                        checked = scheduleEnabled,
                        onChange = { scheduleEnabled = it }
                    )

                    if (scheduleEnabled) {
                        RowDivider()
                        InnerRow {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimeButton("از ساعت", fromMinute, Modifier.weight(1f)) { fromMinute = it }
                                TimeButton("تا ساعت", toMinute, Modifier.weight(1f)) { toMinute = it }
                            }
                            Text(
                                if (fromMinute == toMinute) "بازه: شبانه‌روزی (۲۴ ساعته)"
                                else "بازه‌ی مؤثر: ${AlertRule.hhmm(fromMinute)} تا ${AlertRule.hhmm(toMinute)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { fromMinute = 9 * 60; toMinute = 12 * 60 + 30; days = setOf(0, 1, 2, 3, 4, 5) }) {
                                    Text("ساعات بورس تهران", fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { fromMinute = 0; toMinute = 0; days = setOf(0, 1, 2, 3, 4, 5, 6) }) {
                                    Text("شبانه‌روزی (کریپتو)", fontSize = 11.sp)
                                }
                            }
                            Text(
                                "روزهای فعال",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (0..6).forEach { d ->
                                    FilterChip(
                                        selected = d in days,
                                        onClick = { days = if (d in days) days - d else days + d },
                                        label = { Text(AlertRule.dayName(d)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─────────── ۳) دریافت نوتیف ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("دریافت نوتیف")
                RowsCard {
                    InnerRow {
                        Text(
                            "حداقل فاصله‌ی بین دو نوتیف",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "هر بار", 5 to "۵ دقیقه", 15 to "۱۵ دقیقه", 30 to "۳۰ دقیقه", 60 to "۱ ساعت", 180 to "۳ ساعت")
                                .forEach { (m, label) ->
                                    FilterChip(
                                        selected = cooldown == m,
                                        onClick = { cooldown = m },
                                        label = { Text(label) }
                                    )
                                }
                        }
                    }
                    RowDivider()
                    SwitchRow(
                        title = "فقط لحظه‌ی عبور از حد",
                        desc = "تا قیمت برنگردد و دوباره عبور نکند، نوتیف تکرار نمی‌شود",
                        checked = onlyOnCross,
                        onChange = { onlyOnCross = it }
                    )
                }
            }
        }

        DialogActionsRow(
            cancelText = "انصراف",
            confirmText = "ذخیره",
            onCancel = onDismiss,
            confirmEnabled = canSave,
            onConfirm = {
                val cleanCode = if (manualMode) manualCode.trim() else symbolCode
                val cleanLabel = when {
                    manualMode -> manualLabel.trim().ifBlank { cleanCode }
                    symbolLabel.isNotBlank() -> symbolLabel
                    else -> cleanCode
                }
                val cleanSource = when {
                    manualMode -> sources.firstOrNull()?.id ?: ""
                    symbolSourceId.isNotBlank() -> symbolSourceId
                    else -> sources.firstOrNull { src -> src.symbols.any { it.code == cleanCode } }?.id
                        ?: sources.firstOrNull()?.id ?: ""
                }
                onSave(
                    AlertRule(
                        id = existing?.id ?: "alert_" + System.currentTimeMillis(),
                        symbolCode = cleanCode,
                        symbolLabel = cleanLabel,
                        sourceId = cleanSource,
                        condition = condition,
                        threshold = threshold.replace(",", "").toDoubleOrNull() ?: 0.0,
                        scheduleEnabled = scheduleEnabled,
                        fromMinute = fromMinute,
                        toMinute = toMinute,
                        days = if (scheduleEnabled) days else setOf(0, 1, 2, 3, 4, 5, 6),
                        cooldownMin = cooldown,
                        onlyOnCross = onlyOnCross,
                        enabled = existing?.enabled ?: true
                    )
                )
            }
        )
    }
}

/** دکمه‌ی انتخاب ساعت با دیالوگ استاندارد اندروید */
@Composable
private fun TimeButton(label: String, minuteOfDay: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
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
            Text(AlertRule.hhmm(minuteOfDay))
        }
    }
}
