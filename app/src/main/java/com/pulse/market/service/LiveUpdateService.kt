package com.pulse.market.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pulse.market.R
import com.pulse.market.ui.MainActivity
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * سرویس پیش‌زمینه‌ی «حالت زنده»:
 * هر N ثانیه (طبق تنظیمات) قیمت‌ها را می‌گیرد و ویجت را تازه می‌کند.
 * این همان چیزی است که به‌روزرسانی لحظه‌ای را ممکن می‌کند.
 */
class LiveUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("در حال به‌روزرسانی قیمت‌ها…"))
        if (loopJob == null) loopJob = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            // سرویس زنده فقط وقتی می‌ماند که ویجتِ واقعیِ زنده‌ای روی صفحه باشد —
            // الگوی پیش‌فرض به‌تنهایی نگهش نمی‌دارد (باتری)
            if (!StockWidgetProvider.anyLiveWidget(this)) {
                stopSelf()
                return
            }
            try {
                StockWidgetProvider.refreshAll(this, force = true, respectSchedule = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // آخرین داده‌ی سالم روی ویجت می‌ماند؛ حلقه در نوبت بعد دوباره تلاش می‌کند.
            }
            val sec = StockWidgetProvider.liveInterval(this)
            updateNotification("قیمت‌ها هر $sec ثانیه تازه می‌شوند")
            delay(sec * 1000L)
        }
    }

    /** Android 15+ پس از سهمیه‌ی dataSync این callback را می‌زند؛ توقف فوری مانع crash می‌شود. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        loopJob?.cancel()
        stopSelf(startId)
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // حتی اگر اپ از رسیست بسته شد، سرویس زنده بماند
        super.onTaskRemoved(rootIntent)
    }

    // ───────────── اعلان ─────────────

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "\u200Fبه‌روزرسانی زنده‌ی قیمت‌ها", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "\u200Fسرویس پس‌زمینه‌ی ویجت نبض بازار"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle("\u200Fنبض بازار")
            .setContentText("\u200F$text")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "pulse_live"
        private const val NOTIF_ID = 4711

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, LiveUpdateService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, LiveUpdateService::class.java)) }
        }
    }
}
