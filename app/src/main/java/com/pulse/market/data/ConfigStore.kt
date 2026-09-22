package com.pulse.market.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "pulse_settings")

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
    private var pendingSave: Job? = null

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

    private suspend fun snapshot(context: Context): WidgetsFile =
        context.dataStore.data.map { loadFile(it) }.first()

    /** سازگاری با فرمت‌های قدیمی: تک‌منبعی ← چندمنبعی + نام‌گذاری منبع نمادها */
    private fun migrate(cfg: WidgetConfig): WidgetConfig {
        val ids = cfg.sourceIds.ifEmpty { listOf(cfg.sourceId) }
        val symbols = cfg.symbols.map { s ->
            if (s.sourceId.isEmpty()) s.copy(sourceId = ids.first()) else s
        }
        return cfg.copy(sourceIds = ids, symbols = symbols)
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
     */
    fun saveDebounced(context: Context, cfg: WidgetConfig, widgetId: Int = 0, delayMs: Long = 200) {
        pendingSave?.cancel()
        pendingSave = ioScope.launch {
            if (delayMs > 0) delay(delayMs)
            save(context, cfg, widgetId)
        }
    }

    /** ذخیره‌ی فوری بدون debounce — برای دکمه‌ی ذخیره */
    fun saveNow(context: Context, cfg: WidgetConfig, widgetId: Int = 0) {
        pendingSave?.cancel()
        pendingSave = ioScope.launch { save(context, cfg, widgetId) }
    }

    /** پاک کردن تنظیمات ویجتِ حذف‌شده از صفحه */
    suspend fun deleteWidget(context: Context, widgetId: Int) {
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

    /** آیا هیچ ویجتی (یا الگو) حالت زنده را می‌خواهد؟ */
    suspend fun anyLive(context: Context): Boolean {
        val f = snapshot(context)
        return f.template.liveService || f.widgets.values.any { it.liveService }
    }

    /** کمینه‌ی فاصله‌ی به‌روزرسانی بین ویجت‌های زنده */
    suspend fun minLiveInterval(context: Context): Int {
        val f = snapshot(context)
        val all = listOf(f.template) + f.widgets.values
        val live = all.filter { it.liveService }.ifEmpty { all }
        return live.minOf { it.intervalSec }.coerceIn(5, 3600)
    }

    // ───────────── منابع و نمادهای سفارشی (سراسری) ─────────────

    private val KEY_CUSTOM_SOURCES = stringPreferencesKey("custom_sources")
    private val KEY_TSE_SYMBOLS = stringPreferencesKey("tse_custom_symbols")

    fun customSourcesFlow(context: Context): Flow<List<SourceDef>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_CUSTOM_SOURCES]?.let {
                runCatching {
                    json.decodeFromString(ListSerializer(SourceDef.serializer()), it)
                }.getOrNull()
            } ?: emptyList()
        }

    suspend fun currentCustomSources(context: Context): List<SourceDef> = customSourcesFlow(context).first()

    suspend fun saveCustomSources(context: Context, list: List<SourceDef>) {
        context.dataStore.edit {
            it[KEY_CUSTOM_SOURCES] = json.encodeToString(ListSerializer(SourceDef.serializer()), list)
        }
    }

    /** ذخیره‌ی منابع دلخواه بدون نیاز به زنده ماندن صفحه */
    fun saveCustomSourcesAsync(context: Context, list: List<SourceDef>) {
        ioScope.launch { saveCustomSources(context, list) }
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

    /** ذخیره‌ی نمادهای دلخواه بورس بدون نیاز به زنده ماندن صفحه */
    fun saveTseSymbolsAsync(context: Context, list: List<SymbolDef>) {
        ioScope.launch { saveTseSymbols(context, list) }
    }

    // ───────────── بکاپ و بازگردانی ─────────────

    /** ساخت فایل بکاپ JSON از همه‌ی تنظیمات (ویجت‌ها + الگو + منابع دلخواه + نمادهای بورس) */
    suspend fun exportAll(context: Context): String {
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
            } ?: emptyList()
        )
        return json.encodeToString(BackupDump.serializer(), dump)
    }

    /**
     * بازیابی از فایل بکاپ — تنظیمات فعلی کاملاً جایگزین می‌شود.
     * برمی‌گرداند: تعداد ویجت‌های بازیابی‌شده.
     */
    suspend fun importAll(context: Context, text: String): Int {
        val dump = json.decodeFromString(BackupDump.serializer(), text.trim())
        context.dataStore.edit { prefs ->
            prefs[KEY_WIDGETS] = json.encodeToString(WidgetsFile.serializer(), dump.widgets)
            prefs[KEY_CUSTOM_SOURCES] =
                json.encodeToString(ListSerializer(SourceDef.serializer()), dump.customSources)
            prefs[KEY_TSE_SYMBOLS] =
                json.encodeToString(ListSerializer(SymbolDef.serializer()), dump.tseSymbols)
        }
        return dump.widgets.widgets.size
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
    val tseSymbols: List<SymbolDef> = emptyList()
)
