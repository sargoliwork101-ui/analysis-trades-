package com.pulse.market.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulse.market.R
import com.pulse.market.service.AlertActionReceiver
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
 * منطق ضد‌اسپم: هر قانون یک «آخرین مقدار دیده‌شده» و «آخرین زمان نوتیف» دارد؛
 * به‌صورت پیش‌فرض فقط لحظه‌ی عبور از حد نوتیف می‌رود و بین دو نوتیف حداقل cooldownMin فاصله است.
 */
object AlertEngine {

    private const val PREF = "pulse_alerts"
    // دو کانال: یکی با لرزش، یکی بی‌لرزش. در اندروید ۸+ لرزش را کانال تعیین می‌کند
    // (نه تک‌تک نوتیف‌ها)، پس برای اختیاری‌کردنِ لرزش باید کانالِ مناسب انتخاب شود.
    private const val CHANNEL_ID = "pulse_price_alerts_v2"
    private const val CHANNEL_ID_SILENT = "pulse_price_alerts_novib_v2"

    /** پیش‌فرض «یادآوری بعداً» روی نوتیف (دقیقه) */
    const val SNOOZE_DEFAULT_MIN = 15

    private fun channelId(vibrate: Boolean) = if (vibrate) CHANNEL_ID else CHANNEL_ID_SILENT

    /** یک «تپ» کوتاه و محتاطانه (۷۰ میلی‌ثانیه) — بدون ویبره‌ی طولانی و تکراری */
    private val VIBRATION_PATTERN = longArrayOf(0L, 70L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun safeOwner(ownerKey: String): String = ownerKey
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(80)
        .ifBlank { "default" }

    private fun metricKey(owner: String, rule: AlertRule): String =
        "last_metric_${safeOwner(owner)}_${rule.id}_${rule.condition.name}"

    private fun notifiedKey(owner: String, ruleId: String): String =
        "last_notified_${safeOwner(owner)}_$ruleId"

    /** شناسه‌ی تاریخچه باید owner را هم داشته باشد تا قوانین کپی‌شده حذف نشوند. */
    internal fun eventId(ownerKey: String, ruleId: String, triggeredAt: Long): String =
        "event_${safeOwner(ownerKey)}_${ruleId.take(200)}_$triggeredAt"

    /**
     * state عبور/cooldown فقط برای قوانین ویجت‌های نصب‌شده نگه داشته می‌شود.
     * حذف ویجت یا قانون نباید کلیدهای SharedPreferences را برای همیشه باقی بگذارد.
     */
    @Synchronized
    fun pruneState(context: Context, activeRulesByOwner: Map<String, List<AlertRule>>) {
        val keep = buildSet {
            for ((owner, rules) in activeRulesByOwner) {
                for (rule in rules) {
                    add(metricKey(owner, rule))
                    add(notifiedKey(owner, rule.id))
                }
            }
        }
        val store = prefs(context)
        val stale = store.all.keys.filter { key ->
            (key.startsWith("last_metric_") || key.startsWith("last_notified_")) && key !in keep
        }
        if (stale.isNotEmpty()) {
            store.edit().also { editor ->
                for (key in stale) editor.remove(key)
            }.apply()
        }
    }

    /** جلوگیری از دو نوتیف تکراری وقتی سرویس، Worker و رسیور هم‌زمان ارزیابی می‌کنند. */
    private val evaluateMutex = Mutex()

    suspend fun evaluate(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        quotes: List<Quote>
    ) {
        if (!cfg.showNotification) return
        // اگر کاربر مجوز/اعلان‌های برنامه را بسته، عبور از حد را «تحویل‌شده» ثبت نکن؛
        // بعد از فعال‌کردن اعلان‌ها باید هشدار جاری امکان نمایش داشته باشد.
        // روی بعضی پروفایل‌ها/رام‌ها این سرویس null است و دسترسی مستقیم NPE می‌داد.
        val manager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager?.getNotificationChannel(channelId(cfg.alertVibrate))?.importance == NotificationManager.IMPORTANCE_NONE
        ) return
        evaluateMutex.withLock { evaluateLocked(context, ownerKey, cfg, quotes) }
    }

