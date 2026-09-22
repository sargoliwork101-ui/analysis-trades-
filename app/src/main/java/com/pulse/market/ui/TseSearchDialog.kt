package com.pulse.market.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.TseInstrument
import com.pulse.market.data.TseService
import com.pulse.market.ui.settings.DialogShell
import com.pulse.market.ui.settings.Hint
import com.pulse.market.ui.settings.RowDivider
import com.pulse.market.ui.settings.RowsCard
import kotlinx.coroutines.launch

/**
 * پنجره‌ی جستجوی نماد بورس تهران — با همان زبان طراحی تنظیمات:
 * یک کارت یکپارچه برای نتایج و ردیف‌های جداشده با خط ظریف.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TseSearchDialog(
    selectedSymbols: List<SymbolDef>,
    customSymbols: List<SymbolDef> = emptyList(),
    onDismiss: () -> Unit,
    onAddSymbol: (SymbolDef) -> Unit,
    onRemoveSymbol: (SymbolDef) -> Unit = {},
    onDeleteCustomSymbol: (SymbolDef) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<TseInstrument>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }

    // بارگذاری اولیه‌ی چند نماد محبوب
    LaunchedEffect(Unit) {
        searchResults = TseService.POPULAR_INSTRUMENTS.take(8)
    }

    fun doSearch(queryText: String) {
        val q = queryText.trim()
        if (q.isBlank()) {
            searchResults = TseService.POPULAR_INSTRUMENTS.take(8)
            statusMessage = ""
            return
        }
        isLoading = true
        statusMessage = "در حال جستجو در بورس تهران…"
        focusManager.clearFocus()
        scope.launch {
            val results = TseService.search(q)
            searchResults = results
            isLoading = false
            statusMessage = if (results.isEmpty()) "هیچ نمادی با «$q» یافت نشد. می‌توانید لینک مستقیم صفحه TSETMC را وارد کنید." else ""
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString().orEmpty().trim()
            if (text.isNotEmpty()) {
                searchQuery = text
                doSearch(text)
            }
        }
    }

    DialogShell(
        icon = Icons.Default.Search,
        tint = Color(0xFF22C55E),
        title = "جستجوی نماد بورس تهران",
        subtitle = "نام سهم (مثلاً اهرم، خودرو) یا لینک صفحه‌ی TSETMC",
        onClose = onDismiss
    ) {

        // ─── فیلد جستجو + چسباندن ───
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (it.isBlank()) {
                    searchResults = TseService.POPULAR_INSTRUMENTS.take(8)
                    statusMessage = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("نام نماد یا لینک tsetmc.com...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = TseService.POPULAR_INSTRUMENTS.take(8)
                            statusMessage = ""
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                        }
                    }
                    IconButton(onClick = { pasteFromClipboard() }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "چسباندن لینک")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch(searchQuery) })
        )

        Button(
            onClick = { doSearch(searchQuery) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("جستجو / بررسی لینک")
        }

        // ─── چیپ‌های پرطرفدار (دسترسی سریع) ───
        if (searchQuery.isBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Hint("پرمعامله‌ها و صندوق‌های طلا — کلیک برای جستجو")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("اهرم", "طلا", "عیار", "کهربا", "فولاد", "خودرو", "خساپا", "شپنا", "شتران", "دی").forEach { s ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                searchQuery = s
                                doSearch(s)
                            },
                            label = { Text(s, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        if (statusMessage.isNotEmpty()) {
            Text(
                statusMessage,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // ─── نتایج — یک کارت یکپارچه با ردیف‌های جداشده ───
        RowsCard {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 265.dp)
            ) {
                itemsIndexed(searchResults) { index, inst ->
                    if (index > 0) RowDivider()

                    val isAlreadyAdded = selectedSymbols.any {
                        it.code.equals(inst.symbol, ignoreCase = true) || it.code == inst.insCode
                    }
                    val isCustom = customSymbols.any {
                        it.code.equals(inst.symbol, ignoreCase = true) || it.code == inst.insCode
                    }
                    val symDef = SymbolDef(
                        code = inst.symbol,
                        label = inst.name.ifBlank { inst.symbol },
                        sourceId = "tse_tsetmc"
                    )

                    TseResultRow(
                        inst = inst,
                        isAdded = isAlreadyAdded,
                        isCustom = isCustom,
                        onAdd = { onAddSymbol(symDef) },
                        onRemove = { onRemoveSymbol(symDef) },
                        onDeleteCustom = { onDeleteCustomSymbol(symDef) }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("بستن", fontSize = 13.sp)
        }
    }
}

/** یک ردیف نتیجه‌ی جستجو — نام نماد + قیمت + دکمه‌ی افزودن/حذف */
@Composable
private fun TseResultRow(
    inst: TseInstrument,
    isAdded: Boolean,
    isCustom: Boolean = false,
    onAdd: () -> Unit,
    onRemove: () -> Unit = {},
    onDeleteCustom: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded) { onAdd() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    inst.symbol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (inst.changePct != null) {
                    Spacer(Modifier.width(8.dp))
                    val isUp = inst.changePct >= 0
                    val text = (if (isUp) "+" else "") + String.format("%.2f%%", inst.changePct)
                    Text(
                        text,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                inst.name,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (inst.closePrice != null && inst.closePrice > 0) {
                Text(
                    "قیمت: ${Format.price(inst.closePrice)} ریال" +
                            (inst.volume?.let { " • حجم ${Format.volume(it)}" } ?: ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // نمادهای دلخواه کاربر — قابل حذف از فهرست
        if (isCustom) {
            IconButton(onClick = onDeleteCustom) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف از فهرست من",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isAdded) {
            // با زدن دوباره، نماد از ویجت حذف می‌شود
            OutlinedButton(
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("انتخاب شده — حذف", fontSize = 11.sp)
            }
        } else {
            Button(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("افزودن", fontSize = 11.5.sp)
            }
        }
    }
}
