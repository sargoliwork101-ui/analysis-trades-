package com.pulse.market.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.AlertCondition
import com.pulse.market.data.AlertRule
import com.pulse.market.data.SourceDef

/**
 * پنجره‌ی ساخت/ویرایش هشدار قیمت.
 * نمادها از همه‌ی منابع روشن (چند منبعی) نمایش داده می‌شوند؛
 * شرط (بالاتر/پایین‌تر/درصد) + بازه‌ی زمانی و روزهای فعال + فاصله‌ی ضد‌اسپم.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAlertDialog(
    sources: List<SourceDef>,
    existing: AlertRule?,
    onDismiss: () -> Unit,
    onSave: (AlertRule) -> Unit
) {
    val manualMode = sources.all { it.symbols.isEmpty() }

    var symbolCode by remember {
        mutableStateOf(existing?.symbolCode ?: sources.firstOrNull()?.symbols?.firstOrNull()?.code ?: "")
    }
    var symbolLabel by remember {
        mutableStateOf(
            existing?.symbolLabel
                ?: sources.firstOrNull()?.symbols?.firstOrNull()?.label
                ?: ""
        )
    }
    var symbolSourceId by remember {
        mutableStateOf(
            existing?.sourceId ?: sources.firstOrNull()?.id ?: ""
        )
    }
    var manualCode by remember { mutableStateOf(if (manualMode) existing?.symbolCode ?: "" else "") }
    var manualLabel by remember { mutableStateOf(if (manualMode) existing?.symbolLabel ?: "" else "") }

    var condition by remember { mutableStateOf(existing?.condition ?: AlertCondition.ABOVE) }
    var threshold by remember {
        mutableStateOf(existing?.threshold?.let { Format.price(it, false).replace(",", "") } ?: "")
    }

    var scheduleEnabled by remember { mutableStateOf(existing?.scheduleEnabled ?: true) }
    var fromMinute by remember { mutableStateOf(existing?.fromMinute ?: 9 * 60) }
    var toMinute by remember { mutableStateOf(existing?.toMinute ?: 17 * 60) }
    var days by remember { mutableStateOf(existing?.days ?: setOf(0, 1, 2, 3, 4)) }

    var cooldown by remember { mutableStateOf(existing?.cooldownMin ?: 30) }
    var onlyOnCross by remember { mutableStateOf(existing?.onlyOnCross ?: true) }

    val canSave = (if (manualMode) manualCode.isNotBlank() else symbolCode.isNotBlank()) &&
            (threshold.replace(",", "").toDoubleOrNull() != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "هشدار جدید" else "ویرایش هشدار") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── نماد (گروه‌بندی بر اساس منبع) ──
                Text("کدام نماد؟", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (manualMode) {
                    OutlinedTextField(
                        value = manualCode, onValueChange = { manualCode = it },
                        label = { Text("کد نماد (مثل فولاد یا AAPL)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = manualLabel, onValueChange = { manualLabel = it },
                        label = { Text("نام نمایشی (اختیاری)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                } else {
                    sources.forEach { src ->
                        if (src.symbols.isNotEmpty()) {
                            Text(
                                src.title.substringBefore(" —"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                src.symbols.forEach { sym ->
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

                // ── شرط ──
                Text("شرط فعال شدن", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
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
                                AlertCondition.ABOVE, AlertCondition.BELOW -> "عدد قیمت (مثلاً 6420)"
                                else -> "درصد (مثلاً 5)"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // ── زمان‌بندی ──
                Text("زمان‌بندی هشدار", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("فقط در بازه‌ی زمانی زیر فعال باشد", fontSize = 13.sp)
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                }

                if (scheduleEnabled) {
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
                    Text("روزهای فعال", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // ── ضد اسپم ──
                Text("فاصله‌ی بین نوتیف‌ها", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("فقط لحظه‌ی عبور از حد نوتیف بده", fontSize = 13.sp)
                    Switch(checked = onlyOnCross, onCheckedChange = { onlyOnCross = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
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
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** دکمه‌ی انتخاب ساعت با دیالوگ استاندارد اندروید */
@Composable
private fun TimeButton(label: String, minuteOfDay: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier) {
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(AlertRule.hhmm(minuteOfDay))
        }
    }
}
