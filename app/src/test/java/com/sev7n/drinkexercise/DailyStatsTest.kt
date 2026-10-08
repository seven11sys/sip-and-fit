package com.sev7n.drinkexercise

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate

class DailyStatsTest {
    private val now = ZonedDateTime.parse("2026-10-08T10:00:00+08:00[Asia/Shanghai]")
    private fun record(id: String?, name: String, unit: WorkoutUnit, amount: String, at: ZonedDateTime = now) =
        WorkoutRecord("record", at, id, name, unit, BigDecimal(amount))

    @Test fun monthIncludesAllDatesAndExcludesOtherMonthsAndFutureDays() {
        val records = listOf(WaterRecord("first", now.withDayOfMonth(1), 100),
            WaterRecord("today", now, 200), WaterRecord("old", now.minusMonths(1), 500),
            WaterRecord("future", now.plusDays(1), 300))
        val days = DailyStats.water(now.toLocalDate(), now.zone, records, StatsPeriod.MONTH)
        assertEquals(31, days.size)
        assertEquals(LocalDate.of(2026,10,1), days.first().day)
        assertEquals(BigDecimal("300"), days.sumOf { it.amount })
        assertEquals(BigDecimal.ZERO, days[8].amount)
    }

    @Test fun leapYearIncludesFebruary29AndAggregatesTwelveMonths() {
        val date = LocalDate.of(2024,12,31)
        val records = listOf(WaterRecord("leap", ZonedDateTime.parse("2024-02-29T10:00:00+08:00[Asia/Shanghai]"), 250),
            WaterRecord("dec", ZonedDateTime.parse("2024-12-31T10:00:00+08:00[Asia/Shanghai]"), 100),
            WaterRecord("outside", now, 500))
        val days = DailyStats.water(date, now.zone, records, StatsPeriod.YEAR)
        assertEquals(366, days.size)
        assertEquals(BigDecimal("250"), days.first { it.day == LocalDate.of(2024,2,29) }.amount)
        val months = DailyStats.months(days)
        assertEquals(12, months.size)
        assertEquals(BigDecimal("250"), months[1].amount)
        assertEquals(BigDecimal("350"), months.sumOf { it.amount })
    }

    @Test fun historicalWorkoutMonthConvertsUnitsAndFiltersUnrelatedDimensions() {
        val anchor = now.toLocalDate().minusMonths(1)
        val records = listOf(record("run", "跑步", WorkoutUnit.METERS, "500", now.minusMonths(1)),
            record("run", "跑步", WorkoutUnit.KILOMETERS, "1", now.minusMonths(1)),
            record("run", "跑步", WorkoutUnit.MINUTES, "30", now.minusMonths(1)),
            record("run", "跑步", WorkoutUnit.KILOMETERS, "10"))
        val days = DailyStats.workout(now.toLocalDate(), now.zone, WorkoutSeries("run", "跑步", WorkoutUnit.KILOMETERS), records, StatsPeriod.MONTH, anchor)
        assertEquals(30, days.size)
        assertEquals("1.5", WorkoutGoals.display(days.sumOf { it.amount }))
    }

    @Test fun navigationUsesCalendarBoundariesWithoutSkippingFebruary() {
        val jan = LocalDate.of(2024,1,31)
        assertEquals(LocalDate.of(2024,2,1), StatsPeriod.MONTH.move(jan,1))
        assertEquals(LocalDate.of(2024,2,29), StatsPeriod.MONTH.end(StatsPeriod.MONTH.move(jan,1)))
        assertEquals(LocalDate.of(2023,1,1), StatsPeriod.YEAR.move(jan,-1))
        assertEquals(jan.minusDays(7), StatsPeriod.WEEK.move(jan,-1))
    }

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
