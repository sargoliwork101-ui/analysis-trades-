package com.pulse.market.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulse.market.R
import com.pulse.market.ui.Format
import com.pulse.market.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** اعلان دوره‌ای پامپ؛ اسکن را حداکثر هر ۱۵ دقیقه انجام می‌دهد تا CoinGecko اسپم نشود. */
object PumpAlertEngine {
    private const val PREF = "pulse_pump_alerts"
    private const val CHANNEL_ID = "pulse_pump_alerts_v1"
    private const val LAST_BACKGROUND_CHECK = "last_background_check"
    private const val MIN_SCAN_INTERVAL_MS = 15 * 60 * 1000L
    private val mutex = Mutex()
    private val evaluationLock = Any()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** یک اسکن مشترک برای همه‌ی ویجت‌هایی که هشدار پامپ را روشن کرده‌اند. */
    suspend fun evaluateConfigured(context: Context, configs: List<Pair<Int, WidgetConfig>>) {
        val eligible = configs.filter { (_, cfg) -> cfg.pumpAlertEnabled }
        if (eligible.isEmpty()) return
        mutex.withLock {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()
            val store = prefs(appContext)
            val lastCheck = store.getLong(LAST_BACKGROUND_CHECK, 0L)
            if (!TimePolicy.cooldownElapsed(now, lastCheck, MIN_SCAN_INTERVAL_MS)) return@withLock
            // حتی در خطای شبکه زمان تلاش ثبت می‌شود تا سرویس هر چند ثانیه CoinGecko را نکوبد.
            store.edit().putLong(LAST_BACKGROUND_CHECK, now).apply()

            val universe = eligible.maxOf { it.second.pumpUniverse }.coerceIn(10, 250)
            val threshold = eligible.minOf { it.second.pumpMinChange }.coerceIn(0.0, 100.0)
            val scan = PumpScanner.scan(appContext, universe, threshold, force = true)
            if (scan.error != null) return@withLock
            eligible.forEach { (widgetId, cfg) ->
                evaluateScan(appContext, "widget_$widgetId", cfg, scan)
            }
        }
    }

    /** ارزیابی نتیجه‌ی اسکن دستی یا دوره‌ای و ارسال حداکثر یک اعلان. */
    fun evaluateScan(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        scan: PumpScanner.PumpScan
    ): Boolean = synchronized(evaluationLock) {
        evaluateScanLocked(context, ownerKey, cfg, scan)
    }

    private fun evaluateScanLocked(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        scan: PumpScanner.PumpScan
    ): Boolean {
        if (!cfg.pumpAlertEnabled || scan.error != null || scan.at <= 0L) return false
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false

        val safeKey = ownerKey.take(100)
        val keySeen = "seen_$safeKey"
        val keyNotified = "notified_$safeKey"
        val store = prefs(context)
        if (scan.at <= store.getLong(keySeen, 0L)) return false

        val matches = scan.coins.filter {
            (it.change24h ?: Double.NEGATIVE_INFINITY) >= cfg.pumpMinChange
        }
        // همین اسکن دوباره ارزیابی نشود، حتی اگر نتیجه‌ای نداشت یا cooldown فعال بود.
        store.edit().putLong(keySeen, scan.at).apply()
        val top = selectCandidate(scan.coins, cfg.pumpMinChange) ?: return false

        val now = System.currentTimeMillis()
        val cooldownMs = cfg.pumpAlertCooldownMin.coerceIn(15, 24 * 60) * 60_000L
        if (!TimePolicy.cooldownElapsed(now, store.getLong(keyNotified, 0L), cooldownMs)) return false

        val delivered = notify(context, safeKey, cfg, top, matches.size)
        if (delivered) store.edit().putLong(keyNotified, now).apply()
        return delivered
    }

    /** انتخاب قطعیِ بهترین کاندید، مستقل از Android و قابل تست. */
    fun selectCandidate(
        coins: List<PumpScanner.PumpCoin>,
        minChange24h: Double
    ): PumpScanner.PumpCoin? = coins
        .asSequence()
        .filter { (it.change24h ?: Double.NEGATIVE_INFINITY) >= minChange24h }
        .maxByOrNull { it.score }

    private fun notify(
        context: Context,
        ownerKey: String,
        cfg: WidgetConfig,
        coin: PumpScanner.PumpCoin,
        matchCount: Int
    ): Boolean {
        val rtl = "\u200F"
        val advice = coin.advice
        val ch24 = Format.price(abs(coin.change24h ?: 0.0), cfg.persianDigits)
        val ch1 = Format.price(coin.change1h, cfg.persianDigits)
        val count = if (cfg.persianDigits) Format.toPersianDigits(matchCount.toString()) else matchCount.toString()
        val title = "$rtl⚠️ هشدار پامپ: ${coin.displayName}"
        val shortText = "${rtl}رشد ۲۴ساعته $ch24٪ • پیشنهاد: ${advice.recommendation.label}"
        val longText = "$shortText\n${rtl}رشد ۱ساعته: $ch1٪ • $count کوین بالای آستانه\n" +
                "${rtl}دلیل: ${advice.reason}\n${rtl}این هشدار سیگنال خرید نیست."

        val open = PendingIntent.getActivity(
            context,
            ownerKey.hashCode(),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(longText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(7000 + abs(ownerKey.hashCode() % 1000), notification)
        }.isSuccess
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "\u200Fهشدار پامپ‌های کریپتو",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "\u200Fهشدار رشد شارپ همراه با پیشنهاد احتیاطی و دلیل"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
