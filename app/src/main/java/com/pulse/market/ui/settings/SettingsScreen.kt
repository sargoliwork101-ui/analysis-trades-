package com.pulse.market.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pulse.market.data.AlertEngine
import com.pulse.market.data.AlertRule
import com.pulse.market.data.ConfigStore
import com.pulse.market.data.Fetcher
import com.pulse.market.data.SourceCatalog
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.service.LiveUpdateService
import com.pulse.market.service.LiveUpdateWorker
import com.pulse.market.ui.AddAlertDialog
import com.pulse.market.ui.AddSourceDialog
import com.pulse.market.ui.Format
import com.pulse.market.ui.TseSearchDialog
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.launch

/** دسته‌های صفحه‌ی تنظیمات */
enum class SettingsCategory(val title: String) {
    SOURCES("📡 منابع داده"),
    SYMBOLS("📈 نمادها"),
    LOOK("🎨 ظاهر و سرعت"),
    ALERTS("🔔 هشدارها")
}

/**
 * صفحه‌ی تنظیمات با چهار دسته‌بندی:
 * منابع داده (چند انتخابی) • نمادها • ظاهر و سرعت • هشدارها
 * نوار پایین همیشه دکمه‌های «تست داده» و «ذخیره و به‌روزرسانی» را دارد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(fromWidget: Boolean, onApply: (WidgetConfig) -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf(WidgetConfig()) }
    var customSources by remember { mutableStateOf<List<SourceDef>>(emptyList()) }
    var tseCustomSymbols by remember { mutableStateOf<List<SymbolDef>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SettingsCategory.SOURCES) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showTseSearchDialog by remember { mutableStateOf(false) }
    var alertDialogOpen by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<AlertRule?>(null) }

    // ─── بارگذاری و ذخیره ───

    fun persistAlerts(list: List<AlertRule>) {
        cfg = cfg.copy(alerts = list)
        scope.launch { ConfigStore.save(context, cfg.copy(alerts = list)) }
    }

    LaunchedEffect(Unit) {
        cfg = ConfigStore.current(context)
        customSources = ConfigStore.currentCustomSources(context)
        tseCustomSymbols = ConfigStore.currentTseSymbols(context)
    }

    // ─── اجازه‌ی اعلان (اندروید ۱۳ به بالا) ───

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted && context is ComponentActivity) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val allSources = SourceCatalog.all(customSources)
    val selectedIds = cfg.activeSourceIds
    val selectedSources = allSources.filter { it.id in selectedIds }

    // ─── اسکلت صفحه ───

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("نبض بازار", fontWeight = FontWeight.Bold)
                        Text(
                            "ویجت لحظه‌ای سهام، کریپتو، طلا و ارز",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // چیپ‌های دسته‌بندی
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.title, fontSize = 12.sp) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // محتوای دسته‌ی انتخاب‌شده
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fromWidget) {
                    InfoCard("منابع و نمادها را انتخاب کن و «ذخیره و به‌روزرسانی» را بزن.")
                }

                when (category) {
                    SettingsCategory.SOURCES -> SourcesCategory(
                        allSources = allSources,
                        selectedIds = selectedIds,
                        onToggle = { src ->
                            val ids = selectedIds.toMutableList()
                            if (src.id in ids) {
                                ids.remove(src.id)
                                if (ids.isEmpty()) ids.add(src.id) // حداقل یک منبع روشن بماند
                                cfg = cfg.copy(
                                    sourceIds = ids,
                                    sourceId = ids.first(),
                                    symbols = cfg.symbols.filterNot { it.sourceId == src.id }
                                )
                            } else {
                                ids.add(src.id)
                                val room = (4 - cfg.symbols.size).coerceAtLeast(0)
                                cfg = cfg.copy(
                                    sourceIds = ids,
                                    sourceId = ids.first(),
                                    symbols = cfg.symbols +
                                            src.symbols.take(minOf(room, maxOf(1, 4 / (ids.size)))).map {
                                                it.copy(sourceId = src.id)
                                            }
                                )
                            }
                        },
                        onDelete = { src ->
                            scope.launch {
                                val list = customSources.filterNot { it.id == src.id }
                                ConfigStore.saveCustomSources(context, list)
                                customSources = list
                                if (src.id in cfg.activeSourceIds) {
                                    val ids = cfg.activeSourceIds.filterNot { it == src.id }
                                        .ifEmpty { listOf(SourceCatalog.builtIn.first().id) }
                                    cfg = cfg.copy(
                                        sourceIds = ids,
                                        sourceId = ids.first(),
                                        symbols = cfg.symbols.filterNot { it.sourceId == src.id }
                                    )
                                    ConfigStore.save(context, cfg)
                                }
                            }
                        },
                        onAddClick = { showAddDialog = true }
                    )

                    SettingsCategory.SYMBOLS -> SymbolsCategory(
                        selectedSources = selectedSources,
                        selectedSymbols = cfg.symbols,
                        tseCustomSymbols = tseCustomSymbols,
                        onToggle = { sym, src ->
                            val list = cfg.symbols.toMutableList()
                            val selected = list.any { it.code == sym.code && it.sourceId == src.id }
                            if (selected) {
                                list.removeAll { it.code == sym.code && it.sourceId == src.id }
                            } else if (list.size < 4) {
                                list.add(sym.copy(sourceId = src.id))
                            }
                            cfg = cfg.copy(symbols = list)
                        },
                        onOpenTseSearch = { showTseSearchDialog = true }
                    )

                    SettingsCategory.LOOK -> LookCategory(
                        cfg = cfg,
                        onChange = { new -> cfg = new },
                        onLiveToggle = { on ->
                            cfg = cfg.copy(liveService = on)
                            if (on) {
                                LiveUpdateService.start(context)
                                LiveUpdateWorker.schedule(context)
                            } else {
                                LiveUpdateService.stop(context)
                                LiveUpdateWorker.cancel(context)
                            }
                        }
                    )

                    SettingsCategory.ALERTS -> AlertsCategory(
                        cfg = cfg,
                        onToggleAlert = { rule, on ->
                            persistAlerts(cfg.alerts.map {
                                if (it.id == rule.id) it.copy(enabled = on) else it
                            })
                        },
                        onEditAlert = { rule -> editingAlert = rule; alertDialogOpen = true },
                        onDeleteAlert = { rule ->
                            persistAlerts(cfg.alerts.filterNot { it.id == rule.id })
                        },
                        onAddAlert = { editingAlert = null; alertDialogOpen = true },
                        onTestNotification = { AlertEngine.notifyTest(context) }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            // نوار اقدام پایین (همیشه در دسترس)
            Surface(
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (testResult.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                testResult,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = !busy && cfg.symbols.isNotEmpty(),
                            onClick = {
                                busy = true
                                testResult = "در حال تست…"
                                scope.launch {
                                    val byId = allSources.associateBy { it.id }
                                    val lines = cfg.symbols.take(6).map { sym ->
                                        val src = byId[sym.sourceId]
                                            ?: byId[cfg.activeSourceIds.firstOrNull().orEmpty()]
                                        val q = src?.let { Fetcher.fetch(it, sym) }
                                        if (q?.price != null)
                                            "${q.label}: ${Format.price(q.price)} ${q.unit}  ${Format.pct(q.changePct)}"
                                        else
                                            "${sym.label}: خطا — ${q?.error ?: "منبع پیدا نشد"}"
                                    }
                                    testResult = lines.joinToString("\n")
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.padding(3.dp))
                            Text("تست داده", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    ConfigStore.save(context, cfg)
                                    StockWidgetProvider.refreshAll(context, force = true)
                                    if (cfg.liveService) {
                                        LiveUpdateService.start(context)
                                        LiveUpdateWorker.schedule(context)
                                    } else {
                                        LiveUpdateService.stop(context)
                                        LiveUpdateWorker.cancel(context)
                                    }
                                    testResult = "ذخیره شد ✓ ویجت به‌روزرسانی شد."
                                    busy = false
                                }
                                onApply(cfg)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (cfg.liveService) Icons.Default.PlayArrow else Icons.Default.Stop,
                                contentDescription = null
                            )
                            Spacer(Modifier.padding(3.dp))
                            Text("ذخیره و به‌روزرسانی", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // ─── پنجره‌ها ───

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newSource ->
                scope.launch {
                    val list = customSources + newSource
                    ConfigStore.saveCustomSources(context, list)
                    customSources = list
                    // منبع تازه خودکار روشن و نمادهایش انتخاب شود
                    val ids = (cfg.activeSourceIds + newSource.id).distinct()
                    val room = (4 - cfg.symbols.size).coerceAtLeast(0)
                    cfg = cfg.copy(
                        sourceIds = ids,
                        sourceId = ids.first(),
                        symbols = (cfg.symbols + newSource.symbols.take(room)
                            .map { it.copy(sourceId = newSource.id) })
                    )
                    showAddDialog = false
                }
            }
        )
    }

    if (showTseSearchDialog) {
        TseSearchDialog(
            selectedSymbols = cfg.symbols,
            onDismiss = { showTseSearchDialog = false },
            onAddSymbol = { newSym ->
                scope.launch {
                    val updatedCustom = (tseCustomSymbols + newSym).distinctBy { it.code }
                    ConfigStore.saveTseSymbols(context, updatedCustom)
                    tseCustomSymbols = updatedCustom

                    val currentList = cfg.symbols.toMutableList()
                    if (!currentList.any { it.code == newSym.code && it.sourceId == newSym.sourceId }) {
                        if (currentList.size >= 4) {
                            currentList.removeAt(currentList.size - 1)
                        }
                        currentList.add(newSym)
                        cfg = cfg.copy(symbols = currentList)
                        ConfigStore.save(context, cfg)
                    }
                }
            }
        )
    }

    if (alertDialogOpen) {
        AddAlertDialog(
            sources = selectedSources,
            existing = editingAlert,
            onDismiss = { alertDialogOpen = false; editingAlert = null },
            onSave = { rule ->
                val list = cfg.alerts.filterNot { it.id == rule.id } + rule
                persistAlerts(list)
                // اگر نماد هشدار در ویجت نیست، اضافه‌اش کن تا هشدار قابل بررسی باشد
                val already = cfg.symbols.any {
                    it.code == rule.symbolCode &&
                            (it.sourceId.isEmpty() || it.sourceId == rule.sourceId)
                }
                if (!already && cfg.symbols.size < 4) {
                    cfg = cfg.copy(
                        symbols = cfg.symbols + SymbolDef(rule.symbolCode, rule.symbolLabel, rule.sourceId)
                    )
                    scope.launch { ConfigStore.save(context, cfg) }
                }
                alertDialogOpen = false
                editingAlert = null
            }
        )
    }
}
