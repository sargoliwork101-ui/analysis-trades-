package com.pulse.market.data

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

@Serializable
data class PriceAnomalyCandidate(
    val price: Double,
    val firstSeenAt: Long
)

data class PriceAnomalyDecision(
    val suspicious: Boolean,
    val deviationPct: Double = 0.0
)

/** منطق خالص تشخیص جهش غیرعادی؛ نمونه‌ی دومِ هم‌قیمت می‌تواند جهش واقعی را تأیید کند. */
object PriceAnomalyDetector {
    const val MIN_DEVIATION_PCT = 40.0
    const val CONFIRM_TOLERANCE_PCT = 3.0
    const val CANDIDATE_MAX_AGE_MS = 30 * 60 * 1000L

    fun inspect(previous: Double?, current: Double?, reportedChangePct: Double?): PriceAnomalyDecision {
        val old = previous?.takeIf { it.isFinite() && it > 0.0 }
            ?: return PriceAnomalyDecision(false)
        val fresh = current?.takeIf { it.isFinite() && it > 0.0 }
            ?: return PriceAnomalyDecision(false)
        val deviation = ((fresh - old) / old) * 100.0
        if (!deviation.isFinite() || abs(deviation) < MIN_DEVIATION_PCT) {
            return PriceAnomalyDecision(false, deviation.takeIf { it.isFinite() } ?: 0.0)
        }

        // اگر درصد رسمی منبع همین جهش و همین جهت را تأیید کند، حرکت واقعی بازار است.
        val official = reportedChangePct?.takeIf { it.isFinite() }
        val sameDirection = official != null && (official == 0.0 || official * deviation > 0.0)
        val tolerance = max(8.0, abs(deviation) * 0.35)
        val explained = sameDirection && abs(abs(official!!) - abs(deviation)) <= tolerance
        return PriceAnomalyDecision(!explained, deviation)
    }

    fun confirms(candidate: PriceAnomalyCandidate?, current: Double, now: Long): Boolean {
        if (candidate == null || !current.isFinite() || current <= 0.0) return false
        if (now - candidate.firstSeenAt !in 0..CANDIDATE_MAX_AGE_MS) return false
        val distance = abs(current - candidate.price) / candidate.price * 100.0
        return distance.isFinite() && distance <= CONFIRM_TOLERANCE_PCT
    }
}
