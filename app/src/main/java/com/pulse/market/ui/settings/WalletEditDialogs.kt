package com.pulse.market.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.PaperTradeStore

/** مقادیرِ ویرایش‌شده‌ی یک معامله؛ فقط فیلدهای غیرِ null اعمال می‌شوند. */
data class WalletTradeEdit(
    val amountUsd: Double? = null,
    val entryPrice: Double? = null,
    val takeProfitPct: Double? = null,
    val stopLossPct: Double? = null,
    val feePct: Double? = null,
    val note: String? = null,
    val clearTakeProfit: Boolean = false,
    val clearStopLoss: Boolean = false,
    val closePrice: Double? = null,
    val closeReason: PaperTradeStore.CloseReason? = null
)

private fun String.toNum(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** نمایشِ یک عدد به‌صورت رشته‌ی لاتینِ خوانا برای پیش‌پرکردنِ فیلد (بدون نماد علمی). */
private fun fieldValue(v: Double?): String {
    if (v == null || !v.isFinite()) return ""
    return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}

/**
 * پنجره‌ی «سرمایه‌ی کیف»: تعیینِ مقدار یا افزودن پول. یک راهِ خروج هم برای صفر‌کردنِ
 * سود/زیانِ ماندگار دارد تا کاربر در بن‌بست نماند.
 */
@Composable
fun CapitalEditDialog(
    currentCapital: Double,
    persian: Boolean,
    onSetCapital: (Double) -> Unit,
    onAddCapital: (Double) -> Unit,
    onResetCarried: () -> Unit,
    onDismiss: () -> Unit
) {
    var capital by remember { mutableStateOf(fieldValue(currentCapital)) }
    var addAmount by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    DialogShell(
        icon = Icons.Default.AccountBalanceWallet,
        tint = ProfitGreen,
        title = "سرمایه‌ی کیف پول",
        subtitle = "پولِ داخل کیف را تعیین کن یا به آن اضافه کن. ارزشِ کل خودکار حساب می‌شود.",
        onClose = onDismiss
    ) {
        OutlinedTextField(
            value = capital,
            onValueChange = { capital = it.take(12) },
            label = { Text("مبلغ سرمایه (دلار)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        DialogActionsRow(
            cancelText = "انصراف",
            confirmText = "ثبتِ سرمایه",
            onCancel = onDismiss,
            onConfirm = {
                capital.toNum()?.let { onSetCapital(it) }
                onDismiss()
            },
            confirmEnabled = capital.toNum()?.let { it >= 0.0 } == true
        )

        Hint("یا بدونِ تغییرِ مقدارِ فعلی، پول اضافه کن:")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = addAmount,
                onValueChange = { addAmount = it.take(12) },
                label = { Text("افزودن (دلار)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    addAmount.toNum()?.let { onAddCapital(it) }
                    onDismiss()
                },
                enabled = addAmount.toNum()?.let { it > 0.0 } == true
            ) { Text("+ افزودن", fontSize = 12.sp) }
        }

        if (!confirmReset) {
            TextButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "صفر کردنِ سود/زیانِ ماندگار (بازنشانی کامل)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Hint("مطمئنی؟ مجموعِ سود/زیانِ ماندگار پاک می‌شود. سرمایه دست‌نخورده می‌ماند.")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { confirmReset = false },
                    modifier = Modifier.weight(1f)
                ) { Text("نه", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = {
                        onResetCarried()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("بله، صفر کن", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * پنجره‌ی ویرایشِ یک معامله. برای معامله‌ی باز: مبلغ/قیمتِ ورود/حد سود/حد ضرر/کارمزد/یادداشت.
 * برای معامله‌ی بسته: قیمت و دلیلِ بسته‌شدن (+ کارمزد و یادداشت). در معامله‌ی پله‌ای،
 * مبلغ و قیمتِ ورود چون خودکار از پله‌ها حساب می‌شوند قابل ویرایش نیستند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTradeDialog(
    trade: PaperTradeStore.Trade,
    persian: Boolean,
    onSave: (WalletTradeEdit) -> Unit,
    onDismiss: () -> Unit
) {
    val ladder = trade.isLadder || trade.isSellLadder
    var amount by remember { mutableStateOf(fieldValue(trade.amountUsd)) }
    var entry by remember { mutableStateOf(fieldValue(trade.entryPrice)) }
    var tp by remember { mutableStateOf(fieldValue(trade.takeProfitPct)) }
    var sl by remember { mutableStateOf(fieldValue(trade.stopLossPct)) }
    var fee by remember { mutableStateOf(fieldValue(trade.feePct)) }
    var note by remember { mutableStateOf(trade.note) }
    var closePrice by remember { mutableStateOf(fieldValue(trade.closePrice)) }
    var reason by remember { mutableStateOf(trade.closeReason ?: PaperTradeStore.CloseReason.MANUAL) }

    DialogShell(
        icon = Icons.Default.Edit,
        tint = MaterialTheme.colorScheme.primary,
        title = "ویرایش معامله",
        subtitle = "${trade.name} (${trade.symbol.uppercase()}) — " +
                if (trade.isOpen) "معامله‌ی باز" else "بسته‌شده",
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (trade.isOpen) {
                if (!ladder) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it.take(9) },
                            label = { Text("مبلغ سرمایه (دلار)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = entry,
                            onValueChange = { entry = it.take(16) },
                            label = { Text("قیمت خرید") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Hint("این معامله پله‌ای است؛ مبلغ و قیمتِ ورود خودکار از پله‌ها حساب می‌شوند.")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = tp,
                        onValueChange = { tp = it.take(6) },
                        label = { Text("حد سود ٪") },
                        placeholder = { Text("خالی = بدون حد") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sl,
                        onValueChange = { sl = it.take(6) },
                        label = { Text("حد ضرر ٪") },
                        placeholder = { Text("خالی = بدون حد") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = closePrice,
                        onValueChange = { closePrice = it.take(16) },
                        label = { Text("قیمت فروش") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "دلیل بسته‌شدن",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (r in PaperTradeStore.CloseReason.values()) {
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r.label, fontSize = 11.5.sp) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = fee,
                onValueChange = { fee = it.take(5) },
                label = { Text("کارمزد هر سمت ٪") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )
            Hint("فقط مقادیری که تغییر می‌دهی اعمال می‌شوند. حد سود/ضررِ خالی یعنی حذفِ آن حد.")
        }

        DialogActionsRow(
            cancelText = "انصراف",
            confirmText = "ذخیره",
            onCancel = onDismiss,
            onConfirm = {
                val edit = if (trade.isOpen) {
                    WalletTradeEdit(
                        amountUsd = if (ladder) null else amount.toNum(),
                        entryPrice = if (ladder) null else entry.toNum(),
                        takeProfitPct = tp.toNum(),
                        stopLossPct = sl.toNum(),
                        clearTakeProfit = tp.isBlank(),
                        clearStopLoss = sl.isBlank(),
                        feePct = fee.toNum(),
                        note = note
                    )
                } else {
                    WalletTradeEdit(
                        closePrice = closePrice.toNum(),
                        closeReason = reason,
                        feePct = fee.toNum(),
                        note = note
                    )
                }
                onSave(edit)
                onDismiss()
            }
        )
    }
}
