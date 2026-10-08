package com.sev7n.drinkexercise

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class WaterSettings(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(21, 0),
    val intervalMinutes: Long = 90,
    val pauseEnabled: Boolean = true,
    val pauseStart: LocalTime = LocalTime.of(12, 30),
    val pauseEnd: LocalTime = LocalTime.of(14, 0),
    val goalMl: Int = 2000,
    val stopAtGoal: Boolean = true,
)

data class WorkoutSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(19, 0),
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

/** Pure scheduling rules; Android delivery and permissions live in ReminderScheduler. */
object ReminderRules {
    fun waterAllowed(now: ZonedDateTime, settings: WaterSettings, totalMl: Int): Boolean {
        val time = now.toLocalTime()
        return settings.enabled && time >= settings.start && time < settings.end &&
            !(settings.pauseEnabled && time >= settings.pauseStart && time < settings.pauseEnd) &&
            !(settings.stopAtGoal && totalMl >= settings.goalMl)
    }

    fun nextWater(
        now: ZonedDateTime,
        settings: WaterSettings,
        lastAnchor: ZonedDateTime?,
        todayMl: Int,
        snoozeUntil: ZonedDateTime? = null,
    ): ZonedDateTime? {
        if (!settings.enabled) return null
        require(settings.start < settings.end)
        require(settings.intervalMinutes > 0)
        require(!settings.pauseEnabled || settings.pauseStart < settings.pauseEnd)
        for (offset in 0..2) {
            val day = now.toLocalDate().plusDays(offset.toLong())
            if (offset == 0 && settings.stopAtGoal && todayMl >= settings.goalMl) continue
            var candidate = day.atTime(settings.start).atZone(now.zone)
            if (offset == 0) {
                // A snooze overrides the interval but must still fit the active window.
                candidate = when {
                    snoozeUntil != null && snoozeUntil > now -> maxOf(candidate, snoozeUntil)
                    lastAnchor != null && lastAnchor.toLocalDate() == day -> maxOf(candidate, lastAnchor.plusMinutes(settings.intervalMinutes))
                    else -> candidate
                }
                // Missed reminders are never replayed when the app opens or the phone restarts.
                if (candidate <= now) candidate = now.plusMinutes(settings.intervalMinutes)
            }
            if (settings.pauseEnabled) {
                val pauseStart = day.atTime(settings.pauseStart).atZone(now.zone)
                val pauseEnd = day.atTime(settings.pauseEnd).atZone(now.zone)
                if (candidate >= pauseStart && candidate < pauseEnd) candidate = pauseEnd
            }
            if (candidate.toLocalDate() == day && candidate.toLocalTime() < settings.end && candidate > now) {
                return candidate
            }
        }
        return null
    }

    fun nextWorkout(
        now: ZonedDateTime,
        settings: WorkoutSettings,
        blockedDates: Set<LocalDate>,
        snoozeUntil: ZonedDateTime? = null,
    ): ZonedDateTime? {
        if (!settings.enabled || settings.days.isEmpty()) return null
        if (snoozeUntil != null && snoozeUntil > now &&
            snoozeUntil.toLocalDate() == now.toLocalDate() &&
            snoozeUntil.dayOfWeek in settings.days && snoozeUntil.toLocalDate() !in blockedDates) {
            return snoozeUntil
        }
        for (offset in 0..7) {
            val day = now.toLocalDate().plusDays(offset.toLong())
            val candidate = day.atTime(settings.time).atZone(now.zone)
            if (candidate > now && day.dayOfWeek in settings.days && day !in blockedDates) return candidate
        }
        return null
    }
}
