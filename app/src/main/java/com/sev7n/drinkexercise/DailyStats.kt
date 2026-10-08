package com.sev7n.drinkexercise

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class DailyAmount(val day: LocalDate, val amount: BigDecimal)
data class WorkoutSeries(val projectId: String?, val name: String, val unit: WorkoutUnit) {
    val key get() = (projectId ?: "name:${name.lowercase(Locale.ROOT)}") + ":" + unit.dimension
    val label get() = "$name · ${unit.choice}"
}

object DailyStats {
    private fun week(today: LocalDate, amount: (LocalDate) -> BigDecimal) =
        (6L downTo 0L).map { offset -> today.minusDays(offset).let { DailyAmount(it, amount(it)) } }

    fun water(today: LocalDate, zone: ZoneId, records: List<WaterRecord>): List<DailyAmount> {
        val byDay = records.groupBy { it.at.withZoneSameInstant(zone).toLocalDate() }
        return week(today) { day -> BigDecimal(byDay[day].orEmpty().sumOf { it.ml }) }
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

    fun workout(today: LocalDate, zone: ZoneId, series: WorkoutSeries, records: List<WorkoutRecord>): List<DailyAmount> {
        val matching = records.filter {
            val projectMatches = if (series.projectId != null) it.projectId == series.projectId
                else it.projectId == null && it.type.equals(series.name, ignoreCase = true)
            projectMatches && it.unit.dimension == series.unit.dimension
        }.groupBy { it.at.withZoneSameInstant(zone).toLocalDate() }
        return week(today) { day -> matching[day].orEmpty().fold(BigDecimal.ZERO) { sum, record ->
            sum + record.amount * record.unit.scale
        }.divide(series.unit.scale, 6, RoundingMode.HALF_UP).stripTrailingZeros() }
    }
}
