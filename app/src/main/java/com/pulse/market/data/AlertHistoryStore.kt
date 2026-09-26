package com.pulse.market.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** یک هشدار که واقعاً به NotificationManager تحویل داده شده است. */
@Serializable
data class AlertEvent(
    val id: String,
    val ruleId: String,
    val symbolCode: String,
    val symbolLabel: String,
    val sourceId: String,
    val condition: AlertCondition,
    val threshold: Double,
    val price: Double? = null,
    val changePct: Double? = null,
    val volume: Double? = null,
    /** برای VOLUME_SPIKE درصد جهش محاسبه‌شده نسبت به نمونه‌ی قبلی است. */
    val observedValue: Double? = null,
    val unit: String = "",
    val triggeredAt: Long = System.currentTimeMillis()
)

/** تاریخچه‌ی محلی هشدارها؛ هیچ داده‌ای از دستگاه خارج نمی‌شود. */
object AlertHistoryStore {
    private const val PREF = "pulse_alert_history"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(AlertEvent.serializer())

    @Synchronized
    fun load(context: Context): List<AlertEvent> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .filter { it.triggeredAt > 0L && it.threshold.isFinite() }
            .sortedByDescending { it.triggeredAt }
            .take(MAX_EVENTS)
    }

    @Synchronized
    fun add(context: Context, event: AlertEvent) {
        val updated = (listOf(event) + load(context))
            .distinctBy { it.id }
            .take(MAX_EVENTS)
        replace(context, updated)
    }

    @Synchronized
    fun replace(context: Context, events: List<AlertEvent>) {
        val safe = events.asSequence()
            .filter { it.id.isNotBlank() && it.symbolCode.isNotBlank() && it.threshold.isFinite() }
            .map {
                it.copy(
                    id = it.id.take(200),
                    ruleId = it.ruleId.take(200),
                    symbolCode = it.symbolCode.take(200),
                    symbolLabel = it.symbolLabel.take(200),
                    sourceId = it.sourceId.take(200),
                    price = it.price?.takeIf(Double::isFinite),
                    changePct = it.changePct?.takeIf(Double::isFinite),
                    volume = it.volume?.takeIf(Double::isFinite),
                    observedValue = it.observedValue?.takeIf(Double::isFinite),
                    unit = it.unit.take(40)
                )
            }
            .sortedByDescending { it.triggeredAt }
            .take(MAX_EVENTS)
            .toList()
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_EVENTS, json.encodeToString(serializer, safe))
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).apply()
    }
}
