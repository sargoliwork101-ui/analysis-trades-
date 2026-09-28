package com.pulse.market.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulse.market.data.AlertEngine

/**
 * دکمه‌های روی نوتیفِ هشدار را مدیریت می‌کند:
 *  • [ACTION_SNOOZE]: کاربر روی «یادآوری بعد از N دقیقه» زد → نوتیف بسته و یک آلارم
 *    برای N دقیقه‌ی بعد ثبت می‌شود.
 *  • [ACTION_REMIND]: آلارمِ بالا سررسید → همان هشدار دوباره نمایش داده می‌شود.
 *
 * exported=false است؛ فقط از PendingIntentهای خودِ برنامه صدا زده می‌شود.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> AlertEngine.handleSnooze(context, intent)
            ACTION_REMIND -> AlertEngine.handleRemind(context, intent)
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.pulse.market.ALERT_SNOOZE"
        const val ACTION_REMIND = "com.pulse.market.ALERT_REMIND"

        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_BIGTEXT = "bigtext"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_MINUTES = "minutes"
    }
}
