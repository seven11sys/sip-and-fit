package com.sev7n.drinkexercise

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

enum class WorkoutUnit(val label: String, val dimension: String, val scale: BigDecimal) {
    SECONDS("秒", "time", BigDecimal.ONE),
    MINUTES("分钟", "time", BigDecimal("60")),
    KILOMETERS("公里", "distance", BigDecimal("1000")),
    METERS("米", "distance", BigDecimal.ONE),
    REPETITIONS("次", "count", BigDecimal.ONE);

    val choice: String get() = when (dimension) {
        "time" -> "时长（$label）"
        "distance" -> "距离（$label）"
        else -> "次数（$label）"
    }
}

data class WorkoutGoal(val id: String, val name: String, val unit: WorkoutUnit, val target: BigDecimal?)
data class WorkoutRecord(val id: String, val at: ZonedDateTime, val projectId: String?,
    val type: String, val unit: WorkoutUnit, val amount: BigDecimal)

object WorkoutGoals {
    fun validAmount(value: BigDecimal, unit: WorkoutUnit): Boolean =
        value > BigDecimal.ZERO && value <= BigDecimal("1000000") && value.scale() <= 3 &&
            (unit != WorkoutUnit.REPETITIONS || value.stripTrailingZeros().scale() <= 0)

    fun progress(goal: WorkoutGoal, day: LocalDate, records: List<WorkoutRecord>): BigDecimal =
        records.filter { it.projectId == goal.id && it.at.toLocalDate() == day && it.unit.dimension == goal.unit.dimension }
            .fold(BigDecimal.ZERO) { total, record -> total + record.amount * record.unit.scale }
            .divide(goal.unit.scale, 6, java.math.RoundingMode.HALF_UP).stripTrailingZeros()

    fun complete(goals: List<WorkoutGoal>, day: LocalDate, records: List<WorkoutRecord>): Boolean {
        val planned = goals.filter { it.target != null }
        return planned.isNotEmpty() && planned.all { progress(it, day, records) >= it.target!! }
    }

    fun display(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString()
}
