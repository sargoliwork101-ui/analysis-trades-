package com.pulse.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.ChangeMode
import com.pulse.market.data.FetchKind
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.ui.settings.DialogActionsRow
import com.pulse.market.ui.settings.DialogShell
import com.pulse.market.ui.settings.InnerRow
import com.pulse.market.ui.settings.RowDivider
import com.pulse.market.ui.settings.RowsCard
import com.pulse.market.ui.settings.SectionHeader

/**
 * پنجره‌ی «منبع دلخواه» — با همان زبان طراحی تنظیمات:
 * کاربر آدرس یک سایت/API را می‌دهد و می‌گوید قیمت در کدام مسیر JSON قرار دارد
 * (یا با کدام سلکتور CSS از HTML خوانده شود).
 */
@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onSave: (SourceDef) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var kind by remember { mutableStateOf(FetchKind.JSON_REST) }
    var pricePath by remember { mutableStateOf("") }
    var changePath by remember { mutableStateOf("") }
    var volumePath by remember { mutableStateOf("") }
    var changeMode by remember { mutableStateOf(ChangeMode.PERCENT) }
    var cssSelector by remember { mutableStateOf("") }
    var cssAttr by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var scale by remember { mutableStateOf("1") }
    var symbolsText by remember { mutableStateOf("") }

    val canSave = title.isNotBlank() && url.startsWith("http") &&
            (if (kind == FetchKind.JSON_REST) pricePath.isNotBlank() else cssSelector.isNotBlank())

    DialogShell(
        icon = Icons.Default.Add,
        tint = Color(0xFF38BDF8),
        title = "منبع داده‌ی دلخواه",
        subtitle = "قیمت را از هر API یا صفحه‌ی وب بخوان",
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ─────────── ۱) نام و آدرس ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("نام و آدرس")
                RowsCard {
                    InnerRow {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text("نام منبع (مثلاً: سهام وال‌استریت)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    RowDivider()
                    InnerRow {
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            label = { Text("آدرس (کد نماد را {symbol} بگذار)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ─────────── ۲) نوع داده و مسیر ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("نوع داده")
                RowsCard {
                    InnerRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = kind == FetchKind.JSON_REST,
                                onClick = { kind = FetchKind.JSON_REST },
                                label = { Text("JSON (API)") }
                            )
                            FilterChip(
                                selected = kind == FetchKind.HTML_CSS,
                                onClick = { kind = FetchKind.HTML_CSS },
                                label = { Text("صفحه‌ی HTML") }
                            )
                        }
                    }

                    RowDivider()

                    if (kind == FetchKind.JSON_REST) {
                        InnerRow {
                            OutlinedTextField(
                                value = pricePath, onValueChange = { pricePath = it },
                                label = { Text("مسیر قیمت، مثل data.price یا {symbol}.usd") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = changePath, onValueChange = { changePath = it },
                                label = { Text("مسیر تغییر (اختیاری)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = volumePath, onValueChange = { volumePath = it },
                                label = { Text("مسیر حجم (اختیاری، مثل {symbol}.usd_24h_vol)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        RowDivider()
                        InnerRow {
                            Text(
                                "معنی عدد تغییر",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    ChangeMode.PERCENT to "درصد",
                                    ChangeMode.ABSOLUTE to "مقدار",
                                    ChangeMode.PREV_CLOSE to "قیمت دیروز",
                                    ChangeMode.NONE to "ندارم"
                                ).forEach { (m, label) ->
                                    FilterChip(
                                        selected = changeMode == m,
                                        onClick = { changeMode = m },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    } else {
                        InnerRow {
                            OutlinedTextField(
                                value = cssSelector, onValueChange = { cssSelector = it },
                                label = { Text("سلکتور CSS، مثل #price .value") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = cssAttr, onValueChange = { cssAttr = it },
                                label = { Text("خواندن از ویژگی (اختیاری، مثل content)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // ─────────── ۳) واحد و نمادها ───────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("واحد و نمادها")
                RowsCard {
                    InnerRow {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = unit, onValueChange = { unit = it },
                                label = { Text("واحد (تومان/ریال/$)") },
                                modifier = Modifier.weight(1f), singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = scale, onValueChange = { scale = it },
                                label = { Text("ضریب (تقسیم/ضرب)") },
                                modifier = Modifier.weight(1f), singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Text(
                            "اگر سایت قیمت را ریال یا سنت می‌دهد، ضریب را مثلاً 0.1 بگذار تا تومان/دلار شود.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RowDivider()
                    InnerRow {
                        OutlinedTextField(
                            value = symbolsText, onValueChange = { symbolsText = it },
                            label = { Text("نمادها — هر خط: کد:نام  مثل\nAAPL:اپل\nTSLA:تسلا") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        DialogActionsRow(
            cancelText = "انصراف",
            confirmText = "ذخیره",
            onCancel = onDismiss,
            confirmEnabled = canSave,
            onConfirm = {
                val syms = symbolsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { line ->
                        val parts = line.split(":", limit = 2)
                        val code = parts[0].trim()
                        val label = parts.getOrNull(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: code
                        SymbolDef(code, label)
                    }
                onSave(
                    SourceDef(
                        id = "custom_" + System.currentTimeMillis(),
                        title = title.trim(),
                        subtitle = if (kind == FetchKind.JSON_REST) "منبع دلخواه (JSON)" else "منبع دلخواه (HTML)",
                        kind = kind,
                        urlTemplate = url.trim(),
                        pricePath = pricePath.takeIf { it.isNotBlank() },
                        changePath = changePath.takeIf { it.isNotBlank() },
                        volumePath = volumePath.takeIf { it.isNotBlank() },
                        changeMode = changeMode,
                        cssSelector = cssSelector.takeIf { it.isNotBlank() },
                        cssAttr = cssAttr.takeIf { it.isNotBlank() },
                        scale = scale.trim().toDoubleOrNull() ?: 1.0,
                        unit = unit.trim(),
                        symbols = syms,
                        builtIn = false
                    )
                )
            }
        )
    }
}
