package com.pulse.market

import android.app.Application
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * هر بار پروسه‌ی برنامه بالا بیاید (باز کردن اپ، آپدیت، اجرای Worker، بوت گوشی)
 * سرویس زنده و ویجت‌ها با تنظیمات واقعی همگام می‌شوند.
 *
 * چرا مهم است: بعد از آپدیت برنامه یا کشته‌شدن پروسه، هیچ‌چیز سرویس زنده را
 * برنمی‌گرداند و ویجت‌ها فریز می‌مانند. این همگام‌سازی «هر پروسه‌ی تازه» آن را
 * تضمین می‌کند — بی‌خطر است: اگر ویجتی حالت زنده نخواهد، سرویس متوقف می‌شود.
 */
class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                StockWidgetProvider.syncLiveService(this@App)
                StockWidgetProvider.refreshAll(this@App)
            }
        }
    }
}
