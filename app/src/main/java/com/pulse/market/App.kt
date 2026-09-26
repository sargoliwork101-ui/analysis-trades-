package com.pulse.market

import android.app.Application
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * هر بار پروسه‌ی برنامه بالا بیاید، Worker دوره‌ای با تنظیمات واقعی ویجت‌ها
 * همگام می‌شود. اینجا عمداً ForegroundService یا درخواست شبکه شروع نمی‌شود، چون
 * ممکن است پروسه را یک Worker/رسیور در پس‌زمینه ساخته باشد؛ شروع سرویس زنده فقط
 * پس از بازشدن Activity یا اقدام مستقیم کاربر انجام می‌شود.
 */
class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                // بالا آمدن پروسه ممکن است از Worker/رسیورِ پس‌زمینه باشد؛ در آن حالت
                // شروع ForegroundService مجاز نیست. اینجا فقط Worker را همگام می‌کنیم؛
                // سرویس زنده هنگام باز شدن Activity یا اقدام مستقیم کاربر شروع می‌شود.
                StockWidgetProvider.syncLiveService(this@App, startForeground = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // WorkManager در ورود بعدی دوباره همگام می‌شود.
            }
        }
    }
}
