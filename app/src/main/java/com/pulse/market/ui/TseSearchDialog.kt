package com.pulse.market.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.TseInstrument
import com.pulse.market.data.TseService
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TseSearchDialog(
    selectedSymbols: List<SymbolDef>,
    onDismiss: () -> Unit,
    onAddSymbol: (SymbolDef) -> Unit
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                // ─── عنوان ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "جستجوی نماد بورس تهران",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "نام سهم (مثلاً اهرم، خودرو) یا لینک صفحه TSETMC",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "بستن")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ─── فیلد جستجو + دکمه چسباندن ───
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(searchQuery) })
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { doSearch(searchQuery) },
                        modifier = Modifier.weight(1f),
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
                }

                Spacer(Modifier.height(8.dp))

                // ─── چیپ‌های پرطرفدار (دسترسی سریع) ───
                if (searchQuery.isBlank()) {
                    Text(
                        "پرمعامله‌ها و صندوق‌های طلا (کلیک برای جستجو):",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
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
                    Spacer(Modifier.height(6.dp))
                }

                if (statusMessage.isNotEmpty()) {
                    Text(
                        statusMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // ─── لیست نتایج ───
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { inst ->
                        val isAlreadyAdded = selectedSymbols.any {
                            it.code.equals(inst.symbol, ignoreCase = true) || it.code == inst.insCode
                        }

                        TseItemCard(
                            inst = inst,
                            isAdded = isAlreadyAdded,
                            onAdd = {
                                onAddSymbol(
                                    SymbolDef(
                                        code = inst.symbol,
                                        label = inst.name.ifBlank { inst.symbol }
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بستن")
                }
            }
        }
    }
}

@Composable
private fun TseItemCard(
    inst: TseInstrument,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAdded) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded) { onAdd() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        inst.symbol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    if (inst.changePct != null) {
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
                        "قیمت: ${Format.price(inst.closePrice)} ریال",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (isAdded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "اضافه شده",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "انتخاب شده",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = onAdd,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("افزودن", fontSize = 12.sp)
                }
            }
        }
    }
}
