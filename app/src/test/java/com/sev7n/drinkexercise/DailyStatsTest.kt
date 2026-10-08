package com.sev7n.drinkexercise

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyStatsTest {
    private val now = ZonedDateTime.parse("2026-10-08T10:00:00+08:00[Asia/Shanghai]")
    private fun record(id: String?, name: String, unit: WorkoutUnit, amount: String, at: ZonedDateTime = now) =
        WorkoutRecord("record", at, id, name, unit, BigDecimal(amount))

    @Test fun waterWeekIncludesTodayAndZeroDaysButExcludesOutsideDates() {
        val records = listOf(WaterRecord("a", now.minusDays(6), 100), WaterRecord("b", now, 250),
            WaterRecord("c", now.plusHours(1), 300), WaterRecord("old", now.minusDays(7), 500), WaterRecord("future", now.plusDays(1), 200))
        val days = DailyStats.water(now.toLocalDate(), now.zone, records)
        assertEquals(7, days.size)
        assertEquals(listOf("100", "0", "0", "0", "0", "0", "550"), days.map { it.amount.toPlainString() })
        assertEquals(now.toLocalDate(), days.last().day)
    }

    @Test fun waterUsesPhoneTimeZoneAtMidnight() {
        val record = WaterRecord("utc", ZonedDateTime.parse("2026-10-07T18:00:00Z"), 250)
        val days = DailyStats.water(now.toLocalDate(), ZoneId.of("Asia/Shanghai"), listOf(record))
        assertEquals(BigDecimal("250"), days.last().amount)
        assertEquals(BigDecimal.ZERO, days[5].amount)
    }

    @Test fun workoutConvertsUnitsAndDoesNotMixProjectsOrDimensions() {
        val series = WorkoutSeries("run", "跑步", WorkoutUnit.KILOMETERS)
        val all = listOf(record("run", "跑步", WorkoutUnit.METERS, "500"), record("run", "跑步", WorkoutUnit.KILOMETERS, "1"),
            record("run", "跑步", WorkoutUnit.MINUTES, "30"), record("bike", "骑行", WorkoutUnit.KILOMETERS, "5"))
        assertEquals("1.5", WorkoutGoals.display(DailyStats.workout(now.toLocalDate(), now.zone, series, all).last().amount))
    }

    @Test fun seriesKeepsMeasurementSeparateAndUsesGoalUnitsForConversion() {
        val goal = WorkoutGoal("plank", "平板支撑", WorkoutUnit.SECONDS, BigDecimal("90"))
        val all = listOf(record("plank", "平板支撑", WorkoutUnit.MINUTES, "1.5"), record("plank", "平板支撑", WorkoutUnit.REPETITIONS, "3"))
        val choices = DailyStats.series(listOf(goal), all)
        assertEquals(2, choices.size)
        assertEquals(WorkoutUnit.SECONDS, choices.first().unit)
        assertEquals("90", WorkoutGoals.display(DailyStats.workout(now.toLocalDate(), now.zone, choices.first(), all).last().amount))
        assertEquals("3", WorkoutGoals.display(DailyStats.workout(now.toLocalDate(), now.zone, choices.last(), all).last().amount))
    }

    @Test fun temporaryRecordsAreChartedWithoutAnyGoal() {
        val all = listOf(record(null, "爬楼梯", WorkoutUnit.MINUTES, "5"), record(null, "爬楼梯", WorkoutUnit.SECONDS, "30"))
        val series = DailyStats.series(emptyList(), all).single()
        assertEquals("5.5", WorkoutGoals.display(DailyStats.workout(now.toLocalDate(), now.zone, series, all).last().amount))
        assertEquals("5", WorkoutGoals.display(DailyStats.workout(now.toLocalDate(), now.zone, series, all.take(1)).last().amount))
    }
}
