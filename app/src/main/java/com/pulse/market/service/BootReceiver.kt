package com.pulse.market.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulse.market.data.ConfigStore
import com.pulse.market.widget.StockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** بعد از روشن شدن گوشی، حالت زنده را دوباره راه می‌اندازد */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cfg = ConfigStore.current(context)
                if (cfg.liveService) {
                    LiveUpdateService.start(context)   // اگر اندروید اجازه نداد، بی‌صدا رد می‌شویم
                }
                LiveUpdateWorker.schedule(context)
                StockWidgetProvider.refreshAll(context, force = true)
            } finally {
                pending.finish()
            }
        }
    }
}
