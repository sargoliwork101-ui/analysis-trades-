package com.pulse.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.ChangeMode
import com.pulse.market.data.FetchKind
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef

/**
 * پنجره‌ی «منبع دلخواه»: کاربر آدرس یک سایت/API را می‌دهد و
 * می‌گوید قیمت در کدام مسیر JSON قرار دارد (یا با کدام سلکتور CSS از HTML خوانده شود).
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
    var changeMode by remember { mutableStateOf(ChangeMode.PERCENT) }
    var cssSelector by remember { mutableStateOf("") }
    var cssAttr by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var scale by remember { mutableStateOf("1") }
    var symbolsText by remember { mutableStateOf("") }

    val canSave = title.isNotBlank() && url.startsWith("http") &&
            (if (kind == FetchKind.JSON_REST) pricePath.isNotBlank() else cssSelector.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("منبع دلخواه") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("نام منبع (مثلاً: سهام وال‌استریت)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("آدرس (کد نماد را {symbol} بگذار)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع داده", fontSize = 13.sp)
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

                if (kind == FetchKind.JSON_REST) {
                    OutlinedTextField(
                        value = pricePath, onValueChange = { pricePath = it },
                        label = { Text("مسیر قیمت، مثل data.price یا {symbol}.usd") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = changePath, onValueChange = { changePath = it },
                        label = { Text("مسیر تغییر (اختیاری)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("معنی عدد تغییر", fontSize = 13.sp)
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
                } else {
                    OutlinedTextField(
                        value = cssSelector, onValueChange = { cssSelector = it },
                        label = { Text("سلکتور CSS، مثل #price .value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cssAttr, onValueChange = { cssAttr = it },
                        label = { Text("خواندن از ویژگی (اختیاری، مثل content)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text("واحد (تومان/ریال/$)") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = scale, onValueChange = { scale = it },
                        label = { Text("ضریب (تقسیم/ضرب)") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Text("اگر سایت قیمت را ریال یا سنت می‌دهد، ضریب را مثلاً 0.1 بگذار تا تومان/دلار شود.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = symbolsText, onValueChange = { symbolsText = it },
                    label = { Text("نمادها — هر خط: کد:نام  مثل\nAAPL:اپل\nTSLA:تسلا") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
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
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