    private fun evaluateLocked(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        quotes: List<Quote>
    ) {
        val rules = cfg.alerts.filter { it.enabled }
        if (rules.isEmpty() || quotes.isEmpty()) return

        val now = System.currentTimeMillis()
        val safeOwner = safeOwner(ownerKey)
        val snoozed = isSnoozed(context)
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
            // ruleهای کپی‌شده از template در چند ویجت id یکسان دارند؛ owner باید
            // بخشی از کلید باشد تا crossing/cooldown دو ویجت روی هم اثر نگذارند.
            val keyMetric = metricKey(safeOwner, rule)
            val keyNotified = notifiedKey(safeOwner, rule.id)
            val previous = store.getString(keyMetric, null)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() }

            // هشدار جهش حجم، حجم خام دو نمونه‌ی متوالی را نگه می‌دارد و درصد جهش را
            // محاسبه می‌کند. بقیه‌ی شرط‌ها قیمت/درصد هم‌نوع خودشان را مقایسه می‌کنند.
            val storedMetric: Double
            val observed: Double
            val triggered: Boolean
            if (rule.condition == AlertCondition.VOLUME_SPIKE) {
                storedMetric = quote.volume?.takeIf { it.isFinite() && it >= 0.0 } ?: continue
                observed = AlertLogic.volumeSpikePercent(previous, storedMetric) ?: 0.0
                triggered = previous != null && AlertLogic.isTriggered(rule, observed)
            } else {
                storedMetric = AlertLogic.metric(rule, price, quote.changePct) ?: continue
                observed = storedMetric
                triggered = AlertLogic.isTriggered(rule, observed)
            }

            // نمونه‌ی جاری حتی در حالت خواب یا خارج از برنامه‌ی زمانی ثبت می‌شود تا
            // پس از بیدارشدن، اعلان دیرهنگام یا جهش حجم کاذب ساخته نشود.
            editor.putString(keyMetric, storedMetric.toString())
            dirty = true

            if (snoozed || !AlertLogic.isInsideSchedule(rule, dayIndex, minuteOfDay)) continue

            // ۱) شرط برقرار باشد ۲) برای قیمت/درصد در حالت عبور، نوبت قبل برقرار نباشد
            // ۳) فاصله‌ی ضداسپم تمام شده باشد. جهش حجم خودش رویداد بین دو نمونه است.
            var shouldNotify = triggered
            if (shouldNotify && rule.condition != AlertCondition.VOLUME_SPIKE &&
                rule.onlyOnCross && previous != null
            ) {
                shouldNotify = !AlertLogic.isTriggered(rule, previous)
            }
            if (shouldNotify) {
                val lastNotified = store.getLong(keyNotified, 0L)
                shouldNotify = TimePolicy.cooldownElapsed(
                    now,
                    lastNotified,
                    rule.cooldownMin.coerceAtLeast(1) * 60_000L
                )
            }

            if (shouldNotify) {
                // فقط اعلان واقعاً تحویل‌داده‌شده ثبت می‌شود. در خطای مجوز/سیستم،
                // metric قبلی حفظ می‌شود تا نوبت بعد امکان تلاش دوباره وجود داشته باشد.
                if (!notify(context, safeOwner, cfg, rule, quote, observed)) {
                    if (previous == null) editor.remove(keyMetric)
                    else editor.putString(keyMetric, previous.toString())
                    continue
                }
                editor.putLong(keyNotified, now)
                AlertHistoryStore.add(
                    context,
                    AlertEvent(
                        id = eventId(safeOwner, rule.id, now),
                        ruleId = rule.id,
                        symbolCode = rule.symbolCode,
                        symbolLabel = rule.symbolLabel,
                        sourceId = rule.sourceId,
                        condition = rule.condition,
                        threshold = rule.threshold,
                        price = quote.price,
                        changePct = quote.changePct,
                        volume = quote.volume,
                        observedValue = observed,
                        unit = quote.unit,
                        triggeredAt = now
                    )
                )
            }
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
    private const val KEY_SNOOZE_SET_AT = "snooze_set_at"

    fun snooze(context: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        prefs(context).edit()
            .putLong(KEY_SNOOZE_SET_AT, now)
            .putLong(KEY_SNOOZE, now + safeMinutes * 60_000L)
            .apply()
    }

