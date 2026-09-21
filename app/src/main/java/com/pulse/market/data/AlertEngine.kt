package com.pulse.market.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pulse.market.R
import com.pulse.market.ui.Format
import com.pulse.market.ui.MainActivity
import java.util.Calendar
import kotlin.math.abs

/**
 * موتور هشدار قیمت.
 *
 * هر بار که قیمت‌ها تازه می‌شوند (سرویس زنده، رفرش دستی یا Worker) این موتور صدا زده می‌شود و
 * بررسی می‌کند کدام قانون‌ها «الان» و «در بازه‌ی زمانی خودشان» برقرار شده‌اند و نوتیف می‌فرستد.
 *
 * منطق ضد‌اسپم: هر قانون یک «آخرین قیمت دیده‌شده» و «آخرین زمان نوتیف» دارد؛
 * به‌صورت پیش‌فرض فقط لحظه‌ی عبور از حد نوتیف می‌رود و بین دو نوتیف حداقل cooldownMin فاصله است.
 */
object AlertEngine {

    private const val PREF = "pulse_alerts"
    private const val CHANNEL_ID = "pulse_price_alerts"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    suspend fun evaluate(context: Context, cfg: WidgetConfig, quotes: List<Quote>) {
        val rules = cfg.alerts.filter { it.enabled }
        if (rules.isEmpty() || quotes.isEmpty()) return

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dayIndex = persianDayIndex(cal.get(Calendar.DAY_OF_WEEK))

        for (rule in rules) {
            val quote = quotes.firstOrNull { it.code.equals(rule.symbolCode, ignoreCase = true) }
                ?: continue
            val price = quote.price ?: continue

            // ── زمان‌بندی: آیا همین حالا این هشدار مجاز است؟ ──
            if (rule.scheduleEnabled) {
                if (rule.days.isNotEmpty() && dayIndex !in rule.days) continue
                if (!rule.noTimeLimit && !insideWindow(minuteOfDay, rule.fromMinute, rule.toMinute)) continue
            }

            val triggered = isTriggered(rule, price, quote.changePct)

            val keyPrice = "last_price_${rule.id}"
            val keyNotified = "last_notified_${rule.id}"
            val previous = prefs(context).getString(keyPrice, null)?.toDoubleOrNull()

            // ۱) آیا شرط برقرار است؟  ۲) اگر «فقط لحظه‌ی عبور» است، قبلاً برقرار نبوده باشد  ۳) کول‌داون رد شده باشد
            var shouldNotify = triggered
            if (shouldNotify && rule.onlyOnCross && previous != null) {
                shouldNotify = !isTriggered(rule, previous, quote.changePct)
            }
            if (shouldNotify) {
                val lastNotified = prefs(context).getLong(keyNotified, 0L)
                shouldNotify = now - lastNotified >= rule.cooldownMin.coerceAtLeast(1) * 60_000L
            }

            if (shouldNotify) {
                notify(context, cfg, rule, quote)
                prefs(context).edit().putLong(keyNotified, now).apply()
            }
            prefs(context).edit().putString(keyPrice, price.toString()).apply()
        }
    }

    private fun isTriggered(rule: AlertRule, price: Double, changePct: Double?): Boolean = when (rule.condition) {
        AlertCondition.ABOVE -> price >= rule.threshold
        AlertCondition.BELOW -> price <= rule.threshold
        AlertCondition.PCT_UP -> changePct != null && changePct >= abs(rule.threshold)
        AlertCondition.PCT_DOWN -> changePct != null && changePct <= -abs(rule.threshold)
    }

    /** بازه‌ی زمانی معمولی و بازه‌های شب‌گذر (مثلاً ۲۲:۰۰ تا ۰۶:۰۰) */
    private fun insideWindow(minute: Int, from: Int, to: Int): Boolean =
        if (from <= to) minute in from..to else (minute >= from || minute <= to)

    /** شنبه=۰ ... جمعه=۶ */
    private fun persianDayIndex(calendarDay: Int): Int = when (calendarDay) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        else -> 6
    }

    // ─────────────────────── نوتیف ───────────────────────

    fun notify(context: Context, cfg: WidgetConfig, rule: AlertRule, quote: Quote) {
        ensureChannel(context)

        val priceText = Format.price(quote.price, cfg.persianDigits)
        val unit = quote.unit.ifBlank { "" }
        val title = when (rule.condition) {
            AlertCondition.ABOVE -> "🔔 ${rule.symbolLabel} از حد گذشت"
            AlertCondition.BELOW -> "🔻 ${rule.symbolLabel} زیر حد آمد"
            AlertCondition.PCT_UP -> "🚀 رشد ${rule.symbolLabel}"
            AlertCondition.PCT_DOWN -> "⚠️ افت ${rule.symbolLabel}"
        }

        val thresholdText = when (rule.condition) {
            AlertCondition.ABOVE, AlertCondition.BELOW ->
                "$priceText $unit (حد: ${Format.price(rule.threshold, cfg.persianDigits)} $unit)"
            AlertCondition.PCT_UP, AlertCondition.PCT_DOWN ->
                "${Format.pct(quote.changePct, cfg.persianDigits)} (حد: ${Format.price(abs(rule.threshold), cfg.persianDigits)}٪)"
        }

        val open = PendingIntent.getActivity(
            context, rule.id.hashCode(),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(thresholdText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$thresholdText\nبازه‌ی فعال: ${rule.scheduleText()}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(rule.id.hashCode(), notification)
        }
    }

    /** برای دکمه‌ی «تست هشدار» در تنظیمات */
    fun notifyTest(context: Context) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle("🔔 نوتیف تستی")
            .setContentText("اگر این را می‌بینی، هشدارها درست کار می‌کنند.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(999, n)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "هشدار قیمت", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "وقتی نماد به حدی که تعیین کرده‌ای رسید"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
