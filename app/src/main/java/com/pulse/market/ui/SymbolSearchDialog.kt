package com.pulse.market.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.market.data.MarketKind
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.TseService
import com.pulse.market.data.WebSymbolSearch
import com.pulse.market.data.marketKind
import com.pulse.market.ui.settings.DialogShell
import com.pulse.market.ui.settings.RowDivider
import com.pulse.market.ui.settings.RowsCard
import kotlinx.coroutines.delay

/** یک نتیجه‌ی جستجو — یکدست برای همه‌ی منابع */
private data class SearchHit(
    val sym: SymbolDef,
    /** عنوان اصلی ردیف — بورس: نماد کوتاه (فولاد) | بقیه: نام نمایشی */
    val title: String,
    /** خط دوم — بورس: نام کامل | بقیه: کد نماد */
    val subtitle: String = "",
    /** خط سوم — فقط بورس: قیمت و حجم لحظه‌ای */
    val priceLine: String = "",
    /** بورس: insCode — برای تشخیص نمادهایی که با لینک اضافه شده‌اند */
    val altCode: String = "",
    /** نماد ذخیره‌شده در «نمادهای دلخواه بورس من» — قابل حذف از فهرست */
    val isTseCustom: Boolean = false
)

/**
 * پنجره‌ی جستجوی نماد — برای **همه‌ی منابع**، نه فقط بورس:
 * - بورس تهران: جستجوی آنلاین TSETMC (نام، کد یا لینک صفحه) + فهرست پرمعامله‌ها
 * - کریپتو: جستجوی آنلاین (CoinGecko) + فهرست آماده
 * - بقیه‌ی منابع (طلا و ارز TGJU، منابع دلخواه): جستجو در فهرست خودشان
 *   + افزودن نماد با هر کد دلخواه
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolSearchDialog(
    sources: List<SourceDef>,
    selectedSymbols: List<SymbolDef>,
    tseCustomSymbols: List<SymbolDef> = emptyList(),
    onDismiss: () -> Unit,
    onAddSymbol: (SymbolDef) -> Unit,
    onRemoveSymbol: (SymbolDef) -> Unit = {},
    onDeleteTseCustomSymbol: (SymbolDef) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // منبع پیش‌فرض: بورس (اگر فعال باشد)، وگرنه اولین منبع ویجت
    var activeSource by remember {
        mutableStateOf(
            sources.firstOrNull { it.marketKind == MarketKind.TSE } ?: sources.firstOrNull()
        )
    }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    val isTse = activeSource?.marketKind == MarketKind.TSE

    // جستجوی خودکار با تغییر منبع یا عبارت — با مکث کوتاه تا تایپ تمام شود
    LaunchedEffect(activeSource?.id, query) {
        val src = activeSource ?: return@LaunchedEffect
        val q = query.trim()
        delay(350)
        loading = true
        val result = runCatching {
            if (src.marketKind == MarketKind.TSE) tseHits(q, tseCustomSymbols)
            else generalHits(src, q)
        }.getOrDefault(emptyList())
        hits = result
        loading = false
        status = when {
            result.isNotEmpty() || q.isBlank() -> ""
            src.marketKind == MarketKind.TSE ->
                "چیزی با «$q» پیدا نشد — می‌توانی لینک صفحه‌ی TSETMC را بچسبانی و دوباره جستجو کنی"

            else -> "در فهرست این منبع چیزی با «$q» نبود — می‌توانی همین را به‌عنوان کد دلخواه اضافه کنی"
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            query = text
            focusManager.clearFocus()
        }
    }

    DialogShell(
        icon = Icons.Default.Search,
        tint = Color(0xFF38BDF8),
        title = "جستجوی نماد",
        subtitle = activeSource?.title?.substringBefore(" —") ?: "منبعی انتخاب نشده",
        onClose = onDismiss
    ) {

        // ─── انتخاب منبعِ جستجو (وقتی چند منبع در ویجت فعال است) ───
        if (sources.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { src ->
                    FilterChip(
                        selected = activeSource?.id == src.id,
                        onClick = { activeSource = src },
                        label = { Text(src.title.substringBefore(" —"), fontSize = 11.sp) }
                    )
                }
            }
        }

        // ─── فیلد جستجو + چسباندن ───
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(if (isTse) "نام نماد یا لینک tsetmc.com…" else "نام یا کد نماد…")
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                        }
                    }
                    IconButton(onClick = { pasteFromClipboard() }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "چسباندن")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("در حال جستجو…", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.secondary)
            }
        } else if (status.isNotEmpty()) {
            Text(status, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.secondary)
        }

        // ─── افزودن نماد با کد دلخواه (منابع غیر بورس) ───
        // وقتی عبارتی تایپ شده و همین کد دقیقاً در نتایج نیست
        val q = query.trim()
        val src = activeSource
        if (!isTse && src != null && q.isNotEmpty() &&
            hits.none { it.sym.code.equals(q, ignoreCase = true) }
        ) {
            ManualAddRow(code = q) {
                // اگر همین کد از قبل در فهرست نمادهای منبع باشد، تعریف کاملش (واحد و
                // ضریب مخصوص نماد) برگردانده می‌شود تا مثلاً «انس طلا» دلاری ذخیره شود،
                // نه با واحد و ضریب منبع (باگ ۱٫۱۳).
                val known = src.symbols.firstOrNull { it.code.equals(q, ignoreCase = true) }
                onAddSymbol(known?.copy(sourceId = src.id) ?: SymbolDef(q, q, src.id))
            }
        }

        // ─── نتایج — یک کارت یکپارچه با ردیف‌های جداشده ───
        RowsCard {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                itemsIndexed(hits) { index, hit ->
                    if (index > 0) RowDivider()

                    val isAdded = selectedSymbols.any { sel ->
                        (sel.code.equals(hit.sym.code, ignoreCase = true) &&
                                (sel.sourceId.isEmpty() || sel.sourceId == hit.sym.sourceId)) ||
                                (hit.altCode.isNotEmpty() && sel.code == hit.altCode)
                    }

                    HitRow(
                        hit = hit,
                        isAdded = isAdded,
                        onAdd = { onAddSymbol(hit.sym) },
                        onRemove = { onRemoveSymbol(hit.sym) },
                        onDeleteCustom = { onDeleteTseCustomSymbol(hit.sym) }
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

// ───────────────────── جستجوی هر نوع منبع ─────────────────────

/** بورس تهران: خالی = پرمعامله‌ها | عبارت = جستجوی آنلاین TSETMC (نام/کد/لینک) */
private suspend fun tseHits(q: String, custom: List<SymbolDef>): List<SearchHit> {
    val instruments = if (q.isBlank()) TseService.POPULAR_INSTRUMENTS.take(24)
    else TseService.search(q)
    return instruments.map { inst ->
        SearchHit(
            sym = SymbolDef(inst.symbol, inst.name.ifBlank { inst.symbol }, "tse_tsetmc"),
            title = inst.symbol,
            subtitle = inst.name,
            priceLine = if (inst.closePrice != null && inst.closePrice > 0) {
                "قیمت: ${QuoteText.priceWithUnit(inst.closePrice, "ریال")}" +
                        (inst.volume?.let { " • حجم ${Format.volume(it)}" } ?: "")
            } else "",
            altCode = inst.insCode,
            isTseCustom = custom.any { it.code == inst.symbol || it.code == inst.insCode }
        )
    }
}

