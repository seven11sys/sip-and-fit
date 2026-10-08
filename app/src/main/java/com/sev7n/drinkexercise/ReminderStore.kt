package com.sev7n.drinkexercise

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun water() = WaterSettings(
        enabled = prefs.getBoolean("water.enabled", false),
        start = time("water.start", "09:00"), end = time("water.end", "21:00"),
        intervalMinutes = prefs.getLong("water.interval", 90),
        pauseEnabled = prefs.getBoolean("water.pause", true),
        pauseStart = time("water.pauseStart", "12:30"), pauseEnd = time("water.pauseEnd", "14:00"),
        goalMl = prefs.getInt("water.goal", 2000), stopAtGoal = prefs.getBoolean("water.stop", true),
    )

    fun workout() = WorkoutSettings(
        enabled = prefs.getBoolean("workout.enabled", false),
        time = time("workout.time", "19:00"),
        days = prefs.getStringSet("workout.days", (1..7).map { it.toString() }.toSet())!!
            .map { DayOfWeek.of(it.toInt()) }.toSet(),
    )

    fun save(water: WaterSettings, workout: WorkoutSettings) {
        prefs.edit().putBoolean("water.enabled", water.enabled)
            .putString("water.start", water.start.toString()).putString("water.end", water.end.toString())
            .putLong("water.interval", water.intervalMinutes).putBoolean("water.pause", water.pauseEnabled)
            .putString("water.pauseStart", water.pauseStart.toString()).putString("water.pauseEnd", water.pauseEnd.toString())
            .putInt("water.goal", water.goalMl).putBoolean("water.stop", water.stopAtGoal)
            .putBoolean("workout.enabled", workout.enabled).putString("workout.time", workout.time.toString())
            .putStringSet("workout.days", workout.days.map { it.value.toString() }.toSet())
            .remove("water.snooze").remove("workout.snooze").commit()
    }

    fun recordWater(ml: Int, now: ZonedDateTime) {
        require(ml in 1..5000)
        val records = JSONArray(prefs.getString("water.records", "[]"))
        records.put(JSONObject().put("at", now.toInstant().toEpochMilli()).put("ml", ml))
        prefs.edit().putString("water.records", records.toString())
            .putLong("water.anchor", now.toInstant().toEpochMilli()).remove("water.snooze").commit()
    }

    fun totalWater(now: ZonedDateTime): Int {
        val records = JSONArray(prefs.getString("water.records", "[]"))
        return (0 until records.length()).sumOf { index ->
            val record = records.getJSONObject(index)
            val day = Instant.ofEpochMilli(record.getLong("at")).atZone(now.zone).toLocalDate()
            if (day == now.toLocalDate()) record.getInt("ml") else 0
        }
    }

    fun markWorkout(now: ZonedDateTime, completed: Boolean) {
        val key = if (completed) "workout.completed" else "workout.skipped"
        prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet())!! + now.toLocalDate().toString())
            .remove("workout.snooze").commit()
    }

    fun workoutBlocked() = (dates("workout.completed") + dates("workout.skipped"))
    fun workoutStatus(day: LocalDate): String = when (day) {
        in dates("workout.completed") -> "今日健身已完成"
        in dates("workout.skipped") -> "今日健身已跳过"
        else -> "今日健身尚未打卡"
    }

    fun timestamp(key: String, zone: ZoneId): ZonedDateTime? =
        if (prefs.contains(key)) Instant.ofEpochMilli(prefs.getLong(key, 0)).atZone(zone) else null

    fun setTimestamp(key: String, time: ZonedDateTime) {
        prefs.edit().putLong(key, time.toInstant().toEpochMilli()).commit()
    }

    fun clearTimestamp(key: String) { prefs.edit().remove(key).commit() }
    private fun time(key: String, default: String) = LocalTime.parse(prefs.getString(key, default))
    private fun dates(key: String) = prefs.getStringSet(key, emptySet())!!.map { LocalDate.parse(it) }.toSet()
}
