package com.pulse.market.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulse.market.widget.StockWidgetProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * پشتیبان سرویس زنده: اگر سیستم سرویس را کشت (یا اندروید اجازه‌ی راه‌اندازی نداد)،
 * این Worker حداقل هر ۱۵ دقیقه (کمترین بازه‌ی مجاز اندروید) قیمت‌ها را تازه می‌کند.
 */
class LiveUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            StockWidgetProvider.refreshAll(applicationContext, force = true, respectSchedule = true)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "pulse_periodic_refresh"
        private const val NOW_NAME = "pulse_immediate_refresh"

        private val connected = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LiveUpdateWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME, ExistingPeriodicWorkPolicy.UPDATE, request
                )
            }
        }

        /** رفرش یک‌باره‌ی امن برای بوت/آپدیت برنامه؛ خود Worker زمان‌بندی ویجت را رعایت می‌کند. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveUpdateWorker>()
                .setConstraints(connected)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NOW_NAME, ExistingWorkPolicy.REPLACE, request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).apply {
                    cancelUniqueWork(NAME)
                    cancelUniqueWork(NOW_NAME)
                }
            }
        }
    }
}
