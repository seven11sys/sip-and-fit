package com.sev7n.drinkexercise

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val water = WaterSettings(enabled = true)
    private val workout = WorkoutSettings(enabled = true)
    private fun at(day: String = "2026-10-08", time: String) = LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(zone)

    @Test fun drinkResetsInterval() {
        val now = at(time = "10:10")
        assertEquals(at(time = "11:40"), ReminderRules.nextWater(now, water, now, 250))
    }
    @Test fun lunchDefersToEndOfPause() {
        val now = at(time = "11:10")
        assertEquals(at(time = "14:00"), ReminderRules.nextWater(now, water, now, 250))
    }
    @Test fun nightMovesToTomorrowMorning() {
        val now = at(time = "20:30")
        assertEquals(at("2026-10-09", "09:00"), ReminderRules.nextWater(now, water, now, 250))
    }
    @Test fun endIsExclusive() {
        assertFalse(ReminderRules.waterAllowed(at(time = "21:00"), water, 0))
    }
    @Test fun goalStopsOnlyToday() {
        assertEquals(at("2026-10-09", "09:00"), ReminderRules.nextWater(at(time = "10:00"), water, null, 2000))
        assertFalse(ReminderRules.waterAllowed(at(time = "10:00"), water, 2000))
    }
    @Test fun goalStopCanBeDisabled() {
        val now = at(time = "10:00")
        assertEquals(at(time = "11:30"), ReminderRules.nextWater(now, water.copy(stopAtGoal = false), now, 2000))
    }
    @Test fun snoozeOverridesNormalInterval() {
        val now = at(time = "10:30")
        assertEquals(at(time = "10:45"), ReminderRules.nextWater(now, water, now, 0, now.plusMinutes(15)))
    }
    @Test fun snoozeCannotEnterLunchOrNight() {
        val now = at(time = "12:20")
        assertEquals(at(time = "14:00"), ReminderRules.nextWater(now, water, now, 0, now.plusMinutes(15)))
        val late = at(time = "20:50")
        assertEquals(at("2026-10-09", "09:00"), ReminderRules.nextWater(late, water, late, 0, late.plusMinutes(15)))
    }
    @Test fun restartDoesNotReplayMissedReminders() {
        val now = at(time = "16:00")
        assertEquals(at(time = "17:30"), ReminderRules.nextWater(now, water, at(time = "10:00"), 0))
    }
    @Test fun disabledRemindersHaveNoNextTime() {
        val now = at(time = "10:00")
        assertNull(ReminderRules.nextWater(now, water.copy(enabled = false), null, 0))
        assertNull(ReminderRules.nextWorkout(now, workout.copy(enabled = false), emptySet()))
    }
    @Test fun completionAndSkipSuppressToday() {
        val now = at(time = "18:00")
        assertEquals(at("2026-10-09", "19:00"), ReminderRules.nextWorkout(now, workout, setOf(now.toLocalDate())))
    }
    @Test fun workoutRepeatsOnSelectedDaysOnly() {
        val now = at(time = "18:00") // Thursday
        val settings = workout.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(at("2026-10-09", "19:00"), ReminderRules.nextWorkout(now, settings, emptySet()))
    }
    @Test fun missedWorkoutIsNotReplayed() {
        assertEquals(at("2026-10-09", "19:00"), ReminderRules.nextWorkout(at(time = "20:00"), workout, emptySet()))
    }
    @Test fun workoutSnoozeStaysOnSameDay() {
        val now = at(time = "19:00")
        assertEquals(at(time = "19:15"), ReminderRules.nextWorkout(now, workout, emptySet(), now.plusMinutes(15)))
        val late = at(time = "23:55")
        assertEquals(at("2026-10-09", "19:00"), ReminderRules.nextWorkout(late, workout, emptySet(), late.plusMinutes(15)))
    }
    @Test fun goalResetsAtMidnightWithOldAnchor() {
        val tomorrow = at("2026-10-09", "08:00")
        assertEquals(at("2026-10-09", "09:00"), ReminderRules.nextWater(tomorrow, water, at(time = "20:00"), 0))
    }
    @Test fun reminderUsesCurrentTimeZone() {
        val now = ZonedDateTime.of(2026, 10, 8, 8, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val next = ReminderRules.nextWater(now, water, null, 0)!!
        assertEquals(now.zone, next.zone)
        assertEquals(LocalTime.of(9, 0), next.toLocalTime())
    }
}
