package com.sev7n.drinkexercise

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ReminderStoreTest {
    private lateinit var store: ReminderStore
    private val now = ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Before fun setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        store = ReminderStore(RuntimeEnvironment.getApplication())
    }

    @Test fun undoDrinkSurvivesReopeningAndRestoresInterval() {
        store.recordWater(100, now)
        store.recordWater(500, now.plusMinutes(10))
        val latest = store.waterRecords(now.zone).first()
        assertTrue(store.deleteWater(latest.id))
        val reopened = ReminderStore(RuntimeEnvironment.getApplication())
        assertEquals(100, reopened.totalWater(now))
        assertEquals(now, reopened.timestamp("water.anchor", now.zone))
        assertFalse(reopened.deleteWater(latest.id))
    }

    @Test fun undoDrinkPreservesNewerReminderAnchor() {
        store.recordWater(250, now)
        store.setTimestamp("water.lastReminder", now.plusHours(2))
        assertTrue(store.deleteWater(store.waterRecords(now.zone).first().id))
        assertEquals(now.plusHours(2), store.timestamp("water.anchor", now.zone))
    }

    @Test fun oldDrinkRecordsGainStableIdsWithoutLosingData() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putString("water.records", "[{\"at\":${now.toInstant().toEpochMilli()},\"ml\":250}]").commit()
        val record = store.waterRecords(now.zone).single()
        assertEquals(record.id, store.waterRecords(now.zone).single().id)
        assertTrue(store.deleteWater(record.id))
        assertEquals(0, store.totalWater(now))
    }

    @Test fun workoutSkipCanBeReversedWithoutCreatingCompletion() {
        store.skipWorkout(now)
        assertEquals("今日健身已跳过", store.workoutStatus(now.toLocalDate(), now.zone))
        store.setTimestamp("workout.notified", now)
        store.resetWorkout(now.toLocalDate(), now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
        assertNull(store.timestamp("workout.notified", now.zone))
    }

    @Test fun removingLastWorkoutReopensTodayWithoutDeletingOtherDays() {
        val goal = WorkoutGoal("test", "力量训练", WorkoutUnit.MINUTES, BigDecimal("30"))
        store.saveGoal(goal)
        store.recordWorkout(goal, WorkoutUnit.MINUTES, BigDecimal("30"), now.minusDays(1))
        store.recordWorkout(goal, WorkoutUnit.MINUTES, BigDecimal("30"), now)
        store.recordWorkout(goal, WorkoutUnit.MINUTES, BigDecimal("10"), now.plusMinutes(1))
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertTrue(now.toLocalDate() in store.workoutBlocked(now.zone))
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
        assertEquals("力量训练", store.workoutRecords(now.zone).single().type)
        assertEquals(BigDecimal("30"), store.workoutRecords(now.zone).single().amount)
    }

    @Test fun onlyAllGoalsReachedBlockReminderAndUndoRestoresIt() {
        val arm = WorkoutGoal("arm", "速臂器锻炼", WorkoutUnit.REPETITIONS, BigDecimal("30"))
        val plank = WorkoutGoal("plank", "变式平板支撑", WorkoutUnit.SECONDS, BigDecimal("90"))
        store.saveGoal(arm); store.saveGoal(plank)
        store.recordWorkout(arm, WorkoutUnit.REPETITIONS, BigDecimal("30"), now)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
        store.recordWorkout(plank, WorkoutUnit.SECONDS, BigDecimal("60"), now.plusMinutes(1))
        store.recordWorkout(plank, WorkoutUnit.SECONDS, BigDecimal("30"), now.plusMinutes(2))
        assertTrue(now.toLocalDate() in store.workoutBlocked(now.zone))
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
    }

    @Test fun removingProjectKeepsItsRecordsAndDoesNotReseedPresets() {
        val goal = store.workoutGoals().first()
        store.recordWorkout(goal, goal.unit, BigDecimal.ONE, now)
        store.workoutGoals().forEach { store.deleteGoal(it.id) }
        assertTrue(store.workoutGoals().isEmpty())
        assertEquals(1, store.workoutRecords(now.zone).size)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
    }

    @Test fun undoingRecordDoesNotUndoExplicitSkip() {
        val goal = store.workoutGoals().first()
        store.recordWorkout(goal, goal.unit, BigDecimal.ONE, now)
        store.skipWorkout(now)
        store.deleteWorkout(store.workoutRecords(now.zone).first().id, now.zone)
        assertEquals("今日健身已跳过", store.workoutStatus(now.toLocalDate(), now.zone))
        store.resetWorkout(now.toLocalDate(), now.zone)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
    }

    @Test fun oldTimeRecordsAndManualCompletionDoNotFakeGoalCompletion() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminders", Context.MODE_PRIVATE).edit()
            .putString("workout.records", "[{\"at\":${now.toInstant().toEpochMilli()},\"type\":\"跑步\",\"minutes\":30}]")
            .putStringSet("workout.completed", setOf(now.toLocalDate().toString())).commit()
        val record = store.workoutRecords(now.zone).single()
        assertEquals(WorkoutUnit.MINUTES, record.unit)
        assertEquals(BigDecimal("30"), record.amount)
        assertFalse(now.toLocalDate() in store.workoutBlocked(now.zone))
    }

    @Test fun savingOneReminderDoesNotOverwriteOtherSettingsOrSnooze() {
        store.saveWorkout(WorkoutSettings(enabled = true))
        store.setTimestamp("workout.snooze", now.plusMinutes(15))
        store.saveWater(WaterSettings(enabled = true, intervalMinutes = 120))
        assertTrue(store.workout().enabled)
        assertEquals(now.plusMinutes(15), store.timestamp("workout.snooze", now.zone))
    }

    @Test fun invalidWaterInputDoesNotPreventWorkoutAutosave() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val interval = activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<EditText>("water.interval")
        interval.setText("")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val workout = activity.findViewById<android.view.View>(android.R.id.content).findViewWithTag<CheckBox>("workout.enabled")
        workout.isChecked = true
        assertTrue(store.workout().enabled)
        assertEquals(90, store.water().intervalMinutes)
        interval.setText("120")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(120, store.water().intervalMinutes)
    }
}
