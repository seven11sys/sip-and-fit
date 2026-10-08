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
import java.util.UUID

data class WaterRecord(val id: String, val at: ZonedDateTime, val ml: Int)
data class WorkoutRecord(val id: String, val at: ZonedDateTime, val type: String, val minutes: Int)

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

    fun saveWater(water: WaterSettings) {
        prefs.edit().putBoolean("water.enabled", water.enabled)
            .putString("water.start", water.start.toString()).putString("water.end", water.end.toString())
            .putLong("water.interval", water.intervalMinutes).putBoolean("water.pause", water.pauseEnabled)
            .putString("water.pauseStart", water.pauseStart.toString()).putString("water.pauseEnd", water.pauseEnd.toString())
            .putInt("water.goal", water.goalMl).putBoolean("water.stop", water.stopAtGoal)
            .remove("water.snooze").commit()
    }

    fun saveWorkout(workout: WorkoutSettings) {
        prefs.edit().putBoolean("workout.enabled", workout.enabled).putString("workout.time", workout.time.toString())
            .putStringSet("workout.days", workout.days.map { it.value.toString() }.toSet())
            .remove("workout.snooze").commit()
    }

    fun recordWater(ml: Int, now: ZonedDateTime) {
        require(ml in 1..5000)
        val records = records("water.records")
        // Preserve old reminders separately so deleting a drink can restore its interval.
        val anchor = prefs.getLong("water.anchor", 0)
        val latestDrink = (0 until records.length()).maxOfOrNull { records.getJSONObject(it).getLong("at") } ?: 0
        if (anchor > latestDrink && anchor > prefs.getLong("water.lastReminder", 0)) {
            prefs.edit().putLong("water.lastReminder", anchor).commit()
        }
        records.put(JSONObject().put("id", UUID.randomUUID().toString())
            .put("at", now.toInstant().toEpochMilli()).put("ml", ml))
        prefs.edit().putString("water.records", records.toString())
            .putLong("water.anchor", now.toInstant().toEpochMilli()).remove("water.snooze").commit()
    }

    fun waterRecords(zone: ZoneId): List<WaterRecord> {
        val array = records("water.records")
        return (0 until array.length()).map {
            val row = array.getJSONObject(it)
            WaterRecord(row.getString("id"), Instant.ofEpochMilli(row.getLong("at")).atZone(zone), row.getInt("ml"))
        }.sortedByDescending { it.at.toInstant() }
    }

    fun deleteWater(id: String): Boolean {
        val array = records("water.records")
        val oldLatest = (0 until array.length()).maxOfOrNull { array.getJSONObject(it).getLong("at") } ?: 0
        val oldAnchor = prefs.getLong("water.anchor", 0)
        if (oldAnchor > oldLatest && oldAnchor > prefs.getLong("water.lastReminder", 0)) {
            prefs.edit().putLong("water.lastReminder", oldAnchor).commit()
        }
        val remaining = JSONArray()
        var removed = false
        for (index in 0 until array.length()) {
            val row = array.getJSONObject(index)
            if (row.getString("id") == id) removed = true else remaining.put(row)
        }
        if (!removed) return false
        val latest = (0 until remaining.length()).maxOfOrNull { remaining.getJSONObject(it).getLong("at") } ?: 0
        val anchor = maxOf(latest, prefs.getLong("water.lastReminder", 0))
        val edit = prefs.edit().putString("water.records", remaining.toString()).remove("water.snooze")
        if (anchor == 0L) edit.remove("water.anchor") else edit.putLong("water.anchor", anchor)
        edit.commit()
        return true
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
        val opposite = if (completed) "workout.skipped" else "workout.completed"
        prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet())!! + now.toLocalDate().toString())
            .putStringSet(opposite, prefs.getStringSet(opposite, emptySet())!! - now.toLocalDate().toString())
            .remove("workout.snooze").commit()
    }

    fun recordWorkout(type: String, minutes: Int, now: ZonedDateTime) {
        require(type.isNotBlank() && type.length <= 40)
        require(minutes in 1..1440)
        val array = records("workout.records")
        array.put(JSONObject().put("id", UUID.randomUUID().toString())
            .put("at", now.toInstant().toEpochMilli()).put("type", type.trim()).put("minutes", minutes))
        prefs.edit().putString("workout.records", array.toString()).commit()
        markWorkout(now, true)
    }

    fun workoutRecords(zone: ZoneId): List<WorkoutRecord> {
        val array = records("workout.records")
        return (0 until array.length()).map {
            val row = array.getJSONObject(it)
            WorkoutRecord(row.getString("id"), Instant.ofEpochMilli(row.getLong("at")).atZone(zone),
                row.getString("type"), row.getInt("minutes"))
        }.sortedByDescending { it.at.toInstant() }
    }

    fun deleteWorkout(id: String, zone: ZoneId): Boolean {
        val array = records("workout.records")
        val remaining = JSONArray()
        var removedDay: LocalDate? = null
        for (index in 0 until array.length()) {
            val row = array.getJSONObject(index)
            if (row.getString("id") == id) removedDay = Instant.ofEpochMilli(row.getLong("at")).atZone(zone).toLocalDate()
            else remaining.put(row)
        }
        val day = removedDay ?: return false
        prefs.edit().putString("workout.records", remaining.toString()).commit()
        if (workoutRecords(zone).none { it.at.toLocalDate() == day }) resetWorkout(day, zone)
        return true
    }

    fun resetWorkout(day: LocalDate, zone: ZoneId) {
        val edit = prefs.edit()
            .putStringSet("workout.completed", prefs.getStringSet("workout.completed", emptySet())!! - day.toString())
            .putStringSet("workout.skipped", prefs.getStringSet("workout.skipped", emptySet())!! - day.toString())
            .remove("workout.snooze")
        if (timestamp("workout.notified", zone)?.toLocalDate() == day) edit.remove("workout.notified")
        edit.commit()
        // Detailed records continue to count as completed; deleting them is a separate action.
        if (workoutRecords(zone).any { it.at.toLocalDate() == day }) {
            prefs.edit().putStringSet("workout.completed", prefs.getStringSet("workout.completed", emptySet())!! + day.toString()).commit()
        }
    }

    private fun records(key: String): JSONArray {
        val array = JSONArray(prefs.getString(key, "[]"))
        var changed = false
        for (index in 0 until array.length()) {
            val row = array.getJSONObject(index)
            if (!row.has("id")) { row.put("id", UUID.randomUUID().toString()); changed = true }
        }
        if (changed) prefs.edit().putString(key, array.toString()).commit()
        return array
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