/** منابع دیگر: فهرست محلی + جستجوی آنلاین (کریپتو) — بدون تکرار */
private suspend fun generalHits(src: SourceDef, q: String): List<SearchHit> {
    val local = if (q.isBlank()) src.symbols
    else src.symbols.filter { it.label.contains(q, true) || it.code.contains(q, true) }
    val localHits = local.map { SearchHit(sym = it.copy(sourceId = src.id), title = it.label, subtitle = it.code) }
    if (q.isBlank()) return localHits

    // جستجوی آنلاین — اگر منبع API جستجو نداشت یا شبکه شکست خورد، همین فهرست کافی است
    val web = WebSymbolSearch.search(src.id, q).orEmpty()
    val seen = localHits.mapTo(mutableSetOf()) { it.sym.code.lowercase() }
    val webHits = web.filter { it.code.lowercase() !in seen }.map {
        SearchHit(sym = it, title = it.label, subtitle = "${it.code} • از جستجوی آنلاین")
    }
    return localHits + webHits
}

// ───────────────────── ردیف‌های UI ─────────────────────

/** یک نتیجه‌ی جستجو — عنوان + خطوط توضیح + دکمه‌ی افزودن/حذف */
@Composable
private fun HitRow(
    hit: SearchHit,
    isAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded) { onAdd() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                hit.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (hit.subtitle.isNotBlank() && hit.subtitle != hit.title) {
                Text(
                    hit.subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (hit.priceLine.isNotBlank()) {
                Text(
                    hit.priceLine,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // نمادهای دلخواه بورس — قابل حذف از فهرست من
        if (hit.isTseCustom) {
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

/** ردیف «افزودن با همین کد» — برای منابعی که نماد دلخواه می‌پذیرند */
@Composable
private fun ManualAddRow(code: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "افزودن «$code»",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "با همین کد به این منبع اضافه می‌شود",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onAdd,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("افزودن", fontSize = 11.5.sp)
        }
    }
}
