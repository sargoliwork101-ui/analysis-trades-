package com.pulse.market.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "pulse_settings")

/** ذخیره‌ی تنظیمات ویجت (منبع، نمادها، فاصله‌ی زمانی، تم) */
object ConfigStore {

    private val KEY_CONFIG = stringPreferencesKey("widget_config")
    private val KEY_CUSTOM_SOURCES = stringPreferencesKey("custom_sources")
    private val KEY_TSE_SYMBOLS = stringPreferencesKey("tse_custom_symbols")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun configFlow(context: Context): Flow<WidgetConfig> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_CONFIG]?.let {
                runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull()
            } ?: WidgetConfig()
        }.map { migrate(it) }

    /** سازگاری با تنظیمات قدیمی: تک‌منبعی ← چندمنبعی + نام‌گذاری منبع نمادها */
    private fun migrate(cfg: WidgetConfig): WidgetConfig {
        val ids = cfg.sourceIds.ifEmpty { listOf(cfg.sourceId) }
        val symbols = cfg.symbols.map { s ->
            if (s.sourceId.isEmpty()) s.copy(sourceId = ids.first()) else s
        }
        return cfg.copy(sourceIds = ids, symbols = symbols)
    }

    suspend fun current(context: Context): WidgetConfig = configFlow(context).first()

    suspend fun save(context: Context, cfg: WidgetConfig) {
        context.dataStore.edit { it[KEY_CONFIG] = json.encodeToString(WidgetConfig.serializer(), cfg) }
    }

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

    /** همه‌ی منابع = آماده‌های داخل اپ + منابع دلخواه کاربر */
    suspend fun resolveSource(context: Context, id: String): SourceDef? =
        SourceCatalog.byId(id) ?: currentCustomSources(context).firstOrNull { it.id == id }
}
