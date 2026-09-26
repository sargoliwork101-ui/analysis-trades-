package com.pulse.market.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(
    name = "pulse_settings",
    // خرابی فایل تنظیمات نباید کل برنامه/ویجت را برای همیشه از کار بیندازد.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** فایل تنظیمات: الگوی پیش‌فرض + پیکربندی مستقل هر ویجت */
@Serializable
data class WidgetsFile(
    val template: WidgetConfig = WidgetConfig(),
    /** کلید = شماره‌ی ویجت (به‌صورت متن) */
    val widgets: Map<String, WidgetConfig> = emptyMap()
)

/**
 * ذخیره‌ی تنظیمات — هر ویجت پیکربندی مستقل خودش را دارد:
 * - widgetId=0 یعنی «الگوی پیش‌فرض» برای ویجت‌های تازه
 * - هر شماره‌ی دیگر = تنظیمات همان ویجت روی صفحه‌ی اصلی
 */
object ConfigStore {

    private val KEY_CONFIG = stringPreferencesKey("widget_config")      // قدیمی (تک‌تنظیماتی)
    private val KEY_WIDGETS = stringPreferencesKey("widgets_config")    // جدید (هر ویجت جدا)

    /**
     * اسکوپ دائمی ذخیره‌ی تنظیمات — با بسته شدن صفحه کنسل نمی‌شود تا
     * تنظیمات هر ویجت همیشه واقعاً ذخیره بماند.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * ذخیره‌ی معوق هر ویجت — «کلید = شماره‌ی ویجت».
     *
     * چرا نقشه و نه یک متغیر تنها: قبلاً فقط یک Job نگه داشته می‌شد؛ اگر کاربر
     * در فاصله‌ی کمتر از ۲۰۰ میلی‌ثانیه دو ویجت مختلف را تغییر می‌داد (یا الگو و
     * یک ویجت را هم‌زمان)، Job اولی cancel می‌شد و تنظیمات آن **گم می‌شد**.
     * حالا فقط ذخیره‌ی همان ویجت جایگزین می‌شود.
     */
    private val pendingSaves = java.util.concurrent.ConcurrentHashMap<Int, Job>()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ───────────── بارگذاری/ذخیره ─────────────

    private fun loadFile(prefs: Preferences): WidgetsFile {
        prefs[KEY_WIDGETS]?.let {
            runCatching { json.decodeFromString(WidgetsFile.serializer(), it) }.getOrNull()
        }?.let { file ->
            return file.copy(
                template = migrate(file.template),
                widgets = file.widgets.mapValues { migrate(it.value) }
            )
        }
        // مهاجرت از نسخه‌ی قدیمی (یک تنظیمات مشترک برای همه)
        val legacy = prefs[KEY_CONFIG]?.let {
            runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull()
        } ?: WidgetConfig()
        return WidgetsFile(template = migrate(legacy))
    }

    /**
     * سازگاری با فرمت‌های قدیمی + نرمال‌سازی داده‌ی خوانده‌شده از بکاپ.
     * UI خودش بازه‌ها را محدود می‌کند، اما JSON دست‌کاری‌شده نباید interval منفی،
     * NaN، هزاران نماد یا روز/ساعت نامعتبر را وارد سرویس و ویجت کند.
     */
    private fun migrate(cfg: WidgetConfig): WidgetConfig {
        val ids = cfg.sourceIds.ifEmpty { listOf(cfg.sourceId) }
            .map { it.trim().take(200) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(100)
            .ifEmpty { listOf("crypto_coingecko") }
        val symbols = cfg.symbols.asSequence()
            .filter { it.code.isNotBlank() }
            .map { s ->
                val sid = s.sourceId.trim().ifBlank { ids.first() }
                s.copy(
                    code = s.code.trim().take(200),
                    label = s.label.trim().ifBlank { s.code.trim() }.take(200),
                    sourceId = sid.take(200),
                    unit = s.unit.trim().take(40),
                    scale = s.scale?.takeIf { it.isFinite() && it > 0.0 }
                )
            }
            .distinctBy { it.sourceId to it.code }
            .take(MAX_SYMBOLS)
            .toList()
        val alerts = cfg.alerts.asSequence()
            .filter { it.id.isNotBlank() && it.symbolCode.isNotBlank() && it.threshold.isFinite() }
            .map { rule ->
                rule.copy(
                    id = rule.id.trim().take(200),
                    symbolCode = rule.symbolCode.trim().take(200),
                    symbolLabel = rule.symbolLabel.trim().ifBlank { rule.symbolCode.trim() }.take(200),
                    sourceId = rule.sourceId.trim().ifBlank { ids.first() }.take(200),
                    threshold = when (rule.condition) {
                        AlertCondition.PCT_UP, AlertCondition.PCT_DOWN,
                        AlertCondition.VOLUME_SPIKE -> kotlin.math.abs(rule.threshold)
                        else -> rule.threshold
                    },
                    fromMinute = rule.fromMinute.coerceIn(0, 1439),
                    toMinute = rule.toMinute.coerceIn(0, 1439),
                    days = rule.days.filterTo(mutableSetOf()) { it in 0..6 },
                    cooldownMin = rule.cooldownMin.coerceIn(1, 7 * 24 * 60)
                )
            }
            .distinctBy { it.id }
            .take(100)
            .toList()
        val safeFontScale = cfg.fontScale.takeIf { it.isFinite() }?.coerceIn(0.75f, 1.5f) ?: 1.0f
        val safePumpChange = cfg.pumpMinChange.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 8.0
        return cfg.copy(
            sourceId = ids.first(),
            sourceIds = ids,
            symbols = symbols,
            alerts = alerts,
            intervalSec = cfg.intervalSec.coerceIn(5, 3600),
            fontScale = safeFontScale,
            sparkPoints = cfg.sparkPoints.coerceIn(6, 60),
            rows = cfg.rows.coerceIn(1, MAX_SYMBOLS),
            refreshFromMinute = cfg.refreshFromMinute.coerceIn(0, 1439),
            refreshToMinute = cfg.refreshToMinute.coerceIn(0, 1439),
            pumpUniverse = cfg.pumpUniverse.coerceIn(10, 250),
            pumpMinChange = safePumpChange,
            pumpAlertCooldownMin = cfg.pumpAlertCooldownMin.coerceIn(15, 24 * 60),
            title = cfg.title.take(200)
        )
    }

    private fun WidgetsFile.forWidget(widgetId: Int): WidgetConfig =
        if (widgetId == 0) template else widgets[widgetId.toString()] ?: template

    // ───────────── پیکربندی هر ویجت ─────────────

    fun configFlow(context: Context, widgetId: Int = 0): Flow<WidgetConfig> =
        context.dataStore.data.map { loadFile(it).forWidget(widgetId) }

    suspend fun current(context: Context, widgetId: Int = 0): WidgetConfig =
        configFlow(context, widgetId).first()

    suspend fun save(context: Context, cfg: WidgetConfig, widgetId: Int = 0) {
        val migrated = migrate(cfg)
        context.dataStore.edit { prefs ->
            val file = loadFile(prefs)
            val newFile = if (widgetId == 0) {
                file.copy(template = migrated)
            } else {
                file.copy(widgets = file.widgets + (widgetId.toString() to migrated))
            }
            prefs[KEY_WIDGETS] = json.encodeToString(WidgetsFile.serializer(), newFile)
        }
    }

    /**
     * ذخیره‌ی خودکار و فوری تنظیمات (با debounce کوتاه برای اسلایدرها).
     * حتی اگر صفحه بلافاصله بسته شود، ذخیره انجام می‌شود.
     *
     * debounce «هر ویجت جداست» — تغییر هم‌زمان دو ویجت، هیچ‌کدام را گم نمی‌کند.
     */
    fun saveDebounced(context: Context, cfg: WidgetConfig, widgetId: Int = 0, delayMs: Long = 200) {
        enqueueSave(context, cfg, widgetId, delayMs)
    }

    /** ذخیره‌ی فوری بدون debounce — برای دکمه‌ی ذخیره */
    fun saveNow(context: Context, cfg: WidgetConfig, widgetId: Int = 0) {
        enqueueSave(context, cfg, widgetId, 0)
    }

    @Synchronized
    private fun enqueueSave(context: Context, cfg: WidgetConfig, widgetId: Int, delayMs: Long) {
        // فقط نوبتِ همان ویجت جایگزین می‌شود؛ نوبت ویجت‌های دیگر دست‌نخورده می‌ماند.
        pendingSaves.remove(widgetId)?.cancel()
        // LAZY مهم است: Job باید پیش از شروع در نقشه ثبت شود؛ در حالت delay=0 ممکن
        // بود coroutine زودتر تمام شود و Job تکمیل‌شده برای همیشه در نقشه بماند.
        val job = ioScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (delayMs > 0) delay(delayMs)
                save(context.applicationContext, cfg, widgetId)
            } finally {
                val mine = currentCoroutineContext()[Job]
                if (mine != null) pendingSaves.remove(widgetId, mine)
            }
        }
        pendingSaves[widgetId] = job
        job.start()
    }

    /** صبر تا آخرین ذخیره‌ی خودکارِ همین ویجت؛ برای بستن امن جریان پیکربندی. */
    suspend fun awaitPending(widgetId: Int) {
        while (true) {
            val pending = pendingSaves[widgetId] ?: return
            pending.join()
            // اگر هنگام انتظار، نوبت تازه‌تری جایگزین شد، همان را هم منتظر بمان.
            if (pendingSaves[widgetId] == null) return
        }
    }

    /** صبر تا همه‌ی ذخیره‌های debounce شده؛ خروجی بکاپ نباید آخرین تغییر را جا بیندازد. */
    private suspend fun awaitAllPending() {
        while (true) {
            val snapshot = pendingSaves.values.toList()
            if (snapshot.isEmpty()) return
            snapshot.forEach { it.join() }
            if (pendingSaves.isEmpty()) return
        }
    }

    /** پاک کردن تنظیمات ویجتِ حذف‌شده از صفحه */
    suspend fun deleteWidget(context: Context, widgetId: Int) {
        cancelPending(widgetId)
        context.dataStore.edit { prefs ->
            val file = loadFile(prefs)
            if (file.widgets.containsKey(widgetId.toString())) {
                prefs[KEY_WIDGETS] = json.encodeToString(
                    WidgetsFile.serializer(),
                    file.copy(widgets = file.widgets - widgetId.toString())
                )
            }
        }
    }

    // ───────────── منابع و نمادهای سفارشی (سراسری) ─────────────

    private val KEY_CUSTOM_SOURCES = stringPreferencesKey("custom_sources")
    private val KEY_TSE_SYMBOLS = stringPreferencesKey("tse_custom_symbols")
    private val KEY_WATCHLISTS = stringPreferencesKey("named_watchlists")

    fun customSourcesFlow(context: Context): Flow<List<SourceDef>> =
        context.dataStore.data.map { prefs ->
            val decoded = prefs[KEY_CUSTOM_SOURCES]?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(SourceDef.serializer()), it)
                }.getOrNull()
            } ?: emptyList()
            // داده‌ی نسخه‌های قدیمی هم هنگام خواندن امن و محدود شود، نه فقط ذخیره‌ی تازه.
            sanitizeCustom(decoded)
        }

    suspend fun currentCustomSources(context: Context): List<SourceDef> = customSourcesFlow(context).first()

    suspend fun saveCustomSources(context: Context, list: List<SourceDef>) {
        val safe = sanitizeCustom(list)
        context.dataStore.edit {
            it[KEY_CUSTOM_SOURCES] = json.encodeToString(ListSerializer(SourceDef.serializer()), safe)
        }
    }

    /**
     * پاک‌سازی منابع دلخواه پیش از ذخیره — دو چیز را تضمین می‌کند:
     * ۱) `builtIn = false` بماند؛ وگرنه فایل بکاپِ دست‌کاری‌شده می‌تواند منبعی
     *    بسازد که در UI دکمه‌ی حذف نداشته باشد.
     * ۲) هیچ منبع دلخواهی id منبع آماده‌ی داخلی را نگیرد (مثل «tgju» یا «tradingview»)؛
     *    وگرنه در فهرست دو منبع هم‌نام دیده می‌شود و سردرگمی می‌سازد.
     */
    private fun sanitizeCustom(list: List<SourceDef>): List<SourceDef> =
        list.asSequence()
            .filter {
                it.id.isNotBlank() && SourceCatalog.byId(it.id.trim()) == null &&
                        isWebUrl(it.urlTemplate)
            }
            .map { source ->
                source.copy(
                    id = source.id.trim().take(200),
                    title = source.title.trim().ifBlank { "منبع دلخواه" }.take(200),
                    subtitle = source.subtitle.take(300),
                    urlTemplate = source.urlTemplate.trim().take(4096),
                    batchTemplate = source.batchTemplate?.trim()?.take(4096)
                        ?.takeIf(::isWebUrl),
                    urlFallbacks = source.urlFallbacks.map { it.trim().take(4096) }
                        .filter(::isWebUrl).distinct().take(5),
                    scale = source.scale.takeIf { it.isFinite() && it > 0.0 } ?: 1.0,
                    unit = source.unit.trim().take(40),
                    symbols = source.symbols.asSequence()
                        .filter { it.code.isNotBlank() }
                        .map { symbol ->
                            symbol.copy(
                                code = symbol.code.trim().take(200),
                                label = symbol.label.trim().ifBlank { symbol.code.trim() }.take(200),
                                sourceId = "",
                                unit = symbol.unit.trim().take(40),
                                scale = symbol.scale?.takeIf { it.isFinite() && it > 0.0 }
                            )
                        }
                        .distinctBy { it.code }
                        .take(500)
                        .toList(),
                    headers = source.headers.entries.take(30).associate {
                        it.key.take(200) to it.value.take(2000)
                    },
                    builtIn = false
                )
            }
            .distinctBy { it.id }
            .take(100)
            .toList()

    private fun isWebUrl(url: String): Boolean {
        val clean = url.trim()
        return clean.startsWith("https://") || clean.startsWith("http://")
    }

    fun tseSymbolsFlow(context: Context): Flow<List<SymbolDef>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_TSE_SYMBOLS]?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(SymbolDef.serializer()), it)
                }.getOrNull()
            } ?: emptyList()
        }

    suspend fun currentTseSymbols(context: Context): List<SymbolDef> = tseSymbolsFlow(context).first()

    suspend fun saveTseSymbols(context: Context, list: List<SymbolDef>) {
        context.dataStore.edit {
            it[KEY_TSE_SYMBOLS] = json.encodeToString(ListSerializer(SymbolDef.serializer()), list)
        }
    }

    // ───────────── واچ‌لیست‌های نام‌دار (سراسری) ─────────────

    fun watchlistsFlow(context: Context): Flow<List<Watchlist>> =
        context.dataStore.data.map { prefs ->
            val decoded = prefs[KEY_WATCHLISTS]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(Watchlist.serializer()), raw)
                }.getOrNull()
            } ?: emptyList()
            sanitizeWatchlists(decoded)
        }

    suspend fun currentWatchlists(context: Context): List<Watchlist> = watchlistsFlow(context).first()

    suspend fun saveWatchlists(context: Context, list: List<Watchlist>) {
        val safe = sanitizeWatchlists(list)
        context.dataStore.edit {
            it[KEY_WATCHLISTS] = json.encodeToString(ListSerializer(Watchlist.serializer()), safe)
        }
    }

    private fun sanitizeWatchlists(list: List<Watchlist>): List<Watchlist> =
        list.asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .map { watchlist ->
                val ids = watchlist.sourceIds.map { it.trim().take(200) }
                    .filter { it.isNotBlank() }.distinct().take(20)
                val symbols = watchlist.symbols.asSequence()
                    .filter { it.code.isNotBlank() }
                    .map { symbol ->
                        symbol.copy(
                            code = symbol.code.trim().take(200),
                            label = symbol.label.trim().ifBlank { symbol.code.trim() }.take(200),
                            sourceId = symbol.sourceId.trim().take(200),
                            unit = symbol.unit.trim().take(40),
                            scale = symbol.scale?.takeIf { it.isFinite() && it > 0.0 }
                        )
                    }
                    .distinctBy { it.sourceId to it.code }
                    .take(MAX_SYMBOLS)
                    .toList()
                watchlist.copy(
                    id = watchlist.id.trim().take(200),
                    name = watchlist.name.trim().take(80),
                    sourceIds = (ids + symbols.map { it.sourceId }.filter { it.isNotBlank() })
                        .distinct().take(20),
                    symbols = symbols,
                    updatedAt = watchlist.updatedAt.coerceAtLeast(0L)
                )
            }
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
            .take(30)
            .toList()

    @Synchronized
    private fun cancelPending(widgetId: Int) {
        pendingSaves.remove(widgetId)?.cancel()
    }

    @Synchronized
    private fun cancelAllPending() {
        pendingSaves.values.forEach { it.cancel() }
        pendingSaves.clear()
    }

    // ───────────── بکاپ و بازگردانی ─────────────

    /** بکاپ تنظیمات، منابع، نمادهای بورس، واچ‌لیست‌ها و تاریخچه‌ی هشدارها */
    suspend fun exportAll(context: Context): String {
        awaitAllPending()
        val prefs = context.dataStore.data.first()
        val dump = BackupDump(
            exportedAt = System.currentTimeMillis(),
            widgets = loadFile(prefs),
            customSources = prefs[KEY_CUSTOM_SOURCES]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(SourceDef.serializer()), raw)
                }.getOrNull()
            } ?: emptyList(),
            tseSymbols = prefs[KEY_TSE_SYMBOLS]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(SymbolDef.serializer()), raw)
                }.getOrNull()
            } ?: emptyList(),
            watchlists = prefs[KEY_WATCHLISTS]?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(Watchlist.serializer()), raw)
                }.getOrNull()
            } ?: emptyList(),
            alertHistory = AlertHistoryStore.load(context)
        )
        return json.encodeToString(BackupDump.serializer(), dump)
    }

    /**
     * بازیابی از فایل بکاپ — تنظیمات فعلی کاملاً جایگزین می‌شود.
     * برمی‌گرداند: تعداد ویجت‌های بازیابی‌شده.
     */
    suspend fun importAll(context: Context, text: String): Int {
        val dump = json.decodeFromString(BackupDump.serializer(), text.trim())
        if (dump.version != 1) error("نسخه‌ی فایل بکاپ پشتیبانی نمی‌شود")
        // ذخیره‌ی debounce شده‌ی صفحه نباید بعد از بازیابی، فایل تازه را دوباره با
        // تنظیمات قدیمی بازنویسی کند.
        cancelAllPending()
        val safeSources = sanitizeCustom(dump.customSources)
        val safeWidgets = WidgetsFile(
            template = migrate(dump.widgets.template),
            widgets = dump.widgets.widgets.entries.asSequence()
                .filter { (key, _) -> key.toIntOrNull()?.let { it > 0 } == true }
                .take(200)
                .associate { (key, value) -> key to migrate(value) }
        )
        val safeWatchlists = sanitizeWatchlists(dump.watchlists)
        val safeTseSymbols = dump.tseSymbols.asSequence()
            .filter { it.code.isNotBlank() }
            .map {
                it.copy(
                    code = it.code.trim().take(200),
                    label = it.label.trim().ifBlank { it.code.trim() }.take(200),
                    sourceId = "tse_tsetmc",
                    unit = it.unit.trim().take(40),
                    scale = it.scale?.takeIf { value -> value.isFinite() && value > 0.0 }
                )
            }
            .distinctBy { it.code }
            .take(500)
            .toList()
        context.dataStore.edit { prefs ->
            prefs[KEY_WIDGETS] = json.encodeToString(WidgetsFile.serializer(), safeWidgets)
            prefs[KEY_CUSTOM_SOURCES] =
                json.encodeToString(ListSerializer(SourceDef.serializer()), safeSources)
            prefs[KEY_TSE_SYMBOLS] =
                json.encodeToString(ListSerializer(SymbolDef.serializer()), safeTseSymbols)
            prefs[KEY_WATCHLISTS] =
                json.encodeToString(ListSerializer(Watchlist.serializer()), safeWatchlists)
        }
        AlertHistoryStore.replace(context, dump.alertHistory)
        return safeWidgets.widgets.size
    }

    /** همه‌ی منابع = آماده‌های داخل اپ + منابع دلخواه کاربر */
    suspend fun resolveSource(context: Context, id: String): SourceDef? =
        SourceCatalog.byId(id) ?: currentCustomSources(context).firstOrNull { it.id == id }
}

/** قالب فایل بکاپ نبض بازار */
@Serializable
private data class BackupDump(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val widgets: WidgetsFile,
    val customSources: List<SourceDef> = emptyList(),
    val tseSymbols: List<SymbolDef> = emptyList(),
    val watchlists: List<Watchlist> = emptyList(),
    val alertHistory: List<AlertEvent> = emptyList()
)
