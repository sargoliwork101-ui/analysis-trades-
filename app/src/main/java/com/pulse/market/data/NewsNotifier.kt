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

/** اعلان کم‌مزاحمت وقتی ترجمه و تحلیل خبرهای تازه واقعاً آماده و در کش ذخیره شد. */
object NewsNotifier {
    private const val CHANNEL_ID = "market_news_ready_v1"
    private const val NOTIFICATION_ID = 18_081

    fun notifyReady(context: Context, completedCount: Int, newestTitle: String): Boolean {
        if (completedCount <= 0 || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        ensureChannel(context)
        val rtl = "\u200F"
        val count = Format.toPersianDigits(completedCount.toString())
        val text = "$rtl$count خبر تازه به فارسی ترجمه و تحلیل شد"
        val detail = newestTitle.trim().take(180).let { title ->
            if (title.isBlank()) text else "$text\n${rtl}جدیدترین: $title"
        }
        val openNews = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NEWS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle("$rtl📰 خبرهای تازه آماده شدند")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openNews)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "\u200Fآماده‌شدن خبرهای بازار",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "\u200Fوقتی ترجمه و تحلیل فارسی خبرهای تازه کامل شد"
        }
        manager.createNotificationChannel(channel)
    }
}
