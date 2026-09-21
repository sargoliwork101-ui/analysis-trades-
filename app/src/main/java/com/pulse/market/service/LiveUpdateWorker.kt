package com.pulse.market.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulse.market.widget.StockWidgetProvider
import java.util.concurrent.TimeUnit

/**
 * پشتیبان سرویس زنده: اگر سیستم سرویس را کشت (یا اندروید اجازه‌ی راه‌اندازی نداد)،
 * این Worker حداقل هر ۱۵ دقیقه (کمترین بازه‌ی مجاز اندروید) قیمت‌ها را تازه می‌کند.
 */
class LiveUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            StockWidgetProvider.refreshAll(applicationContext, force = true)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val NAME = "pulse_periodic_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LiveUpdateWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.KEEP, request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