    fun cancelSnooze(context: Context) {
        prefs(context).edit().remove(KEY_SNOOZE).remove(KEY_SNOOZE_SET_AT).apply()
    }

    /** ۰ یعنی بیدار؛ عقب‌رفتن ساعت نباید خواب کوتاه را ساعت‌ها/روزها تمدید کند. */
    fun snoozeUntil(context: Context): Long {
        val store = prefs(context)
        val now = System.currentTimeMillis()
        val setAt = store.getLong(KEY_SNOOZE_SET_AT, 0L)
        val until = store.getLong(KEY_SNOOZE, 0L)
        val valid = until > now && (setAt <= 0L || now >= setAt)
        if (!valid && (until != 0L || setAt != 0L)) {
            store.edit().remove(KEY_SNOOZE).remove(KEY_SNOOZE_SET_AT).apply()
        }
        return if (valid) until else 0L
    }

    fun isSnoozed(context: Context): Boolean = snoozeUntil(context) > 0L

    // ─────────────────────── نوتیف ───────────────────────

    private fun notify(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        rule: AlertRule,
        quote: Quote,
        observedValue: Double? = null
    ): Boolean {
        val priceText = Format.price(quote.price, cfg.persianDigits)
        val unit = quote.unit.ifBlank { "" }
        // RLM باعث می‌شود عنوان فارسی حتی با ایموجی یا نماد لاتین از راست آغاز شود.
        val rtl = "\u200F"
        val title = rtl + when (rule.condition) {
            AlertCondition.ABOVE -> "🔔 ${rule.symbolLabel} از حد گذشت"
            AlertCondition.BELOW -> "🔻 ${rule.symbolLabel} زیر حد آمد"
            AlertCondition.PCT_UP -> "🚀 رشد ${rule.symbolLabel}"
            AlertCondition.PCT_DOWN -> "⚠️ افت ${rule.symbolLabel}"
            AlertCondition.VOLUME_SPIKE -> "📊 جهش حجم ${rule.symbolLabel}"
        }

        val thresholdText = rtl + when (rule.condition) {
            AlertCondition.ABOVE, AlertCondition.BELOW ->
                "$priceText $unit (حد: ${Format.price(rule.threshold, cfg.persianDigits)} $unit)"
            AlertCondition.PCT_UP, AlertCondition.PCT_DOWN ->
                "${Format.pct(quote.changePct, cfg.persianDigits)} (حد: ${Format.price(abs(rule.threshold), cfg.persianDigits)}٪)"
            AlertCondition.VOLUME_SPIKE ->
                "حجم ${Format.volume(quote.volume, cfg.persianDigits)} • جهش " +
                        "${Format.price(observedValue, cfg.persianDigits)}٪ " +
                        "(حد: ${Format.price(abs(rule.threshold), cfg.persianDigits)}٪)"
        }

        val notificationId = "$ownerKey|${rule.id}".hashCode()
        val bigText = "$thresholdText\nبازه‌ی فعال: ${rule.scheduleText()}"
        return postAlertNotification(context, notificationId, title, thresholdText, bigText, cfg.alertVibrate)
    }

