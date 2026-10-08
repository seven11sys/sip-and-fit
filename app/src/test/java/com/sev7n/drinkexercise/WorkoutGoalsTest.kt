package com.sev7n.drinkexercise

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZonedDateTime

class WorkoutGoalsTest {
    private val now = ZonedDateTime.parse("2026-10-08T10:00:00+08:00[Asia/Shanghai]")
    private fun goal(unit: WorkoutUnit, target: String? = "30") = WorkoutGoal("one", "自定义", unit, target?.let { BigDecimal(it) })
    private fun record(unit: WorkoutUnit, amount: String, at: ZonedDateTime = now, id: String = "one") =
        WorkoutRecord("r", at, id, "自定义", unit, BigDecimal(amount))

    @Test fun repeatedRecordsAccumulateWithinOneProjectAndDay() {
        val g = goal(WorkoutUnit.REPETITIONS)
        val all = listOf(record(g.unit, "10"), record(g.unit, "20"), record(g.unit, "100", now.minusDays(1)), record(g.unit, "100", id = "other"))
        assertEquals("30", WorkoutGoals.display(WorkoutGoals.progress(g, now.toLocalDate(), all)))
        assertTrue(WorkoutGoals.complete(listOf(g), now.toLocalDate(), all))
    }

    @Test fun durationAndDistanceUnitsConvertWithoutMixingDimensions() {
        val duration = goal(WorkoutUnit.SECONDS, "90")
        val distance = goal(WorkoutUnit.KILOMETERS, "1.5")
        assertTrue(WorkoutGoals.complete(listOf(duration), now.toLocalDate(), listOf(record(WorkoutUnit.MINUTES, "1.5"))))
        assertTrue(WorkoutGoals.complete(listOf(distance), now.toLocalDate(), listOf(record(WorkoutUnit.METERS, "1500"))))
        assertFalse(WorkoutGoals.complete(listOf(distance), now.toLocalDate(), listOf(record(WorkoutUnit.SECONDS, "1500"))))
    }

    @Test fun decimalDistanceDoesNotLosePrecision() {
        val g = goal(WorkoutUnit.KILOMETERS, "0.3")
        val all = listOf(record(g.unit, "0.1"), record(g.unit, "0.2"))
        assertTrue(WorkoutGoals.complete(listOf(g), now.toLocalDate(), all))
    }

    @Test fun noGoalDoesNotPretendCompleted() {
        assertFalse(WorkoutGoals.complete(emptyList(), now.toLocalDate(), emptyList()))
        val g = goal(WorkoutUnit.MINUTES, null)
        assertFalse(WorkoutGoals.complete(listOf(g), now.toLocalDate(), listOf(record(g.unit, "30"))))
    }

    @Test fun repetitionsMustBeIntegralAndAmountsPositiveAndFiniteSize() {
        assertFalse(WorkoutGoals.validAmount(BigDecimal("1.5"), WorkoutUnit.REPETITIONS))
        assertTrue(WorkoutGoals.validAmount(BigDecimal("1.0"), WorkoutUnit.REPETITIONS))
        assertTrue(WorkoutGoals.validAmount(BigDecimal("0.5"), WorkoutUnit.MINUTES))
        assertFalse(WorkoutGoals.validAmount(BigDecimal.ZERO, WorkoutUnit.SECONDS))
        assertFalse(WorkoutGoals.validAmount(BigDecimal("-1"), WorkoutUnit.SECONDS))
        assertFalse(WorkoutGoals.validAmount(BigDecimal("1000001"), WorkoutUnit.SECONDS))
    }
}
