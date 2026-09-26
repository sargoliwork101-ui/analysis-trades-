package com.pulse.market.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * بعد از «راه‌اندازی مجدد گوشی» و «آپدیت برنامه»، Worker ویجت‌ها دوباره زمان‌بندی
 * می‌شود و یک رفرش مجاز در صف قرار می‌گیرد. سرویس زنده از بوت شروع نمی‌شود، چون
 * Android 15+ شروع dataSync ForegroundService از BOOT_COMPLETED را ممنوع کرده است.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Android 15+ شروع dataSync ForegroundService از BOOT_COMPLETED را
                // ممنوع کرده است. پس هنگام بوت فقط Worker را همگام و یک کار فوری
                // صف می‌کنیم؛ سرویس زنده با اقدام مستقیم کاربر دوباره شروع می‌شود.
                StockWidgetProvider.syncLiveService(context, startForeground = false)
                if (StockWidgetProvider.anyPeriodicWidget(context)) {
                    LiveUpdateWorker.enqueueNow(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