    /**
     * ساختِ واقعیِ نوتیفِ هشدار (هم برای هشدار تازه، هم برای «یادآوری» اسنوز استفاده می‌شود).
     * دکمه‌ی «یادآوری بعد از N دقیقه» را هم می‌چسباند.
     */
    private fun postAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        bigText: String,
        vibrate: Boolean
    ): Boolean {
        ensureChannel(context, vibrate)
        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId(vibrate))
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .apply { if (vibrate) setVibrate(VIBRATION_PATTERN) else setVibrate(longArrayOf(0L)) }
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                snoozeAction(context, notificationId, title, text, bigText, vibrate, SNOOZE_DEFAULT_MIN)
            )
            .build()

        return runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(notificationId, notification)
        }.isSuccess
    }

    /** دکمه‌ی «یادآوری بعد از N دقیقه» روی نوتیف. */
    private fun snoozeAction(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        bigText: String,
        vibrate: Boolean,
        minutes: Int
    ): NotificationCompat.Action {
        val intent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_SNOOZE
            putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, notificationId)
            putExtra(AlertActionReceiver.EXTRA_TITLE, title)
            putExtra(AlertActionReceiver.EXTRA_TEXT, text)
            putExtra(AlertActionReceiver.EXTRA_BIGTEXT, bigText)
            putExtra(AlertActionReceiver.EXTRA_VIBRATE, vibrate)
            putExtra(AlertActionReceiver.EXTRA_MINUTES, minutes)
        }
        val pi = PendingIntent.getBroadcast(
            context, notificationId xor 0x5A02E,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = "⏰ ${Format.toPersianDigits("$minutes")} دقیقه دیگر"
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    /** کاربر روی «یادآوری بعداً» زد: نوتیف بسته و آلارمِ یادآوری ثبت می‌شود. */
    fun handleSnooze(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(AlertActionReceiver.EXTRA_NOTIF_ID, 0)
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
        }
        val minutes = intent.getIntExtra(AlertActionReceiver.EXTRA_MINUTES, SNOOZE_DEFAULT_MIN)
            .coerceIn(1, 24 * 60)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val remind = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_REMIND
            putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(AlertActionReceiver.EXTRA_TITLE, intent.getStringExtra(AlertActionReceiver.EXTRA_TITLE))
            putExtra(AlertActionReceiver.EXTRA_TEXT, intent.getStringExtra(AlertActionReceiver.EXTRA_TEXT))
            putExtra(AlertActionReceiver.EXTRA_BIGTEXT, intent.getStringExtra(AlertActionReceiver.EXTRA_BIGTEXT))
            putExtra(
                AlertActionReceiver.EXTRA_VIBRATE,
                intent.getBooleanExtra(AlertActionReceiver.EXTRA_VIBRATE, true)
            )
        }
        val pi = PendingIntent.getBroadcast(
            context, notifId, remind,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        // آلارمِ نادقیق (بدون نیاز به مجوز SCHEDULE_EXACT_ALARM) و مقاوم در Doze.
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
    }

    /** آلارمِ یادآوری سررسید: همان هشدار دوباره نمایش داده می‌شود. */
    fun handleRemind(context: Context, intent: Intent) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notifId = intent.getIntExtra(AlertActionReceiver.EXTRA_NOTIF_ID, 0)
        val rtl = "\u200F"
        val title = intent.getStringExtra(AlertActionReceiver.EXTRA_TITLE) ?: "${rtl}🔔 یادآوری هشدار"
        val text = intent.getStringExtra(AlertActionReceiver.EXTRA_TEXT).orEmpty()
        val bigText = intent.getStringExtra(AlertActionReceiver.EXTRA_BIGTEXT).orEmpty().ifBlank { text }
        val vibrate = intent.getBooleanExtra(AlertActionReceiver.EXTRA_VIBRATE, true)
        val reminderTitle = if (title.contains("یادآوری")) title else "$rtl⏰ یادآوری • ${title.removePrefix(rtl)}"
        postAlertNotification(context, notifId, reminderTitle, text, bigText, vibrate)
    }

    /** برای دکمه‌ی «تست هشدار» در تنظیمات */
    fun notifyTest(context: Context, vibrate: Boolean = true) {
        ensureChannel(context, vibrate)
        val open = PendingIntent.getActivity(
            context, 7,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, channelId(vibrate))
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle("\u200F🔔 نوتیف تستی")
            .setContentText(
                if (vibrate) "\u200Fاگر این را می‌بینی (و گوشی لرزید)، هشدارها درست کار می‌کنند."
                else "\u200Fاگر این را می‌بینی، هشدارها درست کار می‌کنند (لرزش خاموش است)."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (vibrate) setVibrate(VIBRATION_PATTERN) else setVibrate(longArrayOf(0L)) }
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(999, n)
        }
    }

    private fun ensureChannel(context: Context, vibrate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val id = channelId(vibrate)
        if (manager.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(
            id,
            if (vibrate) "\u200Fهشدار قیمت" else "\u200Fهشدار قیمت (بی‌لرزش)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "\u200Fوقتی نماد به حدی که تعیین کرده‌ای رسید"
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = VIBRATION_PATTERN
        }
        manager.createNotificationChannel(channel)
    }
}
