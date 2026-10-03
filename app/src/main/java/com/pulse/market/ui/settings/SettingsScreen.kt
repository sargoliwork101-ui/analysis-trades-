package com.pulse.market.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pulse.market.data.AlertEngine
import com.pulse.market.data.AlertEvent
import com.pulse.market.data.AlertHistoryStore
import com.pulse.market.data.AlertRule
import com.pulse.market.data.AppInstaller
import com.pulse.market.data.AppUpdater
import com.pulse.market.data.ConfigStore
import com.pulse.market.data.Fetcher
import com.pulse.market.data.MarketKind
import com.pulse.market.data.MAX_SYMBOLS
import com.pulse.market.data.SourceCatalog
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SourceHealth
import com.pulse.market.data.SourceHealthStore
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.data.marketKindOf
import com.pulse.market.ui.AddAlertDialog
import com.pulse.market.ui.AddSourceDialog
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText
import com.pulse.market.ui.SymbolSearchDialog
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

/** بخش‌های تنظیمات — هر کدام صفحه‌ی خودش را دارد */
enum class SettingsSection(val title: String) {
    SOURCES("منابع داده"),
    SYMBOLS("نمادها"),
    VALUES("مقادیر نمایشی"),
    LOOK("ظاهر و فونت"),
    UPDATE("به‌روزرسانی"),
    ALERTS("هشدارها"),
    PUMPS("پامپ‌های کریپتو"),
    PORTFOLIO("معامله‌های من"),
    BACKUP("بکاپ و بازگردانی"),
    ABOUT("درباره")
}

