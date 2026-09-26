package com.pulse.market.data

/** محاسبات زمان دیواری با تحمل عقب‌کشیدن ساعت گوشی. */
object TimePolicy {
    fun isFresh(now: Long, timestamp: Long, maxAgeMs: Long): Boolean {
        if (timestamp <= 0L || maxAgeMs < 0L) return false
        val age = now - timestamp
        return age in 0 until maxAgeMs
    }

    /** timestamp آینده (پس از عقب‌کشیدن ساعت) نباید هشدار را برای ساعت‌ها/روزها قفل کند. */
    fun cooldownElapsed(now: Long, lastAt: Long, cooldownMs: Long): Boolean {
        if (lastAt <= 0L || now < lastAt) return true
        return now - lastAt >= cooldownMs.coerceAtLeast(0L)
    }
}
