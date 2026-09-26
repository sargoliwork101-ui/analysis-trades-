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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // کانال جدید — الگوی ویبره‌ی قدیمیِ طولانی برای همه تعویض شود
    private const val CHANNEL_ID = "pulse_price_alerts_v2"

    /** یک «تپ» کوتاه و محتاطانه (۷۰ میلی‌ثانیه) — بدون ویبره‌ی طولانی و تکراری */
    private val VIBRATION_PATTERN = longArrayOf(0L, 70L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** جلوگیری از دو نوتیف تکراری وقتی سرویس، Worker و رسیور هم‌زمان ارزیابی می‌کنند. */
    private val evaluateMutex = Mutex()

    suspend fun evaluate(context: Context, cfg: WidgetConfig, quotes: List<Quote>) {
        if (!cfg.showNotification) return
        evaluateMutex.withLock { evaluateLocked(context, cfg, quotes) }
    }

    private fun evaluateLocked(context: Context, cfg: WidgetConfig, quotes: List<Quote>) {
        val rules = cfg.alerts.filter { it.enabled }
        if (rules.isEmpty() || quotes.isEmpty()) return

        // خواب موقت (میتینگ/شب): در این بازه هیچ هشداری بررسی و نوتیفی نمی‌رود
        if (isSnoozed(context)) return

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dayIndex = persianDayIndex(cal.get(Calendar.DAY_OF_WEEK))

        // همه‌ی «مقدار آخرین‌بار دیده‌شده» و «آخرین نوتیف» در یک تراکنش نوشته می‌شوند.
        val store = prefs(context)
        val editor = store.edit()
        var dirty = false

        for (rule in rules) {
            val quote = quotes.firstOrNull {
                it.code.equals(rule.symbolCode, ignoreCase = true) &&
                        (rule.sourceId.isEmpty() || it.sourceId.isEmpty() || it.sourceId == rule.sourceId)
            } ?: continue
            val price = quote.price?.takeIf { it.isFinite() } ?: continue

            // مجموعه‌ی روز خالی یعنی هیچ روزی؛ قبلاً اشتباهاً مثل «همه‌ی روزها» عمل می‌کرد.
            if (!AlertLogic.isInsideSchedule(rule, dayIndex, minuteOfDay)) continue

            // برای هشدار درصدی باید «درصد قبلی» ذخیره شود، نه قیمت قبلی. در غیر این
            // صورت onlyOnCross بعد از نخستین ارزیابی دیگر هیچ عبور درصدی را نمی‌دید.
            val metric = AlertLogic.metric(rule, price, quote.changePct) ?: continue
            val triggered = AlertLogic.isTriggered(rule, metric)
            val keyMetric = "last_metric_${rule.id}_${rule.condition.name}"
            val keyNotified = "last_notified_${rule.id}"
            val previous = store.getString(keyMetric, null)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }

            // ۱) شرط برقرار باشد  ۲) در حالت عبور، نوبت قبل برقرار نبوده باشد
            // ۳) فاصله‌ی ضداسپم تمام شده باشد.
            var shouldNotify = triggered
            if (shouldNotify && rule.onlyOnCross && previous != null) {
                shouldNotify = !AlertLogic.isTriggered(rule, previous)
            }
            if (shouldNotify) {
                val lastNotified = store.getLong(keyNotified, 0L)
                shouldNotify = now - lastNotified >= rule.cooldownMin.coerceAtLeast(1) * 60_000L
            }

            if (shouldNotify) {
                notify(context, cfg, rule, quote)
                editor.putLong(keyNotified, now)
            }
            editor.putString(keyMetric, metric.toString())
            dirty = true
        }

        if (dirty) editor.apply()
    }

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

    // ───────────── خواب موقت هشدارها (میتینگ/شب) ─────────────

    private const val KEY_SNOOZE = "snooze_until"

    fun snooze(context: Context, minutes: Int) {
        prefs(context).edit()
            .putLong(KEY_SNOOZE, System.currentTimeMillis() + minutes * 60_000L)
            .apply()
    }

    fun cancelSnooze(context: Context) {
        prefs(context).edit().remove(KEY_SNOOZE).apply()
    }

    /** ۰ یعنی بیدار؛ غیره = زمان پایان خواب (timestamp) */
    fun snoozeUntil(context: Context): Long =
        prefs(context).getLong(KEY_SNOOZE, 0L).takeIf { it > System.currentTimeMillis() } ?: 0L

    fun isSnoozed(context: Context): Boolean = snoozeUntil(context) > 0L

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
            .setVibrate(VIBRATION_PATTERN)
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
            .setVibrate(VIBRATION_PATTERN)
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
            vibrationPattern = VIBRATION_PATTERN
        }
        manager.createNotificationChannel(channel)
    }
}
