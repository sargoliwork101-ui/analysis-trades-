package com.pulse.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.JalaliDate

/**
 * انتخابگرِ تاریخِ شمسی (جلالی) — سه فهرستِ کشویی: سال، ماه (با نام)، روز.
 * روزها با توجه به ماه/کبیسه‌بودن سال محدود می‌شوند و تاریخِ آینده مجاز نیست.
 */
@Composable
fun PersianDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val today = remember { JalaliDate.fromMillis(System.currentTimeMillis()) }
    val init = remember(initialMillis) {
        JalaliDate.fromMillis(if (initialMillis > 0L) initialMillis else System.currentTimeMillis())
    }
    var year by remember { mutableStateOf(init.year) }
    var month by remember { mutableStateOf(init.month) }
    var day by remember { mutableStateOf(init.day) }

    // بازه‌ی سال‌ها: ۳۰ سالِ گذشته تا امسال
    val years = remember(today) { (today.year downTo today.year - 30).toList() }
    val maxDays = JalaliDate.daysInMonth(year, month)
    val safeDay = day.coerceAtMost(maxDays)

    // جلوگیری از انتخابِ تاریخِ آینده
    fun isFuture(y: Int, m: Int, d: Int): Boolean =
        y > today.year ||
            (y == today.year && m > today.month) ||
            (y == today.year && m == today.month && d > today.day)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تاریخ خرید (شمسی)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // روز
                    PickerColumn(
                        label = "روز",
                        modifier = Modifier.width(72.dp),
                        options = (1..maxDays).map { it to Format.toPersianDigits("$it") },
                        selected = safeDay,
                        onSelect = { day = it }
                    )
                    // ماه (با نام)
                    PickerColumn(
                        label = "ماه",
                        modifier = Modifier.width(118.dp),
                        options = (1..12).map { it to JalaliDate.MONTHS[it - 1] },
                        selected = month,
                        onSelect = {
                            month = it
                            val md = JalaliDate.daysInMonth(year, it)
                            if (day > md) day = md
                        }
                    )
                    // سال
                    PickerColumn(
                        label = "سال",
                        modifier = Modifier.width(90.dp),
                        options = years.map { it to Format.toPersianDigits("$it") },
                        selected = year,
                        onSelect = {
                            year = it
                            val md = JalaliDate.daysInMonth(it, month)
                            if (day > md) day = md
                        }
                    )
                }
                if (isFuture(year, month, safeDay)) {
                    Text(
                        "تاریخِ آینده قابلِ انتخاب نیست",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(JalaliDate.toMillis(year, month, safeDay)) },
                enabled = !isFuture(year, month, safeDay)
            ) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** یک ستونِ انتخاب: دکمه‌ای که فهرستِ کشویی باز می‌کند. */
@Composable
private fun PickerColumn(
    label: String,
    modifier: Modifier = Modifier,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: "$selected",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                for ((value, text) in options) {
                    DropdownMenuItem(
                        text = { Text(text, fontSize = 13.sp) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
