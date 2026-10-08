package com.sev7n.drinkexercise

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class DailyAmount(val day: LocalDate, val amount: BigDecimal)
enum class StatsPeriod(val label: String) {
    WEEK("周"), MONTH("月"), YEAR("年");
    fun start(anchor: LocalDate): LocalDate = when (this) {
        WEEK -> anchor.minusDays(6)
        MONTH -> anchor.withDayOfMonth(1)
        YEAR -> anchor.withDayOfYear(1)
    }
    fun end(anchor: LocalDate): LocalDate = when (this) {
        WEEK -> anchor
        MONTH -> anchor.withDayOfMonth(anchor.lengthOfMonth())
        YEAR -> anchor.withDayOfYear(anchor.lengthOfYear())
    }
    fun move(anchor: LocalDate, offset: Long): LocalDate = when (this) {
        WEEK -> anchor.plusWeeks(offset)
        MONTH -> anchor.withDayOfMonth(1).plusMonths(offset)
        YEAR -> anchor.withDayOfYear(1).plusYears(offset)
    }
}
data class WorkoutSeries(val projectId: String?, val name: String, val unit: WorkoutUnit) {
    val key get() = (projectId ?: "name:${name.lowercase(Locale.ROOT)}") + ":" + unit.dimension
    val label get() = "$name · ${unit.choice}"
}

object DailyStats {
    private fun days(today: LocalDate, period: StatsPeriod, anchor: LocalDate, amount: (LocalDate) -> BigDecimal): List<DailyAmount> {
        val start = period.start(anchor)
        val end = period.end(anchor)
        return (0L..java.time.temporal.ChronoUnit.DAYS.between(start, end)).map { offset ->
            val day = start.plusDays(offset)
            DailyAmount(day, if (day > today) BigDecimal.ZERO else amount(day))
        }
    }

    fun water(today: LocalDate, zone: ZoneId, records: List<WaterRecord>, period: StatsPeriod = StatsPeriod.WEEK, anchor: LocalDate = today): List<DailyAmount> {
        val byDay = records.groupBy { it.at.withZoneSameInstant(zone).toLocalDate() }
        return days(today, period, anchor) { day -> BigDecimal(byDay[day].orEmpty().sumOf { it.ml.toLong() }) }
    }

    fun series(goals: List<WorkoutGoal>, records: List<WorkoutRecord>): List<WorkoutSeries> {
        val choices = linkedMapOf<String, WorkoutSeries>()
        goals.forEach { goal -> WorkoutSeries(goal.id, goal.name, goal.unit).let { choices[it.key] = it } }
        records.forEach { record ->
            val item = WorkoutSeries(record.projectId, record.type, record.unit)
            if (item.key !in choices) choices[item.key] = item
        }
        return choices.values.toList()
    }

    fun workout(today: LocalDate, zone: ZoneId, series: WorkoutSeries, records: List<WorkoutRecord>, period: StatsPeriod = StatsPeriod.WEEK, anchor: LocalDate = today): List<DailyAmount> {
        val matching = records.filter {
            val projectMatches = if (series.projectId != null) it.projectId == series.projectId
                else it.projectId == null && it.type.equals(series.name, ignoreCase = true)
            projectMatches && it.unit.dimension == series.unit.dimension
        }.groupBy { it.at.withZoneSameInstant(zone).toLocalDate() }
        return days(today, period, anchor) { day -> matching[day].orEmpty().fold(BigDecimal.ZERO) { sum, record ->
            sum + record.amount * record.unit.scale
        }.divide(series.unit.scale, 6, RoundingMode.HALF_UP).stripTrailingZeros() }
    }

    fun months(days: List<DailyAmount>): List<DailyAmount> = days.groupBy { it.day.withDayOfMonth(1) }
        .map { (month, values) -> DailyAmount(month, values.fold(BigDecimal.ZERO) { sum, day -> sum + day.amount }) }

    fun workoutAll(today: LocalDate, zone: ZoneId, records: List<WorkoutRecord>, period: StatsPeriod = StatsPeriod.WEEK, anchor: LocalDate = today): List<DailyAmount> {
        val byDay = records.groupBy { it.at.withZoneSameInstant(zone).toLocalDate() }
        return days(today, period, anchor) { day -> BigDecimal(byDay[day].orEmpty().size) }
    }
}
