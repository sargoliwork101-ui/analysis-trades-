package com.pulse.market.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertLogicTest {

    private fun rule(
        condition: AlertCondition = AlertCondition.ABOVE,
        threshold: Double = 100.0,
        days: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
        from: Int = 0,
        to: Int = 0
    ) = AlertRule(
        id = "test",
        symbolCode = "x",
        symbolLabel = "X",
        sourceId = "source",
        condition = condition,
        threshold = threshold,
        scheduleEnabled = true,
        fromMinute = from,
        toMinute = to,
        days = days
    )

    @Test
    fun emptyDaysMeansNoActiveDay() {
        assertFalse(AlertLogic.isInsideSchedule(rule(days = emptySet()), dayIndex = 0, minuteOfDay = 600))
    }

    @Test
    fun overnightWindowWorks() {
        val overnight = rule(from = 22 * 60, to = 6 * 60)
        assertTrue(AlertLogic.isInsideSchedule(overnight, dayIndex = 1, minuteOfDay = 23 * 60))
        assertTrue(AlertLogic.isInsideSchedule(overnight, dayIndex = 1, minuteOfDay = 5 * 60))
        assertFalse(AlertLogic.isInsideSchedule(overnight, dayIndex = 1, minuteOfDay = 12 * 60))
    }

    @Test
    fun percentCrossingUsesPreviousPercentNotPreviousPrice() {
        val pctRule = rule(condition = AlertCondition.PCT_UP, threshold = 5.0)
        val previousMetric = AlertLogic.metric(pctRule, price = 100_000.0, changePct = 2.0)!!
        val currentMetric = AlertLogic.metric(pctRule, price = 101_000.0, changePct = 6.0)!!
        assertFalse(AlertLogic.isTriggered(pctRule, previousMetric))
        assertTrue(AlertLogic.isTriggered(pctRule, currentMetric))
    }
}
