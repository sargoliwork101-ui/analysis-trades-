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
 * بعد از «راه‌اندازی مجدد گوشی» و «آپدیت برنامه»، ویجت‌ها و سرویس زنده دوباره وصل می‌شوند.
 *
 * چرا MY_PACKAGE_REPLACED لازم است: هنگام آپدیت، اندروید پروسه‌ی برنامه را می‌کُشد؛
 * سرویس زنده و Worker می‌میرند و BOOT_COMPLETED هم دیگر نمی‌آید. بدون این رسیور،
 * ویجت‌های صفحه‌ی اصلی تا ری‌استارت بعدی گوشی با آخرین داده‌ی قبل از آپدیت فریز می‌مانند.
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
                // هم سرویس زنده و هم Worker مطابق تنظیمات واقعی ویجت‌ها
                // (روشن/خاموش بودن به‌روزرسانی خودکار هر ویجت) همگام می‌شوند
                StockWidgetProvider.syncLiveService(context)
                StockWidgetProvider.refreshAll(context, force = true)
            } finally {
                pending.finish()
            }
        }
    }
}
