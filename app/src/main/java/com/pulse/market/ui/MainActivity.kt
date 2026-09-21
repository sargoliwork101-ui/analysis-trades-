package com.pulse.market.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pulse.market.data.AlertEngine
import com.pulse.market.data.AlertRule
import com.pulse.market.data.ConfigStore
import com.pulse.market.data.Fetcher
import com.pulse.market.data.SourceCatalog
import com.pulse.market.data.SourceDef
import com.pulse.market.data.SymbolDef
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.service.LiveUpdateService
import com.pulse.market.service.LiveUpdateWorker
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var widgetId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0

        setContent {
            PulseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        fromWidget = widgetId != 0,
                        onApply = { cfg ->
                            finishConfigure(cfg)
                        }
                    )
                }
            }
        }
    }

    /** اگر از دکمه‌ی «افزودن ویجت» باز شده باشیم، باید نتیجه را به لانچر برگردانیم */
    private fun finishConfigure(cfg: WidgetConfig) {
        StockWidgetProvider.requestUpdate(this)
        if (widgetId != 0) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(fromWidget: Boolean, onApply: (WidgetConfig) -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cfg by remember { mutableStateOf(WidgetConfig()) }
    var customSources by remember { mutableStateOf<List<SourceDef>>(emptyList()) }
    var tseCustomSymbols by remember { mutableStateOf<List<SymbolDef>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showTseSearchDialog by remember { mutableStateOf(false) }
    var alertDialogOpen by remember { mutableStateOf(false) }
    var editingAlert by remember { mutableStateOf<AlertRule?>(null) }

    // هشدارها را بلافاصله ذخیره کن تا بدون زدن دکمه‌ی ذخیره هم کار کنند
    fun persistAlerts(list: List<AlertRule>) {
        cfg = cfg.copy(alerts = list)
        scope.launch { ConfigStore.save(context, cfg.copy(alerts = list)) }
    }

    LaunchedEffect(Unit) {
        cfg = ConfigStore.current(context)
        customSources = ConfigStore.currentCustomSources(context)
        tseCustomSymbols = ConfigStore.currentTseSymbols(context)
    }

    // اجازه‌ی اعلان برای سرویس حالت زنده
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    context as ComponentActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    991
                )
            }
        }
    }

    val allSources = SourceCatalog.all(customSources)
    val currentSource = allSources.firstOrNull { it.id == cfg.sourceId } ?: allSources.first()

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            if (fromWidget) {
                InfoCard("ویجت را روی صفحه بچین، منبع و نمادها را انتخاب کن و «ذخیره» را بزن.")
            }

            // ─── ۱) انتخاب منبع داده ───
            SectionTitle("۱. منبع داده")
            allSources.forEach { src ->
                SourceCard(
                    src = src,
                    selected = src.id == cfg.sourceId,
                    onClick = {
                        cfg = cfg.copy(
                            sourceId = src.id,
                            symbols = src.symbols.take(cfg.rows)
                        )
                    },
                    onDelete = if (!src.builtIn) {
                        {
                            scope.launch {
                                val list = customSources.filterNot { it.id == src.id }
                                ConfigStore.saveCustomSources(context, list)
                                customSources = list
                                if (cfg.sourceId == src.id) cfg = cfg.copy(sourceId = SourceCatalog.builtIn.first().id)
                            }
                        }
                    } else null
                )
            }
            OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("افزودن منبع دلخواه (آدرس سایت + مسیر داده)")
            }

            // ─── ۲) انتخاب نمادها ───
            SectionTitle("۲. نمادهایی که نمایش داده شوند")

            val isTseSource = currentSource.id == "tse_tsetmc"
            val effectiveSymbols = if (isTseSource) {
                val combined = (currentSource.symbols + tseCustomSymbols).distinctBy { it.code }
                combined
            } else {
                currentSource.symbols
            }

            if (isTseSource) {
                Button(
                    onClick = { showTseSearchDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("🔍 جستجو در بورس تهران یا افزودن با لینک TSETMC")
                }
                Text(
                    "می‌توانید هر نماد دلخواهی (اهرم، عیار، طلا، خودرو...) یا لینک صفحه tsetmc.com را جستجو و اضافه کنید.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }

            if (effectiveSymbols.isEmpty()) {
                Text(
                    "این منبع نماد آماده ندارد؛ نمادها را در پنجره‌ی منبع دلخواه وارد کن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    "نمادهای انتخاب‌شده برای ویجت (${cfg.symbols.size} از حداکثر ۴ نماد):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    effectiveSymbols.forEach { sym ->
                        val selected = cfg.symbols.any { it.code == sym.code }
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val list = cfg.symbols.toMutableList()
                                if (selected) list.removeAll { it.code == sym.code }
                                else if (list.size < 4) list.add(sym)
                                cfg = cfg.copy(symbols = list)
                            },
                            label = { Text(sym.label) }
                        )
                    }
                }
            }

            // ─── ۳) تنظیمات زنده ───
            SectionTitle("۳. زمان‌بندی و ظاهر")

            SettingRow("به‌روزرسانی خودکار (حالت زنده)") {
                Switch(
                    checked = cfg.liveService,
                    onCheckedChange = { on ->
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
            }

            Text(
                "فاصله‌ی به‌روزرسانی: ${cfg.intervalSec} ثانیه",
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = cfg.intervalSec.toFloat(),
                onValueChange = { cfg = cfg.copy(intervalSec = it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                enabled = cfg.liveService
            )
            Text(
                "پیشنهاد: کریپتو و سهام آمریکا ۱۰ تا ۳۰ ثانیه • بورس تهران ۶۰ ثانیه (دیتای TSETMC با تأخیر می‌آید)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingRow("تعداد ردیف‌ها (${cfg.rows})") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n ->
                        FilterChip(
                            selected = cfg.rows == n,
                            onClick = { cfg = cfg.copy(rows = n) },
                            label = { Text(Format.toPersianDigits("$n")) }
                        )
                    }
                }
            }

            SettingRow("نمودار مینیاتوری") {
                Switch(
                    checked = cfg.showSparkline,
                    onCheckedChange = { cfg = cfg.copy(showSparkline = it) }
                )
            }

            SettingRow("اعداد فارسی") {
                Switch(
                    checked = cfg.persianDigits,
                    onCheckedChange = { cfg = cfg.copy(persianDigits = it) }
                )
            }

            Text("تم ویجت", color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    WidgetTheme.DARK to "تیره",
                    WidgetTheme.LIGHT to "روشن",
                    WidgetTheme.AMOLED to "AMOLED"
                ).forEach { (t, label) ->
                    FilterChip(
                        selected = cfg.theme == t,
                        onClick = { cfg = cfg.copy(theme = t) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("تم روشن روی تم روشن گوشی، AMOLED برای مصرف کمتر باتری.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ─── ۴) هشدار قیمت ───
            SectionTitle("۴. هشدار قیمت (نوتیف)")
            InfoCard(
                "برای هر نماد می‌توانی شرط بگذاری (بالاتر از X، پایین‌تر از X، یا درصد رشد/افت) و " +
                        "تعیین کنی فقط در چه ساعتی و چه روزهایی فعال باشد. " +
                        "بررسی هشدارها همراه با هر بار تازه شدن قیمت‌ها انجام می‌شود، پس حالت زنده را روشن نگه دار."
            )

            cfg.alerts.forEach { rule ->
                AlertRuleCard(
                    rule = rule,
                    persian = cfg.persianDigits,
                    onToggle = { on ->
                        persistAlerts(cfg.alerts.map { if (it.id == rule.id) it.copy(enabled = on) else it })
                    },
                    onEdit = { editingAlert = rule; alertDialogOpen = true },
                    onDelete = { persistAlerts(cfg.alerts.filterNot { it.id == rule.id }) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { editingAlert = null; alertDialogOpen = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("افزودن هشدار")
                }
                OutlinedButton(
                    onClick = { AlertEngine.notifyTest(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("تست نوتیف")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // ─── ۵) تست و ذخیره ───
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !busy && cfg.symbols.isNotEmpty(),
                    onClick = {
                        busy = true
                        testResult = "در حال تست…"
                        scope.launch {
                            val src = ConfigStore.resolveSource(context, cfg.sourceId)
                            val lines = cfg.symbols.take(4).map { sym ->
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
                    Text("تست داده")
                }

                Button(
                    onClick = {
                        scope.launch {
                            busy = true
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
                    Icon(if (cfg.liveService) Icons.Default.PlayArrow else Icons.Default.Stop, null)
                    Spacer(Modifier.padding(3.dp))
                    Text("ذخیره و به‌روزرسانی")
                }
            }

            if (testResult.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        testResult,
                        modifier = Modifier.padding(14.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newSource ->
                scope.launch {
                    val list = customSources + newSource
                    ConfigStore.saveCustomSources(context, list)
                    customSources = list
                    cfg = cfg.copy(sourceId = newSource.id, symbols = newSource.symbols)
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
                    if (!currentList.any { it.code == newSym.code }) {
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
            source = currentSource,
            existing = editingAlert,
            onDismiss = { alertDialogOpen = false; editingAlert = null },
            onSave = { rule ->
                val list = cfg.alerts.filterNot { it.id == rule.id } + rule
                persistAlerts(list)
                // اگر شهروند نماد در ویجت نیست، اضافه‌اش کن تا هشدار قابل بررسی باشد
                if (cfg.symbols.none { it.code == rule.symbolCode } && cfg.symbols.size < 4) {
                    cfg = cfg.copy(symbols = cfg.symbols + SymbolDef(rule.symbolCode, rule.symbolLabel))
                    scope.launch { ConfigStore.save(context, cfg) }
                }
                alertDialogOpen = false
                editingAlert = null
            }
        )
    }
}

/** کارت یک قانون هشدار در لیست */
@Composable
private fun AlertRuleCard(
    rule: AlertRule,
    persian: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🔔 ${rule.symbolLabel}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    AlertRule.conditionText(rule.condition, rule.threshold, "", persian),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    rule.scheduleText(persian) +
                            if (rule.cooldownMin > 1) " • هر ${Format.toPersianDigits(rule.cooldownMin.toString())} دقیقه" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف هشدار")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
    }
}

@Composable
private fun SettingRow(title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        trailing()
    }
}

@Composable
private fun SourceCard(
    src: SourceDef,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    src.title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                if (src.subtitle.isNotEmpty()) {
                    Text(
                        src.subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف منبع")
                }
            }
        }
    }
}