/**
 * صفحه‌ی تنظیمات — منوی تمیز + هر بخش در صفحه‌ی خودش:
 * - widgetId=0 → «الگوی پیش‌فرض» برای ویجت‌های تازه
 * - widgetId≠0 → تنظیمات همان ویجت روی صفحه (با زدن روی ویجت باز می‌شود)
 * هر تغییر فوراً و خودکار ذخیره می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    widgetId: Int = 0,
    isAddFlow: Boolean = false,
    onApply: (WidgetConfig) -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf(WidgetConfig()) }
    var cfgLoaded by remember { mutableStateOf(false) }
    var customSources by remember { mutableStateOf<List<SourceDef>>(emptyList()) }
    var tseCustomSymbols by remember { mutableStateOf<List<SymbolDef>>(emptyList()) }
    var sourceHealth by remember { mutableStateOf<List<SourceHealth>>(emptyList()) }
    var alertHistory by remember { mutableStateOf<List<AlertEvent>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSymbolSearchDialog by remember { mutableStateOf(false) }
    var alertDialogOpen by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<AlertRule?>(null) }

    // دکمه‌ی Back در زیرصفحه‌ها باید اول به منوی تنظیمات برگردد؛ در جریان افزودن
    // ویجت، فقط Back از خودِ منوی اصلی پایان موفق پیکربندی را اعلام می‌کند.
    BackHandler(enabled = section != null) { section = null }

    // ─── بکاپ و خواب موقت هشدارها ───
    var backupResult by remember { mutableStateOf("") }
    var snoozeUntil by remember { mutableStateOf(AlertEngine.snoozeUntil(context)) }

    val editingWidget = widgetId != 0

    // ─── ذخیره‌ی خودکار هر تغییر (با اسکوپ دائمی — با بسته شدن صفحه از بین نمی‌رود) ───

    fun persist(new: WidgetConfig) {
        // کلیک بسیار زودهنگام نباید پیش از پایان خواندن DataStore، تنظیمات قبلی را
        // با WidgetConfig پیش‌فرض بازنویسی کند.
        if (!cfgLoaded) return
        cfg = new
        ConfigStore.saveDebounced(context, new, widgetId)
    }

    fun persistAlerts(list: List<AlertRule>) {
        persist(cfg.copy(alerts = list))
    }

    /** افزودن یک نماد به نمادهای همین ویجت — با سقف MAX_SYMBOLS (آخرین حذف می‌شود) */
    fun addSymbol(newSym: SymbolDef) {
        if (cfg.symbols.any { it.code == newSym.code && it.sourceId == newSym.sourceId }) return
        val list = cfg.symbols.toMutableList()
        if (list.size >= MAX_SYMBOLS) list.removeAt(list.size - 1)
        list.add(newSym)
        persist(cfg.copy(symbols = list))
    }

    LaunchedEffect(widgetId) {
        try {
            cfg = ConfigStore.current(context, widgetId)
            customSources = ConfigStore.currentCustomSources(context)
            tseCustomSymbols = ConfigStore.currentTseSymbols(context)
            sourceHealth = SourceHealthStore.load(context)
            alertHistory = AlertHistoryStore.load(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // فایل/رام ناسازگار نباید صفحه را در شروع ببندد؛ پیش‌فرض امن قابل استفاده می‌ماند.
            testResult = "تنظیمات قبلی کامل خوانده نشد؛ برنامه با تنظیمات امن باز شد"
        } finally {
            cfgLoaded = true
        }
    }

    LaunchedEffect(section) {
        if (section == SettingsSection.ALERTS) alertHistory = AlertHistoryStore.load(context)
        if (section == SettingsSection.SOURCES || section == null) {
            sourceHealth = SourceHealthStore.load(context)
        }
    }

    // ─── اجازه‌ی اعلان (اندروید ۱۳ به بالا) ───

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    // درخواست مجوز فقط وقتی کاربر وارد یکی از بخش‌های اعلان‌دار می‌شود؛ درخواست ناگهانی در
    // اولین اجرای برنامه هم نرخ رد شدن را بالا می‌برد و هم با سیاست فروشگاه‌ها ناسازگار است.
    LaunchedEffect(section) {
        if ((section == SettingsSection.ALERTS || section == SettingsSection.PUMPS) &&
            Build.VERSION.SDK_INT >= 33
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted && context is ComponentActivity) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ─── لانچرهای فایل بکاپ (خروجی/ورودی) ───

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val text = runSuspendCatching { ConfigStore.exportAll(context) }.getOrNull()
                if (text == null) {
                    backupResult = "❌ خواندن تنظیمات برای خروجی ممکن نشد"
                } else {
                    runSuspendCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(text.toByteArray(Charsets.UTF_8))
                            } ?: error("جریان خروجی باز نشد")
                        }
                    }.onSuccess {
                        backupResult = "✅ بکاپ ذخیره شد — مواظب فایل باش!"
                    }.onFailure {
                        backupResult = "❌ نوشتن فایل ممکن نشد"
                    }
                }
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    val text = runSuspendCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { inp ->
                                inp.readUtf8Capped(MAX_BACKUP_BYTES)
                            } ?: error("جریان ورودی باز نشد")
                        }
                    }.getOrNull()
                    if (text == null) {
                        backupResult = "❌ خواندن فایل ممکن نشد"
                    } else {
                        val imported = runSuspendCatching {
                            ConfigStore.importAll(context, text)
                        }.getOrNull()
                        if (imported == null) {
                            backupResult = "❌ این فایل، بکاپ نبض بازار نیست یا خراب است"
                        } else {
                            backupResult = "✅ بازیابی شد (${Format.toPersianDigits("$imported")} ویجت) — تنظیمات تازه بارگذاری شد"
                            cfg = ConfigStore.current(context, widgetId)
                            customSources = ConfigStore.currentCustomSources(context)
                            tseCustomSymbols = ConfigStore.currentTseSymbols(context)
                            alertHistory = AlertHistoryStore.load(context)
                            StockWidgetProvider.requestUpdate(context)
                            // سرویس زنده/Worker هم مطابق تنظیمات بازیابی‌شده همگام شود.
                            StockWidgetProvider.syncLiveService(context)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    backupResult = "❌ بازیابی کامل نشد؛ فایل و فضای ذخیره‌سازی را بررسی کن"
                } finally {
                    busy = false
                }
            }
        }
    }

    val allSources = SourceCatalog.all(customSources)
    val selectedIds = cfg.activeSourceIds
    val selectedSources = allSources.filter { it.id in selectedIds }

    // نتیجه‌ی «تست داده» سه ثانیه بعد خودش پاک می‌شود
    LaunchedEffect(testResult) {
        if (testResult.isNotEmpty() && testResult != "در حال تست…") {
            delay(3000)
            testResult = ""
        }
    }

    // ─── اسکلت صفحه ───

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            section?.title ?: "تنظیمات ویجت",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                isAddFlow -> "ویجت تازه — با برگشتن هم اضافه می‌شود"
                                editingWidget -> "همین ویجت — روی دیگر ویجت‌ها اثر ندارد"
                                else -> "الگوی پیش‌فرض ویجت‌های تازه"
                            },
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (section != null) {
                        IconButton(onClick = { section = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
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

            // محتوا — منو یا بخش انتخاب‌شده
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (section) {

                    // ───── صفحه‌ی منو ─────
                    null -> LandingMenu(
                        cfg = cfg,
                        selectedSources = selectedSources,
                        healthySourceCount = sourceHealth.count { it.sourceId in selectedIds && it.isHealthy },
                        onOpen = { section = it }
                    )

                    SettingsSection.SOURCES -> SourcesCategory(
                        allSources = allSources,
                        selectedIds = selectedIds,
                        selectedSources = selectedSources,
                        health = sourceHealth,
                        busy = busy,
                        onToggle = { src ->
                            val ids = selectedIds.toMutableList()
                            if (src.id in ids) {
                                // تنها منبع باید روشن بماند؛ کلیک دوباره نباید نمادهایش را پاک کند.
                                if (ids.size > 1) {
                                    ids.remove(src.id)
                                    persist(
                                        cfg.copy(
                                            sourceIds = ids,
                                            sourceId = ids.first(),
                                            symbols = cfg.symbols.filterNot { it.sourceId == src.id }
                                        )
                                    )
                                }
                            } else if (ids.size < MAX_SYMBOLS) {
                                ids.add(src.id)
                                val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
                                persist(
                                    cfg.copy(
                                        sourceIds = ids,
                                        sourceId = ids.first(),
                                        symbols = cfg.symbols +
                                                src.symbols.take(minOf(room, maxOf(1, MAX_SYMBOLS / (ids.size)))).map {
                                                    it.copy(sourceId = src.id)
                                                }
                                    )
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
                                    persist(
                                        cfg.copy(
                                            sourceIds = ids,
                                            sourceId = ids.first(),
                                            symbols = cfg.symbols.filterNot { it.sourceId == src.id }
                                        )
                                    )
                                }
                            }
                        },
                        onAddClick = { showAddDialog = true },
                        onTestAll = {
                            scope.launch {
                                busy = true
                                try {
                                    for (source in selectedSources) {
                                        try {
                                            val symbols = cfg.symbolsOf(source.id).ifEmpty {
                                                source.symbols.take(1).map { it.copy(sourceId = source.id) }
                                            }
                                            if (symbols.isEmpty()) {
                                                SourceHealthStore.recordFailure(
                                                    context, source.id, "نمادی برای آزمایش منبع وجود ندارد"
                                                )
                                            } else {
                                                val started = System.currentTimeMillis()
                                                val quotes = Fetcher.fetchAll(source, symbols)
                                                SourceHealthStore.record(
                                                    context,
                                                    source.id,
                                                    quotes,
                                                    System.currentTimeMillis() - started,
                                                    Fetcher.lastEndpoint(source.id)
                                                )
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            SourceHealthStore.recordFailure(
                                                context, source.id, "آزمایش منبع کامل نشد"
                                            )
                                        }
                                    }
                                    sourceHealth = SourceHealthStore.load(context)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        onClear = {
                            SourceHealthStore.clear(context)
                            sourceHealth = emptyList()
                        }
                    )

                    SettingsSection.SYMBOLS -> SymbolsCategory(
                        selectedSources = selectedSources,
                        selectedSymbols = cfg.symbols,
                        tseCustomSymbols = tseCustomSymbols,
                        sortMode = cfg.sortMode,
                        onSortMode = { m -> persist(cfg.copy(sortMode = m)) },
                        rows = cfg.rows,
                        onRows = { n -> persist(cfg.copy(rows = n)) },
                        onToggle = { sym, src ->
                            val list = cfg.symbols.toMutableList()
                            val selected = list.any { it.code == sym.code && it.sourceId == src.id }
                            if (selected) {
                                list.removeAll { it.code == sym.code && it.sourceId == src.id }
                            } else if (list.size < MAX_SYMBOLS) {
                                list.add(sym.copy(sourceId = src.id))
                            }
                            persist(cfg.copy(symbols = list))
                        },
                        onRemoveSymbol = { index ->
                            val list = cfg.symbols.toMutableList()
                            val removed = list.getOrNull(index)
                            if (removed != null) {
                                list.removeAt(index)
                                persist(cfg.copy(symbols = list))
                                // حذف از ویجت = حذف از فهرست «نمادهای دلخواه بورس من» هم؛
                                // تا کادر پایین همان نماد را نشان ندهد و با خالی شدن، کل کادر برود
                                if (marketKindOf(removed.sourceId) == MarketKind.TSE &&
                                    tseCustomSymbols.any { it.code == removed.code }
                                ) {
                                    scope.launch {
                                        val updated = tseCustomSymbols.filterNot { it.code == removed.code }
                                        ConfigStore.saveTseSymbols(context, updated)
                                        tseCustomSymbols = updated
                                    }
                                }
                            }
                        },
                        onMoveSymbol = { from, to ->
                            val list = cfg.symbols.toMutableList()
                            if (from in list.indices && to in list.indices && from != to) {
                                val item = list.removeAt(from)
                                list.add(to, item)
                                persist(cfg.copy(symbols = list))
                            }
                        },
                        onSetBuyPrice = { index, price, date ->
                            val list = cfg.symbols.toMutableList()
                            list.getOrNull(index)?.let { sym ->
                                list[index] = sym.copy(buyPrice = price, buyDate = date)
                                persist(cfg.copy(symbols = list))
                            }
                        },
                        onDeleteTseSymbol = { sym ->
                            scope.launch {
                                val updated = tseCustomSymbols.filterNot { it.code == sym.code }
                                ConfigStore.saveTseSymbols(context, updated)
                                tseCustomSymbols = updated
                                // اگر در نمادهای این ویجت بود، از آنجا هم حذف می‌شود
                                val list = cfg.symbols.filterNot {
                                    it.code == sym.code && marketKindOf(it.sourceId) == MarketKind.TSE
                                }
                                if (list.size != cfg.symbols.size) {
                                    persist(cfg.copy(symbols = list))
                                }
                            }
                        },
                        onOpenSymbolSearch = { showSymbolSearchDialog = true }
                    )

                    SettingsSection.VALUES -> ValuesCategory(
                        cfg = cfg,
                        onChange = { new -> persist(new) }
                    )

                    SettingsSection.LOOK -> LookCategory(
                        cfg = cfg,
                        onChange = { new -> persist(new) }
                    )

                    SettingsSection.UPDATE -> UpdateCategory(
                        cfg = cfg,
                        onChange = { new -> persist(new) },
                        onLiveToggle = { on ->
                            if (cfgLoaded) {
                                val new = cfg.copy(liveService = on)
                                persist(new)
                                scope.launch {
                                    ConfigStore.save(context, new, widgetId)
                                    StockWidgetProvider.syncLiveService(context)
                                }
                            }
                        }
                    )

                    SettingsSection.BACKUP -> BackupCategory(
                        busy = busy,
                        result = backupResult,
                        onExport = { exportLauncher.launch("nabz-bazar-backup.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                    )

                    SettingsSection.ALERTS -> AlertsCategory(
                        cfg = cfg,
                        sources = selectedSources,
                        snoozeUntil = snoozeUntil,
                        onSnooze = { m ->
                            AlertEngine.snooze(context, m)
                            snoozeUntil = AlertEngine.snoozeUntil(context)
                        },
                        onCancelSnooze = {
                            AlertEngine.cancelSnooze(context)
                            snoozeUntil = 0L
                        },
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
                        onToggleVibrate = { on -> persist(cfg.copy(alertVibrate = on)) },
                        onOpenNotificationSettings = {
                            AlertEngine.openNotificationSettings(context, cfg.alertVibrate)
                        },
                        onTestNotification = { AlertEngine.notifyTest(context, cfg.alertVibrate) },
                        history = alertHistory,
                        onClearHistory = {
                            AlertHistoryStore.clear(context)
                            alertHistory = emptyList()
                        }
                    )

                    SettingsSection.PUMPS -> PumpsCategory(
                        cfg = cfg,
                        alertOwnerKey = "widget_$widgetId",
                        onChange = { new -> persist(new) },
                        onAlertToggle = { enabled ->
                            if (cfgLoaded) {
                                val new = cfg.copy(pumpAlertEnabled = enabled)
                                persist(new)
                                scope.launch {
                                    ConfigStore.save(context, new, widgetId)
                                    StockWidgetProvider.syncLiveService(context)
                                }
                            }
                        },
                        onAddSymbol = { sym ->
                            // منبع کوین (کریپتو) اگر روشن نبود، خودکار روشن می‌شود؛
                            // وگرنه نماد اضافه می‌شد ولی هیچ‌وقت داده نمی‌گرفت
                            val ids = if (sym.sourceId.isBlank() || sym.sourceId in cfg.activeSourceIds) {
                                cfg.activeSourceIds
                            } else {
                                val active = cfg.activeSourceIds.toMutableList()
                                if (active.size >= MAX_SYMBOLS) {
                                    val used = cfg.symbols.mapTo(mutableSetOf()) { it.sourceId }
                                    val removable = active.indexOfLast { it !in used }
                                    if (removable >= 0) active.removeAt(removable)
                                }
                                (active + sym.sourceId).distinct().take(MAX_SYMBOLS)
                            }
                            val list = cfg.symbols.toMutableList()
                            if (list.none { it.code == sym.code && it.sourceId == sym.sourceId }) {
                                if (list.size >= MAX_SYMBOLS) list.removeAt(list.size - 1)
                                list.add(sym)
                            }
                            persist(cfg.copy(sourceIds = ids, sourceId = ids.first(), symbols = list))
                        }
                    )

                    SettingsSection.PORTFOLIO -> PortfolioCategory(persian = cfg.persianDigits)

                    SettingsSection.ABOUT -> AboutCategory()
                }

                Spacer(Modifier.height(6.dp))
            }

            // نوار اقدام پایین — تغییرات خودکار ذخیره می‌شوند؛ این دکمه تأیید نهایی است
            Surface(
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (testResult.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                testResult,
                                modifier = Modifier.padding(12.dp),
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
                                    try {
                                        val byId = allSources.associateBy { it.id }
                                        val selected = cfg.symbols.take(MAX_SYMBOLS)
                                        val quoteByKey = mutableMapOf<Pair<String, String>, com.pulse.market.data.Quote>()
                                        val symbolsBySource = selected.groupBy { sym ->
                                            sym.sourceId.ifBlank { cfg.activeSourceIds.firstOrNull().orEmpty() }
                                        }
                                        for ((sourceId, symbols) in symbolsBySource) {
                                            byId[sourceId]?.let { source ->
                                                for (quote in Fetcher.fetchAll(source, symbols)) {
                                                    quoteByKey[sourceId to quote.code] = quote
                                                }
                                            }
                                        }
                                        val lines = selected.map { sym ->
                                            val sourceId = sym.sourceId.ifBlank {
                                                cfg.activeSourceIds.firstOrNull().orEmpty()
                                            }
                                            val q = quoteByKey[sourceId to sym.code]
                                            val vol = q?.let { "  حجم ${QuoteText.volume(it)}" } ?: ""
                                            if (q?.price != null)
                                                "${q.label}: ${QuoteText.priceWithUnit(q)}  ${QuoteText.change(q)}$vol"
                                            else
                                                "${sym.label}: خطا — ${q?.error ?: "منبع پیدا نشد"}"
                                        }
                                        testResult = lines.joinToString("\n")
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        testResult = "آزمایش داده کامل نشد؛ اتصال و تنظیمات منبع را بررسی کن"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("تست داده", fontSize = 12.5.sp)
                        }

                        Button(
                            onClick = {
                                busy = true
                                val finalConfig = cfg
                                scope.launch {
                                    try {
                                        // بستن صفحه فقط بعد از پایان ذخیره انجام می‌شود. قبلاً
                                        // onApply بیرون coroutine بود و با finish شدن Activity،
                                        // همین scope پیش از شروع ذخیره cancel می‌شد.
                                        withContext(NonCancellable) {
                                            ConfigStore.save(context, finalConfig, widgetId)
                                            StockWidgetProvider.syncLiveService(context)
                                        }
                                        busy = false
                                        onApply(finalConfig)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        testResult = "ذخیره کامل نشد؛ دوباره تلاش کن"
                                        busy = false
                                    }
                                }
                            },
                            enabled = cfgLoaded && !busy,
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    isAddFlow -> "ذخیره و افزودن ویجت"
                                    editingWidget -> "تأیید و بستن"
                                    else -> "ذخیره‌ی الگو"
                                },
                                fontSize = 12.5.sp
                            )
                        }
                    }
                    Text(
                        "تغییرات به‌صورت خودکار ذخیره می‌شوند",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    // منبع تازه تا سقف منابع قابل‌نمایش خودکار روشن و نمادهایش انتخاب شود.
                    val canActivate = cfg.activeSourceIds.size < MAX_SYMBOLS
                    val ids = if (canActivate) {
                        (cfg.activeSourceIds + newSource.id).distinct()
                    } else cfg.activeSourceIds
                    val room = (MAX_SYMBOLS - cfg.symbols.size).coerceAtLeast(0)
                    persist(
                        cfg.copy(
                            sourceIds = ids,
                            sourceId = ids.first(),
                            symbols = if (canActivate) {
                                cfg.symbols + newSource.symbols.take(room)
                                    .map { it.copy(sourceId = newSource.id) }
                            } else cfg.symbols
                        )
                    )
                    showAddDialog = false
                }
            }
        )
    }

    if (showSymbolSearchDialog) {
        SymbolSearchDialog(
            sources = selectedSources,
            selectedSymbols = cfg.symbols,
            tseCustomSymbols = tseCustomSymbols,
            onDismiss = { showSymbolSearchDialog = false },
            onAddSymbol = { newSym ->
                // فقط نمادهای بورس در فهرست «نمادهای دلخواه بورس من» ذخیره می‌شوند؛
                // نمادهای بقیه‌ی منابع فقط به همین ویجت اضافه می‌شوند
                if (marketKindOf(newSym.sourceId) == MarketKind.TSE) {
                    scope.launch {
                        val updatedCustom = (tseCustomSymbols + newSym).distinctBy { it.code }
                        ConfigStore.saveTseSymbols(context, updatedCustom)
                        tseCustomSymbols = updatedCustom
                        addSymbol(newSym)
                    }
                } else {
                    addSymbol(newSym)
                }
            },
            onRemoveSymbol = { sym ->
                val list = cfg.symbols.filterNot {
                    it.code == sym.code && (it.sourceId.isEmpty() || it.sourceId == sym.sourceId)
                }
                persist(cfg.copy(symbols = list))
            },
            onDeleteTseCustomSymbol = { sym ->
                scope.launch {
                    val updated = tseCustomSymbols.filterNot { it.code == sym.code }
                    ConfigStore.saveTseSymbols(context, updated)
                    tseCustomSymbols = updated
                    val list = cfg.symbols.filterNot {
                        it.code == sym.code && marketKindOf(it.sourceId) == MarketKind.TSE
                    }
                    persist(cfg.copy(symbols = list))
                }
            }
        )
    }

    if (alertDialogOpen) {
        AddAlertDialog(
            sources = selectedSources,
            existing = editingAlert,
            widgetSymbols = cfg.symbols,
            onDismiss = { alertDialogOpen = false; editingAlert = null },
            onSave = { rule ->
                val list = cfg.alerts.filterNot { it.id == rule.id } + rule
                persistAlerts(list)
                // اگر نماد هشدار در این ویجت نیست، اضافه‌اش کن تا هشدار قابل بررسی باشد
                val already = cfg.symbols.any {
                    it.code == rule.symbolCode &&
                            (it.sourceId.isEmpty() || it.sourceId == rule.sourceId)
                }
                if (!already && cfg.symbols.size < MAX_SYMBOLS) {
                    persist(
                        cfg.copy(
                            symbols = cfg.symbols + SymbolDef(rule.symbolCode, rule.symbolLabel, rule.sourceId)
                        )
                    )
                }
                alertDialogOpen = false
                editingAlert = null
            }
        )
    }
}

// ═══════════════════ صفحه‌ی منو ═══════════════════

@Composable
private fun LandingMenu(
    cfg: WidgetConfig,
    selectedSources: List<SourceDef>,
    healthySourceCount: Int,
    onOpen: (SettingsSection) -> Unit
) {
    // خلاصه‌ی زنده‌ی هر بخش — بدون شلوغی، فقط آنچه لازم است
    val sourcesSummary = selectedSources.joinToString("، ") { it.title.substringBefore(" —") }
        .ifBlank { "انتخاب نشده" }
    val symbolsSummary = if (cfg.symbols.isEmpty()) "خالی"
    else cfg.symbols.joinToString("، ") { it.label }
    val valuesSummary = buildList {
        add("قیمت")
        if (cfg.showChange) add("تغییر")
        if (cfg.showVolume) add("حجم")
        if (cfg.showSparkline) add("نمودار")
    }.joinToString("، ") + " …"
    val lookSummary = themeName(cfg.theme) + " • فونت ×" +
            Format.toPersianDigits(String.format(Locale.US, "%.2f", cfg.fontScale))
    val updateSummary = if (cfg.liveService)
        "زنده • هر ${Format.toPersianDigits("${cfg.intervalSec}")} ثانیه"
    else "دستی (بدون به‌روزرسانی خودکار)"
    val alertsSummary = when {
        cfg.alerts.isEmpty() -> "بدون هشدار"
        else -> "${Format.toPersianDigits("${cfg.alerts.count { it.enabled }}")} هشدار فعال از ${Format.toPersianDigits("${cfg.alerts.size}")}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RowsCard {
            NavMenuRow(
                icon = Icons.Default.CloudQueue,
                tint = Color(0xFF38BDF8),
                title = SettingsSection.SOURCES.title,
                summary = if (selectedSources.isEmpty()) sourcesSummary else
                    "$sourcesSummary • ${Format.toPersianDigits(healthySourceCount.toString())} منبع سالم از ${Format.toPersianDigits(selectedSources.size.toString())}"
            ) { onOpen(SettingsSection.SOURCES) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.ShowChart,
                tint = Color(0xFF22C55E),
                title = SettingsSection.SYMBOLS.title,
                summary = symbolsSummary
            ) { onOpen(SettingsSection.SYMBOLS) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.Tune,
                tint = Color(0xFFA78BFA),
                title = SettingsSection.VALUES.title,
                summary = valuesSummary
            ) { onOpen(SettingsSection.VALUES) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.Palette,
                tint = Color(0xFFF59E0B),
                title = SettingsSection.LOOK.title,
                summary = lookSummary
            ) { onOpen(SettingsSection.LOOK) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.Schedule,
                tint = Color(0xFF22D3EE),
                title = SettingsSection.UPDATE.title,
                summary = updateSummary
            ) { onOpen(SettingsSection.UPDATE) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.NotificationsActive,
                tint = Color(0xFFF43F5E),
                title = SettingsSection.ALERTS.title,
                summary = alertsSummary
            ) { onOpen(SettingsSection.ALERTS) }
            if (cfg.showPumps) {
                RowDivider()
                NavMenuRow(
                    icon = Icons.Default.TrendingUp,
                    tint = Color(0xFFFB923C),
                    title = SettingsSection.PUMPS.title,
                    summary = "کوین‌های در حال رشد شارپ + آموزش پامپ"
                ) { onOpen(SettingsSection.PUMPS) }
            }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.AccountBalanceWallet,
                tint = Color(0xFF22C55E),
                title = SettingsSection.PORTFOLIO.title,
                summary = "کوین‌هایی که خرید آزمایشی زده‌ای + سود و زیان"
            ) { onOpen(SettingsSection.PORTFOLIO) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.SettingsBackupRestore,
                tint = Color(0xFF34D399),
                title = SettingsSection.BACKUP.title,
                summary = "خروجی و بازیابی همه‌ی تنظیمات"
            ) { onOpen(SettingsSection.BACKUP) }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.Info,
                tint = Color(0xFF94A3B8),
                title = SettingsSection.ABOUT.title,
                summary = "حامد سرگلی • hamedsargoli.ir"
            ) { onOpen(SettingsSection.ABOUT) }
        }
    }
}

private fun themeName(theme: WidgetTheme): String = when (theme) {
    WidgetTheme.DARK -> "تیره"
    WidgetTheme.LIGHT -> "روشن"
    WidgetTheme.GLASS -> "شیشه‌ای"
    WidgetTheme.OCEAN -> "اقیانوس"
    WidgetTheme.MOCHA -> "موکا"
}

// ═══════════════════ درباره‌ی اپ ═══════════════════

/** صفحه‌ی «درباره» — هویت برنامه + سازنده + راه‌های ارتباطی (لینک‌ها واقعی‌اند) */
@Composable
private fun AboutCategory() {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.2"
    }

    // وضعیت بررسی/دانلود/نصب آپدیت — نسخه‌ی جدید روی همین نصب نصب می‌شود؛ پاک کردن لازم نیست.
    // حالت‌ها: idle, checking, uptodate, norelease, error, newer,
    //          downloading, needperm, installing, downloaderror
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf("idle") }
    var latest by remember { mutableStateOf<AppUpdater.LatestRelease?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

    // یک دکمه: دانلودِ APK از ریلیز رسمی، سپس بازکردنِ نصب‌کننده‌ی سیستم (با گرفتنِ اجازه‌ی نصب در صورت نیاز).
    val downloadAndInstall: (String) -> Unit = { url ->
        scope.launch {
            updateState = "downloading"
            downloadProgress = null
            val file = runCatching {
                AppInstaller.downloadApk(context, url, latest?.apkSha256) { p -> downloadProgress = p }
            }.getOrNull()
            if (file == null) {
                updateState = "downloaderror"
            } else {
                downloadedFile = file
                if (AppInstaller.canInstall(context)) {
                    AppInstaller.launchInstaller(context, file)
                    updateState = "installing"
                } else {
                    updateState = "needperm"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // ── هویت برنامه ──
        RowsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    "نبض بازار",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "ویجت زنده‌ی قیمت — سهام، کریپتو، طلا و ارز",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "نسخه‌ی ${Format.toPersianDigits(versionName)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ── به‌روزرسانی برنامه — نسخه‌ی جدید روی همین نصب می‌نشیند ──
        SectionHeader("به‌روزرسانی برنامه")
        RowsCard {
            SettingRow(
                title = "نسخه‌ی نصب‌شده: ${Format.toPersianDigits(versionName)}",
                desc = when (updateState) {
                    "checking" -> "در حال بررسی نسخه‌ی جدید…"
                    "newer" -> "✨ نسخه‌ی ${latest?.tag ?: ""} آماده است — با یک دکمه دانلود و نصب می‌شود"
                    "downloading" -> downloadProgress?.let {
                        "در حال دانلود… ${Format.toPersianDigits("${(it * 100).toInt()}")}٪"
                    } ?: "در حال دانلود…"
                    "needperm" -> "برای نصب خودکار، اجازه‌ی «نصب برنامه‌های ناشناس» را روشن کن"
                    "installing" -> "نصب‌کننده باز شد — ادامه‌ی نصب را تأیید کن"
                    "downloaderror" -> "دانلود ممکن نشد — اینترنت را چک کن و دوباره بزن"
                    "uptodate" -> "✅ آخرین نسخه را داری"
                    "norelease" -> "هنوز ریلیز رسمی منتشر نشده"
                    "error" -> "ممکن نشد — اینترنت را چک کن و دوباره بزن"
                    else -> "از همین‌جا نسخه‌ی جدید را چک، دانلود و نصب کن؛ نیازی به مرورگر یا پاک کردن نیست"
                }
            ) {
                when (updateState) {
                    "newer" -> if (latest?.apkUrl != null) {
                        Button(onClick = { downloadAndInstall(latest!!.apkUrl!!) }) {
                            Text("⬇ دانلود و نصب", fontSize = 12.sp)
                        }
                    }
                    "downloading" -> {
                        // پیشرفت پایین‌تر نشان داده می‌شود؛ اینجا دکمه‌ای نیست.
                    }
                    "needperm" -> Button(onClick = {
                        if (AppInstaller.canInstall(context)) {
                            downloadedFile?.let {
                                AppInstaller.launchInstaller(context, it)
                                updateState = "installing"
                            }
                        } else {
                            AppInstaller.openInstallPermissionSettings(context)
                        }
                    }) { Text("اجازه و نصب", fontSize = 12.sp) }
                    "installing" -> Button(onClick = {
                        downloadedFile?.let { AppInstaller.launchInstaller(context, it) }
                    }) { Text("نصب دوباره", fontSize = 12.sp) }
                    "downloaderror" -> if (latest?.apkUrl != null) {
                        Button(onClick = { downloadAndInstall(latest!!.apkUrl!!) }) {
                            Text("تلاش دوباره", fontSize = 12.sp)
                        }
                    }
                    else -> OutlinedButton(
                        enabled = updateState != "checking",
                        onClick = {
                            scope.launch {
                                updateState = "checking"
                                val rel = AppUpdater.fetchLatest()
                                when {
                                    rel == null -> updateState = "error"
                                    rel.tag.isBlank() -> updateState = "norelease"
                                    AppUpdater.isNewer(rel.version, AppUpdater.parseVersion(versionName)) -> {
                                        latest = rel
                                        updateState = "newer"
                                    }

                                    else -> updateState = "uptodate"
                                }
                            }
                        }
                    ) { Text("بررسی", fontSize = 12.sp) }
                }
            }
            if (updateState == "downloading") {
                RowDivider()
                InnerRow {
                    val p = downloadProgress
                    if (p == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "پس از دانلود، نصب‌کننده‌ی اندروید خودش باز می‌شود.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (latest != null &&
                updateState in listOf("newer", "downloading", "needperm", "installing", "downloaderror")
            ) {
                RowDivider()
                InnerRow {
                    Text(
                        "✨ نسخه‌ی ${latest!!.tag}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (latest!!.notes.isNotBlank()) {
                        Text(
                            latest!!.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6
                        )
                    }
                    // اثر انگشت فایل نصبی — پس از دانلود خودکار تطبیق داده می‌شود؛ اینجا هم
                    // نشان داده می‌شود تا کاربر در صورت تمایل خودش تطبیق دهد (قابل انتخاب/کپی).
                    latest!!.apkSha256?.let { sha ->
                        SelectionContainer {
                            Text(
                                "اثر انگشت فایل (SHA-256):\n$sha",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "فایل از ریلیز رسمیِ همین مخزن دانلود و اثر انگشتش خودکار بررسی می‌شود؛ " +
                                "سپس نصب‌کننده‌ی اندروید باز می‌شود و امضای نسخه را تطبیق می‌دهد (پاک کردن لازم نیست).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── سازنده ──
        SectionHeader("سازنده")
        RowsCard {
            SettingRow(
                title = "حامد سرگلی",
                desc = "طراحی و توسعه‌ی اپلیکیشن"
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── راه‌های ارتباطی ──
        SectionHeader("راه‌های ارتباطی")
        RowsCard {
            NavMenuRow(
                icon = Icons.Default.Language,
                tint = Color(0xFF38BDF8),
                title = "وب‌سایت",
                summary = "hamedsargoli.ir"
            ) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hamedsargoli.ir")))
                }
            }
            RowDivider()
            NavMenuRow(
                icon = Icons.Default.Phone,
                tint = Color(0xFF22C55E),
                title = "تلفن همراه",
                summary = Format.toPersianDigits("09126368924")
            ) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:09126368924")))
                }
            }
        }

        Hint("ساخته‌شده با ❤ برای رصد لحظه‌ای بازار")
    }
}


/** سقف فایل بازیابی؛ تنظیمات عادی چند کیلوبایت‌اند و فایل غول‌آسا نباید حافظه را پر کند. */
private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

private suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

private fun InputStream.readUtf8Capped(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) error("حجم فایل بکاپ بیش از حد مجاز است")
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
